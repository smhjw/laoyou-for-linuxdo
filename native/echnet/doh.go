package echnet

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
)

const (
	dnsTypeA     = 1
	dnsTypeHTTPS = 65
	svcKeyIPv4   = 4
	svcKeyECH    = 5

	minTTL = 60 * time.Second
	maxTTL = 10 * time.Minute
)

// target 是一个域名经 DoH 解析后的结果:可连的 IP 与 ECH 配置。
type target struct {
	ips     []string
	ech     []byte
	expires time.Time
	server  string // 给出这份结果的 DoH 服务器,便于诊断
}

// resolver 依次询问 DoH 服务器(RFC 8484 GET),拿 HTTPS 记录里的 ECH 配置与 IP 提示,按 TTL 缓存。
type resolver struct {
	http *http.Client

	mu      sync.Mutex
	servers []string
	cache   map[string]*target
}

func newResolver(servers []string) *resolver {
	return &resolver{
		// DoH 服务器本身不在黑名单里,用系统 DNS 解析它们的域名即可;不读代理环境变量。
		http: &http.Client{
			Timeout:   8 * time.Second,
			Transport: &http.Transport{Proxy: nil, ForceAttemptHTTP2: true, MaxIdleConnsPerHost: 2},
		},
		servers: servers,
		cache:   map[string]*target{},
	}
}

func (r *resolver) setServers(servers []string) {
	r.mu.Lock()
	r.servers = servers
	r.cache = map[string]*target{}
	r.mu.Unlock()
}

func (r *resolver) invalidate(host string) {
	r.mu.Lock()
	delete(r.cache, host)
	r.mu.Unlock()
}

// updateECH 用服务器在 ECH 被拒时下发的新配置替换缓存里的旧配置。
func (r *resolver) updateECH(host string, ech []byte) {
	r.mu.Lock()
	if t := r.cache[host]; t != nil {
		t.ech = ech
	}
	r.mu.Unlock()
}

func (r *resolver) lookup(ctx context.Context, host string) (*target, error) {
	r.mu.Lock()
	if t := r.cache[host]; t != nil && time.Now().Before(t.expires) {
		r.mu.Unlock()
		return t, nil
	}
	servers := append([]string(nil), r.servers...)
	r.mu.Unlock()
	if len(servers) == 0 {
		return nil, errors.New("没有可用的 DoH 服务器")
	}

	var errs []string
	for _, server := range servers {
		t, err := r.resolveWith(ctx, server, host)
		if err == nil {
			r.mu.Lock()
			r.cache[host] = t
			r.mu.Unlock()
			return t, nil
		}
		errs = append(errs, fmt.Sprintf("%s: %v", server, err))
		if ctx.Err() != nil {
			break
		}
	}
	return nil, fmt.Errorf("DoH 解析 %s 失败: %s", host, strings.Join(errs, "; "))
}

func (r *resolver) resolveWith(ctx context.Context, server, host string) (*target, error) {
	httpsMsg, err := r.query(ctx, server, host, dnsTypeHTTPS)
	if err != nil {
		return nil, err
	}
	ech, hints, ttl, err := parseHTTPS(httpsMsg)
	if err != nil {
		return nil, err
	}
	ips := hints
	if len(ips) == 0 {
		aMsg, err := r.query(ctx, server, host, dnsTypeA)
		if err != nil {
			return nil, err
		}
		ips, ttl, err = parseA(aMsg)
		if err != nil {
			return nil, err
		}
	}
	if ttl < minTTL {
		ttl = minTTL
	} else if ttl > maxTTL {
		ttl = maxTTL
	}
	return &target{ips: ips, ech: ech, expires: time.Now().Add(ttl), server: server}, nil
}

