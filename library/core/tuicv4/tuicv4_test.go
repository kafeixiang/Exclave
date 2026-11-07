package tuicv4

import (
	"bufio"
	"context"
	"crypto/tls"
	"crypto/x509"
	"io"
	"net"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"

	tuicv4 "github.com/exclavenetwork/sing-tuicv4"
	"github.com/sagernet/sing/common/buf"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	aTLS "github.com/sagernet/sing/common/tls"

	"github.com/exclavenetwork/exclave-core/v5/common"
	v2net "github.com/exclavenetwork/exclave-core/v5/common/net"
	v2tuic "github.com/exclavenetwork/exclave-core/v5/proxy/tuic"
)

type simpleTLSConfig struct {
	cfg *tls.Config
}

func (s *simpleTLSConfig) ServerName() string                      { return s.cfg.ServerName }
func (s *simpleTLSConfig) SetServerName(serverName string)         { s.cfg.ServerName = serverName }
func (s *simpleTLSConfig) NextProtos() []string                    { return s.cfg.NextProtos }
func (s *simpleTLSConfig) SetNextProtos(nextProto []string)        { s.cfg.NextProtos = nextProto }
func (s *simpleTLSConfig) HandshakeTimeout() time.Duration         { return 5 * time.Second }
func (s *simpleTLSConfig) SetHandshakeTimeout(timeout time.Duration) {}
func (s *simpleTLSConfig) STDConfig() (*tls.Config, error)         { return s.cfg, nil }
func (s *simpleTLSConfig) Client(conn net.Conn) (aTLS.Conn, error) {
	return tls.Client(conn, s.cfg), nil
}
func (s *simpleTLSConfig) Clone() aTLS.Config {
	return &simpleTLSConfig{cfg: s.cfg.Clone()}
}

func getTLSConfig(t *testing.T) aTLS.Config {
	certBytes, err := os.ReadFile("/tmp/tuic_v4_test/cert.pem")
	if err != nil {
		t.Skipf("skipping TUIC v4 E2E test: %v", err)
	}
	cp := x509.NewCertPool()
	cp.AppendCertsFromPEM(certBytes)

	tlsCfg := &tls.Config{
		RootCAs:            cp,
		ServerName:         "localhost",
		NextProtos:         []string{"h3"},
		InsecureSkipVerify: false,
	}
	return &simpleTLSConfig{cfg: tlsCfg}
}

func TestTuicV4DirectE2E(t *testing.T) {
	// 1. Test TCP Relay (HTTP GET to 127.0.0.1:18080)
	t.Run("TCP_HTTP_Relay", func(t *testing.T) {
		opts := tuicv4.ClientOptions{
			Context:           context.Background(),
			Dialer:            N.SystemDialer,
			ServerAddress:     M.ParseSocksaddr("127.0.0.1:18443"),
			TLSConfig:         getTLSConfig(t),
			Password:          "test_tuic_token_888",
			CongestionControl: "bbr",
			Heartbeat:         10 * time.Second,
		}

		client, err := tuicv4.NewClient(opts)
		if err != nil {
			t.Fatalf("failed to create tuicv4 client: %v", err)
		}
		defer client.CloseWithError(nil)

		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		conn, err := client.DialConn(ctx, M.ParseSocksaddr("127.0.0.1:18080"))
		if err != nil {
			t.Fatalf("client.DialConn failed: %v", err)
		}
		defer conn.Close()

		req, err := http.NewRequestWithContext(ctx, "GET", "http://127.0.0.1:18080/", nil)
		if err != nil {
			t.Fatalf("failed to create request: %v", err)
		}
		if err := req.Write(conn); err != nil {
			t.Fatalf("failed to write request to tuic stream: %v", err)
		}

		resp, err := http.ReadResponse(bufio.NewReader(conn), req)
		if err != nil {
			t.Fatalf("failed to read response: %v", err)
		}
		defer resp.Body.Close()

		body, err := io.ReadAll(resp.Body)
		if err != nil {
			t.Fatalf("failed to read body: %v", err)
		}

		bodyStr := string(body)
		t.Logf("Received HTTP response: %s (status %d)", bodyStr, resp.StatusCode)
		if resp.StatusCode != 200 || !strings.Contains(bodyStr, "TUIC-V4-TCP-VERIFIED-SUCCESS") {
			t.Fatalf("unexpected body: %s", bodyStr)
		}
	})

	// 2. Test UDP Relay - Native Datagram mode
	t.Run("UDP_Native_Datagram_Echo", func(t *testing.T) {
		opts := tuicv4.ClientOptions{
			Context:           context.Background(),
			Dialer:            N.SystemDialer,
			ServerAddress:     M.ParseSocksaddr("127.0.0.1:18443"),
			TLSConfig:         getTLSConfig(t),
			Password:          "test_tuic_token_888",
			CongestionControl: "bbr",
			UDPStream:         false,
			Heartbeat:         10 * time.Second,
		}

		client, err := tuicv4.NewClient(opts)
		if err != nil {
			t.Fatalf("failed to create tuicv4 client: %v", err)
		}
		defer client.CloseWithError(nil)

		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		packetConn, err := client.ListenPacket(ctx)
		if err != nil {
			t.Fatalf("client.ListenPacket failed: %v", err)
		}
		defer packetConn.Close()

		pConn := packetConn.(N.PacketConn)
		dest := M.ParseSocksaddr("127.0.0.1:18081")
		testBuf := buf.As([]byte("PING_NATIVE_UDP_TUIC_V4"))

		err = pConn.WritePacket(testBuf, dest)
		if err != nil {
			t.Fatalf("WritePacket failed: %v", err)
		}

		recvBuf := buf.New()
		defer recvBuf.Release()
		from, err := pConn.ReadPacket(recvBuf)
		if err != nil {
			t.Fatalf("ReadPacket failed: %v", err)
		}

		res := string(recvBuf.Bytes())
		t.Logf("Received UDP Echo from %v: %s", from, res)
		if !strings.Contains(res, "ECHO:PING_NATIVE_UDP_TUIC_V4") {
			t.Fatalf("unexpected UDP echo: %s", res)
		}
	})

	// 3. Test UDP Relay - QUIC UniStream mode (udpRelayMode: "quic", Exclave standard)
	t.Run("UDP_QUIC_UniStream_Echo", func(t *testing.T) {
		opts := tuicv4.ClientOptions{
			Context:           context.Background(),
			Dialer:            N.SystemDialer,
			ServerAddress:     M.ParseSocksaddr("127.0.0.1:18443"),
			TLSConfig:         getTLSConfig(t),
			Password:          "test_tuic_token_888",
			CongestionControl: "bbr",
			UDPStream:         true,
			Heartbeat:         10 * time.Second,
		}

		client, err := tuicv4.NewClient(opts)
		if err != nil {
			t.Fatalf("failed to create tuicv4 client: %v", err)
		}
		defer client.CloseWithError(nil)

		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		packetConn, err := client.ListenPacket(ctx)
		if err != nil {
			t.Fatalf("client.ListenPacket failed: %v", err)
		}
		defer packetConn.Close()

		pConn := packetConn.(N.PacketConn)
		dest := M.ParseSocksaddr("127.0.0.1:18081")
		testBuf := buf.As([]byte("PING_QUIC_STREAM_UDP_TUIC_V4"))

		err = pConn.WritePacket(testBuf, dest)
		if err != nil {
			t.Fatalf("WritePacket failed: %v", err)
		}

		recvBuf := buf.New()
		defer recvBuf.Release()
		from, err := pConn.ReadPacket(recvBuf)
		if err != nil {
			t.Fatalf("ReadPacket failed: %v", err)
		}

		res := string(recvBuf.Bytes())
		t.Logf("Received QUIC Stream UDP Echo from %v: %s", from, res)
		if !strings.Contains(res, "ECHO:PING_QUIC_STREAM_UDP_TUIC_V4") {
			t.Fatalf("unexpected UDP echo: %s", res)
		}
	})
}

