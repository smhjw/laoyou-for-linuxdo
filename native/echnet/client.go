// Package echnet 是佬友自己的 ECH 直连网络层:DoH 取 ECH 配置 → TLS 1.3 + ECH 握手 → HTTP/1.1 或 HTTP/2。
//
// 对外 API 只用 gomobile 能绑定到 Java 的类型(string、[]byte、int、bool、结构体指针),
// 由 App 里的 OkHttp 拦截器调用。Cookie、UA、重定向都留给 OkHttp 那一侧处理,这里只管把请求安全地送到。
package echnet

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

const maxBodyBytes = 64 << 20

// Request 是一次 HTTP 请求。Headers 每行一个 "Name: value",同名头可重复多行。
type Request struct {
	Method    string
	URL       string
	Headers   string
	Body      []byte
	TimeoutMs int
}

// Response 是一次 HTTP 响应。Headers 的格式与 Request 相同;Body 已解压。
type Response struct {
	Status      int
	Reason      string
	Proto       string
	Headers     string
	Body        []byte
	ECHAccepted bool
	RemoteIP    string
}

// Client 持有连接池与 DoH 缓存,应用里保持一个实例即可。
type Client struct {
	resolver  *resolver
	transport *http.Transport
	http      *http.Client

	requests    atomic.Int64
	failures    atomic.Int64
	echRetries  atomic.Int64
	mu          sync.Mutex
	lastError   string
	lastServer  string
	lastIP      string
	handshakeMs int64
}

// NewClient 创建客户端。dohServers 为换行分隔的 DoH 地址,按顺序尝试。
func NewClient(dohServers string) (*Client, error) {
	servers := splitServers(dohServers)
	if len(servers) == 0 {
		return nil, errors.New("至少需要一个 DoH 服务器")
	}
	c := &Client{resolver: newResolver(servers)}
	c.transport = &http.Transport{
		Proxy:                 nil,
		DialTLSContext:        c.dialTLS,
		ForceAttemptHTTP2:     true,
		MaxIdleConns:          32,
		MaxIdleConnsPerHost:   8,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: time.Second,
	}
	c.http = &http.Client{
		Transport: c.transport,
		// 重定向交回 OkHttp:跨域跳转时要去掉论坛 Cookie,那套规则在 App 里。
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}
	return c, nil
}

// SetDohServers 替换 DoH 服务器列表并清空解析缓存(用户在设置里增删节点后调用)。
func (c *Client) SetDohServers(dohServers string) {
	c.resolver.setServers(splitServers(dohServers))
	c.transport.CloseIdleConnections()
}

// Close 关闭空闲连接。
func (c *Client) Close() {
	c.transport.CloseIdleConnections()
}

// Send 发送请求。只用于被 SNI 拦截、必须走 ECH 的站点(linux.do)。
func (c *Client) Send(r *Request) (*Response, error) {
	c.requests.Add(1)
	resp, err := c.do(r)
	if err != nil {
		c.failures.Add(1)
		c.mu.Lock()
		c.lastError = err.Error()
		c.mu.Unlock()
	}
	return resp, err
}

func (c *Client) do(r *Request) (*Response, error) {
	timeout := time.Duration(r.TimeoutMs) * time.Millisecond
	if timeout <= 0 {
		timeout = 30 * time.Second
	}
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	var body io.Reader
	if len(r.Body) > 0 {
		body = bytes.NewReader(r.Body)
	}
	req, err := http.NewRequestWithContext(ctx, r.Method, r.URL, body)
	if err != nil {
		return nil, err
	}
	for _, line := range strings.Split(r.Headers, "\n") {
		name, value, ok := strings.Cut(line, ":")
		if !ok {
			continue
		}
		name, value = strings.TrimSpace(name), strings.TrimSpace(value)
		switch strings.ToLower(name) {
		case "", "connection", "keep-alive", "transfer-encoding", "content-length", "proxy-connection", "upgrade":
			// 逐跳头由传输层自己决定
		case "host":
			req.Host = value
		default:
			req.Header.Add(name, value)
		}
	}

	var remoteIP string
	var echAccepted bool
	trace := withConnTrace(ctx, func(conn net.Conn) {
		if tc, ok := conn.(*tls.Conn); ok {
			echAccepted = tc.ConnectionState().ECHAccepted
		}
		if addr, ok := conn.RemoteAddr().(*net.TCPAddr); ok {
			remoteIP = addr.IP.String()
		}
	})
	resp, err := c.http.Do(req.WithContext(trace))
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(io.LimitReader(resp.Body, maxBodyBytes+1))
	if err != nil {
		return nil, err
	}
	if len(data) > maxBodyBytes {
		return nil, fmt.Errorf("响应超过 %d MB", maxBodyBytes>>20)
	}

	var headers strings.Builder
	for name, values := range resp.Header {
		for _, v := range values {
			headers.WriteString(name)
			headers.WriteString(": ")
			headers.WriteString(v)
			headers.WriteByte('\n')
		}
	}
	reason := strings.TrimSpace(strings.TrimPrefix(resp.Status, fmt.Sprint(resp.StatusCode)))
	return &Response{
		Status:      resp.StatusCode,
		Reason:      reason,
		Proto:       resp.Proto,
		Headers:     headers.String(),
		Body:        data,
		ECHAccepted: echAccepted,
		RemoteIP:    remoteIP,
	}, nil
}

