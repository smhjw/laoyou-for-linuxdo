package echnet

import (
	"context"
	"net"
	"net/http/httptrace"
)

// withConnTrace 在拿到(新建或复用的)连接时回调,用来记录 ECH 是否被接受、实际连到的 IP。
func withConnTrace(ctx context.Context, onConn func(net.Conn)) context.Context {
	return httptrace.WithClientTrace(ctx, &httptrace.ClientTrace{
		GotConn: func(info httptrace.GotConnInfo) {
			if info.Conn != nil {
				onConn(info.Conn)
			}
		},
	})
}