func TestTuicV4RegistryHook(t *testing.T) {
	// Verify that common.CreateObject routes NIL UUID to TuicV4Outbound
	cfgV4 := &v2tuic.ClientConfig{
		Address:           v2net.NewIPOrDomain(v2net.LocalHostIP),
		Port:              18443,
		Uuid:              "00000000-0000-0000-0000-000000000000",
		Password:          "test_tuic_token_888",
		CongestionControl: "bbr",
		UdpRelayMode:      "quic",
	}

	objV4, err := common.CreateObject(context.Background(), cfgV4)
	if err != nil {
		t.Fatalf("CreateObject for TUIC v4 failed: %v", err)
	}

	if _, ok := objV4.(*TuicV4Outbound); !ok {
		t.Fatalf("expected *TuicV4Outbound, got %T", objV4)
	}
	t.Logf("Successfully created %T for NIL UUID (TUIC v4)", objV4)

	// Verify that empty UUID also routes to TuicV4Outbound
	cfgV4EmptyUUID := &v2tuic.ClientConfig{
		Address:           v2net.NewIPOrDomain(v2net.LocalHostIP),
		Port:              18443,
		Uuid:              "",
		Password:          "test_tuic_token_888",
		CongestionControl: "bbr",
	}
	objV4Empty, err := common.CreateObject(context.Background(), cfgV4EmptyUUID)
	if err != nil {
		t.Fatalf("CreateObject for empty UUID failed: %v", err)
	}
	if _, ok := objV4Empty.(*TuicV4Outbound); !ok {
		t.Fatalf("expected *TuicV4Outbound for empty UUID, got %T", objV4Empty)
	}
	t.Logf("Successfully created %T for empty UUID", objV4Empty)

	// Verify that valid non-NIL UUID routes to standard TUIC v5
	cfgV5 := &v2tuic.ClientConfig{
		Address:           v2net.NewIPOrDomain(v2net.LocalHostIP),
		Port:              18443,
		Uuid:              "12345678-1234-1234-1234-123456789abc",
		Password:          "v5_password",
		CongestionControl: "bbr",
	}
	objV5, err := common.CreateObject(context.Background(), cfgV5)
	if err != nil {
		t.Fatalf("CreateObject for TUIC v5 failed: %v", err)
	}
	if _, ok := objV5.(*TuicV4Outbound); ok {
		t.Fatalf("expected v5 outbound, but got TuicV4Outbound!")
	}
	t.Logf("Successfully routed valid UUID to TUIC v5: %T", objV5)
}