// dialTLS:DoH 解析 → 逐个 IP 建 TCP → ECH 握手。服务器拒绝旧 ECH 配置时,用它下发的新配置重试一次。
// 永远不退回明文 SNI:在被 SNI 拦截的网络里那样必然被重置,还会暴露访问目标。
func (c *Client) dialTLS(ctx context.Context, network, addr string) (net.Conn, error) {
	host, port, err := net.SplitHostPort(addr)
	if err != nil {
		return nil, err
	}
	t, err := c.resolver.lookup(ctx, host)
	if err != nil {
		return nil, err
	}

	ech := t.ech
	refreshed := false
	var lastErr error
	for i := 0; i < len(t.ips); i++ {
		ip := t.ips[i]
		conn, err := c.handshake(ctx, network, net.JoinHostPort(ip, port), host, ech)
		if err == nil {
			c.mu.Lock()
			c.lastServer, c.lastIP = t.server, ip
			c.mu.Unlock()
			return conn, nil
		}
		lastErr = err
		var rejection *tls.ECHRejectionError
		if errors.As(err, &rejection) {
			if refreshed || len(rejection.RetryConfigList) == 0 {
				c.resolver.invalidate(host)
				return nil, fmt.Errorf("服务器拒绝 ECH 配置: %w", err)
			}
			// 缓存里的 ECH 配置过期了:换成服务器下发的新配置,从第一个 IP 重新来一轮。
			c.echRetries.Add(1)
			refreshed = true
			ech = rejection.RetryConfigList
			c.resolver.updateECH(host, ech)
			i = -1
			continue
		}
		if ctx.Err() != nil {
			break
		}
	}
	c.resolver.invalidate(host)
	return nil, lastErr
}

func (c *Client) handshake(ctx context.Context, network, ipAddr, host string, ech []byte) (net.Conn, error) {
	dialer := &net.Dialer{Timeout: 8 * time.Second, KeepAlive: 30 * time.Second}
	raw, err := dialer.DialContext(ctx, network, ipAddr)
	if err != nil {
		return nil, err
	}
	start := time.Now()
	conn := tls.Client(raw, &tls.Config{
		ServerName:                     host,
		MinVersion:                     tls.VersionTLS13,
		NextProtos:                     []string{"h2", "http/1.1"},
		EncryptedClientHelloConfigList: ech,
	})
	if err := conn.HandshakeContext(ctx); err != nil {
		raw.Close()
		return nil, err
	}
	if !conn.ConnectionState().ECHAccepted {
		conn.Close()
		return nil, errors.New("握手完成但 ECH 未被接受")
	}
	c.mu.Lock()
	c.handshakeMs = time.Since(start).Milliseconds()
	c.mu.Unlock()
	return conn, nil
}

// Stats 返回诊断用的 JSON(请求数、失败数、最近一次的 DoH 服务器 / IP / 握手耗时 / 错误)。
func (c *Client) Stats() string {
	c.mu.Lock()
	defer c.mu.Unlock()
	b, _ := json.Marshal(map[string]any{
		"requests":    c.requests.Load(),
		"failures":    c.failures.Load(),
		"echRetries":  c.echRetries.Load(),
		"lastServer":  c.lastServer,
		"lastIP":      c.lastIP,
		"handshakeMs": c.handshakeMs,
		"lastError":   c.lastError,
	})
	return string(b)
}

func splitServers(s string) []string {
	var out []string
	for _, line := range strings.Split(s, "\n") {
		if line = strings.TrimSpace(line); line != "" {
			out = append(out, line)
		}
	}
	return out
}