func (r *resolver) query(ctx context.Context, server, name string, qtype uint16) ([]byte, error) {
	var q bytes.Buffer
	_ = binary.Write(&q, binary.BigEndian, [6]uint16{0, 0x0100, 1, 0, 0, 0})
	for _, label := range strings.Split(strings.TrimSuffix(name, "."), ".") {
		q.WriteByte(byte(len(label)))
		q.WriteString(label)
	}
	q.WriteByte(0)
	_ = binary.Write(&q, binary.BigEndian, [2]uint16{qtype, 1})

	sep := "?"
	if strings.Contains(server, "?") {
		sep = "&"
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, server+sep+"dns="+base64.RawURLEncoding.EncodeToString(q.Bytes()), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/dns-message")
	resp, err := r.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	return io.ReadAll(io.LimitReader(resp.Body, 64<<10))
}

type dnsRecord struct {
	typ   uint16
	ttl   uint32
	rdata []byte
}

func parseAnswers(msg []byte) ([]dnsRecord, error) {
	if len(msg) < 12 {
		return nil, errors.New("DNS 报文过短")
	}
	if rcode := msg[3] & 0x0F; rcode != 0 {
		return nil, fmt.Errorf("DNS rcode %d", rcode)
	}
	qd := int(binary.BigEndian.Uint16(msg[4:]))
	an := int(binary.BigEndian.Uint16(msg[6:]))
	off := 12
	var err error
	for i := 0; i < qd; i++ {
		if off, err = skipName(msg, off); err != nil {
			return nil, err
		}
		off += 4
	}
	out := make([]dnsRecord, 0, an)
	for i := 0; i < an; i++ {
		if off, err = skipName(msg, off); err != nil {
			return nil, err
		}
		if off+10 > len(msg) {
			return nil, errors.New("DNS 记录被截断")
		}
		typ := binary.BigEndian.Uint16(msg[off:])
		ttl := binary.BigEndian.Uint32(msg[off+4:])
		rdlen := int(binary.BigEndian.Uint16(msg[off+8:]))
		off += 10
		if off+rdlen > len(msg) {
			return nil, errors.New("DNS rdata 被截断")
		}
		out = append(out, dnsRecord{typ, ttl, msg[off : off+rdlen]})
		off += rdlen
	}
	return out, nil
}

func skipName(msg []byte, off int) (int, error) {
	for {
		if off >= len(msg) {
			return 0, errors.New("DNS 域名越界")
		}
		l := int(msg[off])
		switch {
		case l == 0:
			return off + 1, nil
		case l&0xC0 == 0xC0:
			return off + 2, nil
		default:
			off += 1 + l
		}
	}
}

// parseHTTPS 取 HTTPS 记录里的 ECHConfigList 与 IPv4 提示;没有 ECH 配置视为失败,
// 因为对被 SNI 拦截的站点来说,没有 ECH 就连不上。
func parseHTTPS(msg []byte) (ech []byte, ips []string, ttl time.Duration, err error) {
	records, err := parseAnswers(msg)
	if err != nil {
		return nil, nil, 0, err
	}
	ttl = maxTTL
	for _, rec := range records {
		if rec.typ != dnsTypeHTTPS || len(rec.rdata) < 3 {
			continue
		}
		if d := time.Duration(rec.ttl) * time.Second; d < ttl {
			ttl = d
		}
		off, err := skipName(rec.rdata, 2) // SvcPriority 之后是不压缩的 TargetName
		if err != nil {
			return nil, nil, 0, err
		}
		for off+4 <= len(rec.rdata) {
			key := binary.BigEndian.Uint16(rec.rdata[off:])
			l := int(binary.BigEndian.Uint16(rec.rdata[off+2:]))
			if off+4+l > len(rec.rdata) {
				return nil, nil, 0, errors.New("SvcParam 被截断")
			}
			val := rec.rdata[off+4 : off+4+l]
			switch key {
			case svcKeyECH:
				ech = append([]byte(nil), val...)
			case svcKeyIPv4:
				for i := 0; i+4 <= len(val); i += 4 {
					ips = append(ips, net.IP(val[i:i+4]).String())
				}
			}
			off += 4 + l
		}
	}
	if ech == nil {
		return nil, nil, 0, errors.New("HTTPS 记录里没有 ECH 配置")
	}
	return ech, ips, ttl, nil
}

func parseA(msg []byte) ([]string, time.Duration, error) {
	records, err := parseAnswers(msg)
	if err != nil {
		return nil, 0, err
	}
	ttl := maxTTL
	var ips []string
	for _, rec := range records {
		if rec.typ == dnsTypeA && len(rec.rdata) == 4 {
			ips = append(ips, net.IP(rec.rdata).String())
			if d := time.Duration(rec.ttl) * time.Second; d < ttl {
				ttl = d
			}
		}
	}
	if len(ips) == 0 {
		return nil, 0, errors.New("没有 A 记录")
	}
	return ips, ttl, nil
}
