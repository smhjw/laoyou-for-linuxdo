package echnet

import (
	"encoding/binary"
	"os"
	"strings"
	"testing"
)

// 构造一条最小的 HTTPS 记录应答:ech=<1,2,3> 与 ipv4hint=1.2.3.4。
func httpsAnswer(ech []byte, ipv4 [4]byte) []byte {
	var rdata []byte
	rdata = binary.BigEndian.AppendUint16(rdata, 1) // SvcPriority
	rdata = append(rdata, 0)                        // TargetName "."
	rdata = binary.BigEndian.AppendUint16(rdata, svcKeyIPv4)
	rdata = binary.BigEndian.AppendUint16(rdata, 4)
	rdata = append(rdata, ipv4[:]...)
	rdata = binary.BigEndian.AppendUint16(rdata, svcKeyECH)
	rdata = binary.BigEndian.AppendUint16(rdata, uint16(len(ech)))
	rdata = append(rdata, ech...)

	msg := []byte{0, 0, 0x81, 0x80, 0, 1, 0, 1, 0, 0, 0, 0}
	msg = append(msg, 5, 'l', 'i', 'n', 'u', 'x', 2, 'd', 'o', 0, 0, dnsTypeHTTPS, 0, 1)
	msg = append(msg, 0xC0, 12) // 压缩指针指回问题里的域名
	msg = binary.BigEndian.AppendUint16(msg, dnsTypeHTTPS)
	msg = binary.BigEndian.AppendUint16(msg, 1)
	msg = binary.BigEndian.AppendUint32(msg, 300)
	msg = binary.BigEndian.AppendUint16(msg, uint16(len(rdata)))
	return append(msg, rdata...)
}

func TestParseHTTPS(t *testing.T) {
	ech, ips, ttl, err := parseHTTPS(httpsAnswer([]byte{1, 2, 3}, [4]byte{1, 2, 3, 4}))
	if err != nil {
		t.Fatal(err)
	}
	if string(ech) != "\x01\x02\x03" || len(ips) != 1 || ips[0] != "1.2.3.4" || ttl.Seconds() != 300 {
		t.Fatalf("ech=%v ips=%v ttl=%v", ech, ips, ttl)
	}
}

func TestParseHTTPSWithoutECHFails(t *testing.T) {
	msg := []byte{0, 0, 0x81, 0x80, 0, 1, 0, 0, 0, 0, 0, 0, 5, 'l', 'i', 'n', 'u', 'x', 2, 'd', 'o', 0, 0, dnsTypeHTTPS, 0, 1}
	if _, _, _, err := parseHTTPS(msg); err == nil {
		t.Fatal("没有 ECH 配置时应当报错")
	}
}

func TestParseRejectsTruncated(t *testing.T) {
	full := httpsAnswer([]byte{1, 2, 3}, [4]byte{1, 2, 3, 4})
	for n := 12; n < len(full); n++ {
		if _, _, _, err := parseHTTPS(full[:n]); err == nil {
			t.Fatalf("截断到 %d 字节时应当报错", n)
		}
	}
}

func TestSplitServers(t *testing.T) {
	got := splitServers(" https://a/dns \n\n https://b/q\n")
	if strings.Join(got, ",") != "https://a/dns,https://b/q" {
		t.Fatal(got)
	}
}

// 真实网络测试:ECHNET_LIVE=1 go test -run Live -v
func TestLiveLinuxDo(t *testing.T) {
	if os.Getenv("ECHNET_LIVE") != "1" {
		t.Skip("设置 ECHNET_LIVE=1 才跑真实网络测试")
	}
	c, err := NewClient("https://kudo.ddd.oaifree.com/query-dns\nhttps://edge.1molchuan.top/linuxdo")
	if err != nil {
		t.Fatal(err)
	}
	defer c.Close()
	for i := 0; i < 3; i++ {
		resp, err := c.Send(&Request{
			Method:    "GET",
			URL:       "https://linux.do/srv/status",
			Headers:   "User-Agent: Mozilla/5.0 (Linux; Android 14) echnet-test\nAccept: */*",
			TimeoutMs: 15000,
		})
		if err != nil {
			t.Fatalf("第 %d 次: %v", i+1, err)
		}
		t.Logf("第 %d 次: %d %s %s ECH=%v IP=%s 正文 %d 字节", i+1, resp.Status, resp.Reason, resp.Proto, resp.ECHAccepted, resp.RemoteIP, len(resp.Body))
		if !resp.ECHAccepted {
			t.Fatal("ECH 未被接受")
		}
		// 403 是 Cloudflare 对没有 cf_clearance 的客户端的人机验证页,说明请求已经到了 linux.do
		if resp.Status != 200 && resp.Status != 403 {
			t.Fatalf("意外的状态码 %d", resp.Status)
		}
	}
	t.Log(c.Stats())
}
