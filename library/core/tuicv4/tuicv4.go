package tuicv4

import (
	"context"
	"os"
	"reflect"
	"sync"
	"time"
	_ "unsafe"

	tuicv4 "github.com/exclavenetwork/sing-tuicv4"
	"github.com/sagernet/sing/common/bufio"
	"github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/uot"

	"github.com/exclavenetwork/exclave-core/v5/app/proxyman/outbound"
	"github.com/exclavenetwork/exclave-core/v5/common"
	"github.com/exclavenetwork/exclave-core/v5/common/buf"
	"github.com/exclavenetwork/exclave-core/v5/common/bytespool"
	v2net "github.com/exclavenetwork/exclave-core/v5/common/net"
	"github.com/exclavenetwork/exclave-core/v5/common/session"
	"github.com/exclavenetwork/exclave-core/v5/common/singbridge"
	"github.com/exclavenetwork/exclave-core/v5/common/uuid"
	"github.com/exclavenetwork/exclave-core/v5/proxy"
	v2tuic "github.com/exclavenetwork/exclave-core/v5/proxy/tuic"
	"github.com/exclavenetwork/exclave-core/v5/transport"
	"github.com/exclavenetwork/exclave-core/v5/transport/internet"
	v2tls "github.com/exclavenetwork/exclave-core/v5/transport/internet/tls"
	"github.com/exclavenetwork/libexclavecore/errors"
)

type errPathObjHolder struct{}

func newError(values ...interface{}) *errors.Error {
	return errors.New(values...).WithPathObj(errPathObjHolder{})
}

//go:linkname typeCreatorRegistry github.com/exclavenetwork/exclave-core/v5/common.typeCreatorRegistry
var typeCreatorRegistry map[reflect.Type]common.ConfigCreator

func init() {
	tuicType := reflect.TypeOf((*v2tuic.ClientConfig)(nil))
	origCreator := typeCreatorRegistry[tuicType]
	typeCreatorRegistry[tuicType] = func(ctx context.Context, config interface{}) (interface{}, error) {
		cfg, ok := config.(*v2tuic.ClientConfig)
		if !ok {
			if origCreator != nil {
				return origCreator(ctx, config)
			}
			return nil, newError("invalid config type")
		}

		isV4 := false
		if cfg.Uuid == "" || cfg.Uuid == "00000000-0000-0000-0000-000000000000" {
			isV4 = true
		} else {
			if _, err := uuid.ParseHexDashString(cfg.Uuid); err != nil {
				isV4 = true
			}
		}

		if isV4 {
			return NewTuicV4Client(ctx, cfg)
		}
		if origCreator != nil {
			return origCreator(ctx, config)
		}
		return nil, newError("no creator for tuic v5")
	}
}

var (
	_ proxy.Outbound                    = (*TuicV4Outbound)(nil)
	_ proxy.OutboundWithInterfaceUpdate = (*TuicV4Outbound)(nil)
	_ proxy.ClosableOutbound            = (*TuicV4Outbound)(nil)
	_ proxy.OutboundWithSingUot         = (*TuicV4Outbound)(nil)
)

type TuicV4Outbound struct {
	serverAddr    v2net.Destination
	options       tuicv4.ClientOptions
	client        *tuicv4.Client
	clientAccess  sync.Mutex
	udpOverStream bool
}

func NewTuicV4Client(ctx context.Context, config *v2tuic.ClientConfig) (*TuicV4Outbound, error) {
	o := &TuicV4Outbound{
		serverAddr: v2net.Destination{
			Address: config.Address.AsAddress(),
			Port:    v2net.Port(config.Port),
			Network: v2net.Network_UDP,
		},
		udpOverStream: config.UdpOverStream,
	}

	password := config.Password
	if password == "" {
		password = config.Uuid
	}

	switch config.UdpRelayMode {
	case "", "native":
		if config.UdpOverStream {
			return nil, newError("UDP over stream is conflict with UDP relay mode \"native\"")
		}
	case "quic":
	default:
		return nil, newError("invalid UDP relay mode: ", config.UdpRelayMode)
	}
	switch config.CongestionControl {
	case "", "bbr", "new_reno", "cubic":
	default:
		return nil, newError("invalid congestion control: ", config.CongestionControl)
	}

	heartbeat := 10 * time.Second
	if config.Heartbeat > 0 {
		heartbeat = time.Second * time.Duration(config.Heartbeat)
	}

	o.options = tuicv4.ClientOptions{
		Context:           ctx,
		ServerAddress:     singbridge.ToSocksAddr(o.serverAddr),
		Password:          password,
		CongestionControl: config.CongestionControl,
		UDPStream:         config.UdpRelayMode == "quic" || config.UdpOverStream,
		ZeroRTTHandshake:  config.ZeroRttHandshake,
		Heartbeat:         heartbeat,
	}

	return o, nil
}

func (o *TuicV4Outbound) getClient(ctx context.Context, dialer internet.Dialer) (*tuicv4.Client, error) {
	o.clientAccess.Lock()
	defer o.clientAccess.Unlock()
	if o.client != nil {
		return o.client, nil
	}
	handler, ok := dialer.(*outbound.Handler)
	if !ok {
		panic("dialer is not *outbound.Handler")
	}
	if handler.MuxEnabled() {
		return nil, newError("mux enabled")
	}
	if handler.TransportLayerEnabled() {
		return nil, newError("transport layer enabled")
	}
	streamSettings := handler.StreamSettings()
	if streamSettings == nil || streamSettings.SecurityType != "exclave.core.transport.internet.tls.Config" {
		return nil, newError("tls not enabled")
	}
	tlsSettings, ok := streamSettings.SecuritySettings.(*v2tls.Config)
	if !ok {
		return nil, newError("tls not enabled")
	}

	ctx = session.ContextWithDisableALPNByDefault(ctx, true)
	options := o.options
	options.TLSConfig = singbridge.NewTLSConfigWrapper(ctx, tlsSettings, v2tls.WithDestination(o.serverAddr))
	options.Dialer = singbridge.NewQUICDialerWrapper(dialer)
	client, err := tuicv4.NewClient(options)
	if err != nil {
		return nil, err
	}
	o.client = client
	return client, nil
}

func (o *TuicV4Outbound) Process(ctx context.Context, link *transport.Link, dialer internet.Dialer) error {
	client, err := o.getClient(ctx, dialer)
	if err != nil {
		return err
	}

	ob := session.OutboundFromContext(ctx)
	if ob == nil || !ob.Target.IsValid() {
		return newError("target not specified")
	}
	destination := ob.Target

	newError("tunneling request to ", destination, " via ", o.serverAddr.NetAddr()).WriteToLog()

	if destination.Network == v2net.Network_TCP {
		serverConn, err := client.DialConn(ctx, singbridge.ToSocksAddr(destination))
		if err != nil {
			return err
		}

		var firstPayload []byte
		if reader, ok := link.Reader.(buf.TimeoutReader); ok {
			if mb, _ := reader.ReadMultiBufferTimeout(proxy.FirstPayloadTimeout); mb != nil {
				length := mb.Len()
				firstPayload = bytespool.Alloc(length)
				mb, _ = buf.SplitBytes(mb, firstPayload)
				firstPayload = firstPayload[:length]
				buf.ReleaseMulti(mb)
			}
		}
		_, err = serverConn.Write(firstPayload)
		if firstPayload != nil {
			bytespool.Free(firstPayload)
		}
		if err != nil {
			serverConn.Close()
			return singbridge.ReturnError(err)
		}

		return singbridge.ReturnError(bufio.CopyConn(ctx, singbridge.NewPipeConnWrapper(link), serverConn))
	} else {
		if o.udpOverStream {
			serverConn, err := client.DialConn(ctx, uot.RequestDestination(uot.Version))
			if err != nil {
				return err
			}
			streamConn := uot.NewLazyConn(serverConn, uot.Request{Destination: singbridge.ToSocksAddr(destination)})
			return singbridge.ReturnError(bufio.CopyPacketConn(ctx, singbridge.NewPacketConnWrapper(link, destination), streamConn))
		} else {
			serverConn, err := client.ListenPacket(ctx)
			if err != nil {
				return err
			}
			return singbridge.ReturnError(bufio.CopyPacketConn(ctx, singbridge.NewPacketConnWrapper(link, destination), serverConn.(network.PacketConn)))
		}
	}
}

func (o *TuicV4Outbound) InterfaceUpdate() {
	_ = o.Close()
}

func (o *TuicV4Outbound) Close() error {
	o.clientAccess.Lock()
	client := o.client
	o.client = nil
	o.clientAccess.Unlock()
	if client != nil {
		_ = client.CloseWithError(os.ErrClosed)
	}
	return nil
}

func (o *TuicV4Outbound) SingUotEnabled() bool {
	return o.udpOverStream
}
