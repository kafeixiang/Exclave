/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.v2ray;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonToken;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.nekohasekai.sagernet.fmt.gson.JsonLazyInterface;
import io.nekohasekai.sagernet.fmt.gson.JsonOr;

@SuppressWarnings({"SpellCheckingInspection", "unused", "RedundantSuppression"})
public class V2RayConfig {

    public LogObject log;

    public static class LogObject {

        public String access;
        public String error;
        public String loglevel;

    }

    public ApiObject api;

    public static class ApiObject {

        public String tag;
        public List<String> services;

    }

    public DnsObject dns;

    public static class DnsObject {

        public Map<String, String> hosts;

        public List<StringOrServerObject> servers;

        public List<StringOrFakeDnsObject> fakedns;

        public static class StringOrFakeDnsObject extends JsonOr<String, FakeDnsObject> {
            public StringOrFakeDnsObject() {
                super(JsonToken.STRING, JsonToken.BEGIN_OBJECT);
            }
        }

        public static class ServerObject {

            public String address;
            public Integer port;
            public String clientIp;
            public Boolean skipFallback; // deprecated
            public List<String> domains;
            public List<String> expectIPs;
            public String tag;
            public String queryStrategy;
            public String cacheStrategy;
            public String fallbackStrategy;
            public String domainMatcher;
            public List<StringOrFakeDnsObject> fakedns;

            public static class StringOrFakeDnsObject extends JsonOr<String, FakeDnsObject> {
                public StringOrFakeDnsObject() {
                    super(JsonToken.STRING, JsonToken.BEGIN_OBJECT);
                }
            }

        }

        public static class StringOrServerObject extends JsonOr<String, ServerObject> {
            public StringOrServerObject() {
                super(JsonToken.STRING, JsonToken.BEGIN_OBJECT);
            }
        }

        public String clientIp;
        public Boolean disableCache; // deprecated
        public String tag;
        public List<String> domains;
        public List<String> expectIPs;
        public String queryStrategy;
        public String cacheStrategy;
        public String fallbackStrategy;
        public String domainMatcher;

        public Boolean disableFallback; // deprecated
        public Boolean disableFallbackIfMatch; // deprecated
    }

    public RoutingObject routing;

    public static class RoutingObject {

        public String domainStrategy;
        public String domainMatcher;
        public List<RuleObject> rules;

        public static class RuleObject {

            public String type;
            public List<String> domain;
            public List<String> ip;
            public String port;
            public String sourcePort;
            public String network;
            public List<String> source;
            public List<String> user;
            public List<String> inboundTag;
            public List<String> protocol;
            public String attrs;
            public String outboundTag;
            public String balancerTag;

            // SagerNet private

            public List<Integer> uidList;
            public List<String> ssidList;
            public String networkType;

        }

        public List<BalancerObject> balancers;

        public static class BalancerObject {

            public String tag;
            public List<String> selector;
            public StrategyObject strategy;
            public String fallbackTag;

            public static class StrategyObject {

                public String type;
                public StrategyLeastPingConfig settings;

                public static class StrategyLeastPingConfig {

                    public String observerTag;

                }

            }

        }

    }

    public PolicyObject policy;

    public static class PolicyObject {

        public Map<String, LevelPolicyObject> levels;

        public static class LevelPolicyObject {

            public Integer handshake;
            public Integer connIdle;
            public Integer uplinkOnly;
            public Integer downlinkOnly;
            public Boolean statsUserUplink;
            public Boolean statsUserDownlink;
            public Integer bufferSize;

        }

        public SystemPolicyObject system;

        public static class SystemPolicyObject {

            public Boolean statsInboundUplink;
            public Boolean statsInboundDownlink;
            public Boolean statsOutboundUplink;
            public Boolean statsOutboundDownlink;
            public Boolean overrideAccessLogDest;

        }

    }

    public List<InboundObject> inbounds;

    public static class InboundObject {

        public String listen;
        public Integer port;
        public String protocol;
        public LazyInboundConfigurationObject settings;
        public StreamSettingsObject streamSettings;
        public String tag;
        public SniffingObject sniffing;
        public AllocateObject allocate;

        public void init() {
            if (settings != null) {
                settings.init(this);
            }
        }

        public static class SniffingObject {

            public Boolean enabled;
            public List<String> destOverride;
            public Boolean metadataOnly;
            public Boolean routeOnly;

        }

        public static class AllocateObject {

            public String strategy;
            public Integer refresh;
            public Integer concurrency;

        }

    }

    public static class LazyInboundConfigurationObject extends JsonLazyInterface<InboundConfigurationObject> {

        public LazyInboundConfigurationObject() {
        }

        public LazyInboundConfigurationObject(InboundObject ctx, InboundConfigurationObject value) {
            super(value);
            init(ctx);
        }

        public InboundObject ctx;

        public void init(InboundObject ctx) {
            this.ctx = ctx;
        }

        @Nullable
        @Override
        protected Class<? extends InboundConfigurationObject> getType() {
            switch (ctx.protocol.toLowerCase(Locale.ROOT)) {
                case "dokodemo-door":
                    return DokodemoDoorInboundConfigurationObject.class;
                case "http":
                    return HTTPInboundConfigurationObject.class;
                case "socks":
                    return SocksInboundConfigurationObject.class;
                case "vmess":
                    return VMessInboundConfigurationObject.class;
                case "vless":
                    return VLESSInboundConfigurationObject.class;
                case "shadowsocks":
                    return ShadowsocksInboundConfigurationObject.class;
                case "trojan":
                    return TrojanInboundConfigurationObject.class;
                case "vliteu":
                    return VLiteUInboundConfigurationObject.class;
                case "mixed":
                    return MixedInboundConfigurationObject.class;
                case "shadowsocks-2022":
                    return Shadowsocks2022InboundConfigurationObject.class;
                case "shadowsocks-2022-multi":
                    return Shadowsocks2022MultiInboundConfigurationObject.class;
                case "shadowsocks-2022-relay":
                    return Shadowsocks2022RelayInboundConfigurationObject.class;
                case "hysteria2":
                    return Hysteria2InboundConfigurationObject.class;
            }
            return null;
        }

    }

    public interface InboundConfigurationObject {
    }

    public static class DokodemoDoorInboundConfigurationObject implements InboundConfigurationObject {

        public String address;
        public Integer port;
        public String network;
        public Integer timeout;
        public Boolean followRedirect;
        public Integer userLevel;

    }

    public static class HTTPInboundConfigurationObject implements InboundConfigurationObject {

        public Integer timeout;
        public List<AccountObject> accounts;
        public Boolean allowTransparent;
        public Integer userLevel;

        public static class AccountObject {

            public String user;
            public String pass;
            public Map<String, String> headers;
        }

    }

    public static class SocksInboundConfigurationObject implements InboundConfigurationObject {


        public String auth;
        public List<AccountObject> accounts;
        public Boolean udp;
        public String ip;
        public Integer timeout;
        public Integer userLevel;
        public String packetEncoding;

        public static class AccountObject {

            public String user;
            public String pass;

        }

    }

    public static class VMessInboundConfigurationObject implements InboundConfigurationObject {

        public List<ClientObject> clients;
        @SerializedName("default")
        public DefaultObject defaultObject;
        public DetourObject detour;
        public Boolean disableInsecureEncryption;


        public static class ClientObject {

            public String id;
            public Integer level;
            public Integer alterId;
            public String email;
            public String experiments;

        }

        public static class DefaultObject {

            public Integer level;
            public Integer alterId;

        }

        public static class DetourObject {

            public String to;

        }

    }

    public static class VLESSInboundConfigurationObject implements InboundConfigurationObject {

        public List<ClientObject> clients;
        public String decryption;
        public List<FallbackObject> fallbacks;

        public static class ClientObject {

            public String id;
            public Integer level;
            public String email;

        }

        public static class FallbackObject {

            public String alpn;
            public String path;
            public String dest; // fxxk
            public Integer xver;
            public String name;
            public String type;

        }

    }

    public static class ShadowsocksInboundConfigurationObject implements InboundConfigurationObject {

        public String email;
        public String method;
        public String password;
        public Boolean udp;
        public Integer level;
        public String network;
        public Boolean ivCheck;
        public String plugin;
        public String pluginOpts;
        public List<String> pluginArgs;
        public String packetEncoding;
        public List<UserObject> clients;
        public List<UserObject> users;

        public static class UserObject {

            public String password;
            public Integer level;
            public String email;
            public String address;
            public Integer port;

        }

    }

    public static class TrojanInboundConfigurationObject implements InboundConfigurationObject {

        public List<ClientObject> clients;
        public List<FallbackObject> fallbacks;
        public String packetEncoding;

        public static class ClientObject {

            public String password;
            public String email;
            public Integer level;

        }

        public static class FallbackObject {

            public String name;
            public String alpn;
            public String path;
            public String type;
            public String dest; // fxxk
            public Integer xver;

        }

    }

    public static class MixedInboundConfigurationObject implements InboundConfigurationObject {

        public String auth;
        public List<AccountObject> accounts;
        public Boolean udp;
        public String ip;
        public Integer timeout;
        public Integer userLevel;
        public Boolean allowTransparent;
        public String packetEncoding;

        public static class AccountObject {

            public String user;
            public String pass;

        }

    }

    public static class Shadowsocks2022InboundConfigurationObject implements InboundConfigurationObject {

        public String method;
        public String key;
        public Integer level;
        public String email;
        public String network;
        public String plugin;
        public String pluginOpts;
        public List<String> pluginArgs;

    }

    public static class Shadowsocks2022MultiInboundConfigurationObject implements InboundConfigurationObject {

        public String method;
        public String key;
        public String network;
        public String plugin;
        public String pluginOpts;
        public List<String> pluginArgs;
        public List<UserObject> users;

        public static class UserObject {

            public String key;
            public Integer level;
            public String email;

        }

    }

    public static class Shadowsocks2022RelayInboundConfigurationObject implements InboundConfigurationObject {

        public String method;
        public String key;
        public String network;
        public String plugin;
        public String pluginOpts;
        public List<String> pluginArgs;
        public List<DestinationObject> destinations;

        public static class DestinationObject {

            public String key;
            public String address;
            public Integer port;
            public Integer level;
            public String email;

        }

    }

    public static class VLiteUInboundConfigurationObject implements InboundConfigurationObject {

        public String password;
        public Boolean scramblePacket;
        public Boolean enableFEC;
        public Boolean enableStabilization;
        public Boolean enableRenegotiation;
        public Integer handshakeMaskingPaddingSize;

    }

    public static class Hysteria2InboundConfigurationObject implements InboundConfigurationObject {

        public List<ClientObject> clients;

        public static class ClientObject {

            public String email;
            public Integer level;

        }

    }

    public List<OutboundObject> outbounds;

    public static class OutboundObject {

        public String sendThrough;
        public String protocol;
        public LazyOutboundConfigurationObject settings;
        public String tag;
        public StreamSettingsObject streamSettings;
        public ProxySettingsObject proxySettings;
        public MuxObject mux;
        public String domainStrategy;

        public void init() {
            if (settings != null) {
                settings.init(this);
            }
        }

        public static class ProxySettingsObject {

            public String tag;
            public Boolean transportLayer;

        }

        public static class MuxObject {

            public Boolean enabled;
            public Integer concurrency;
            public String packetEncoding;

        }

    }

    public static class LazyOutboundConfigurationObject extends JsonLazyInterface<OutboundConfigurationObject> {

        public LazyOutboundConfigurationObject() {
        }

        public LazyOutboundConfigurationObject(OutboundObject ctx, OutboundConfigurationObject value) {
            super(value);
            init(ctx);
        }

        private OutboundObject ctx;

        public void init(OutboundObject ctx) {
            this.ctx = ctx;
        }

        @Nullable
        @Override
        protected Class<? extends OutboundConfigurationObject> getType() {
            switch (ctx.protocol.toLowerCase(Locale.ROOT)) {
                case "blackhole":
                    return BlackholeOutboundConfigurationObject.class;
                case "dns":
                    return DNSOutboundConfigurationObject.class;
                case "freedom":
                    return FreedomOutboundConfigurationObject.class;
                case "http":
                    return HTTPOutboundConfigurationObject.class;
                case "socks":
                    return SocksOutboundConfigurationObject.class;
                case "vmess":
                    return VMessOutboundConfigurationObject.class;
                case "vless":
                    return VLESSOutboundConfigurationObject.class;
                case "shadowsocks":
                    return ShadowsocksOutboundConfigurationObject.class;
                case "trojan":
                    return TrojanOutboundConfigurationObject.class;
                case "loopback":
                    return LoopbackOutboundConfigurationObject.class;
                case "vliteu":
                    return VLiteUOutboundConfigurationObject.class;
                case "wireguard":
                    return WireGuardOutboundConfigurationObject.class;
                case "ssh":
                    return SSHOutbountConfigurationObject.class;
                case "shadowsocks-2022":
                    return Shadowsocks2022OutboundConfigurationObject.class;
                case "shadowsocks2022":
                    return Shadowsocks_2022OutboundConfigurationObject.class;
                case "hysteria2":
                    return Hysteria2OutboundConfigurationObject.class;
            }
            return null;
        }
    }

    public interface OutboundConfigurationObject {
    }

    public static class BlackholeOutboundConfigurationObject implements OutboundConfigurationObject {

        public ResponseObject response;

        public static class ResponseObject {
            public String type;
        }

    }

    public static class DNSOutboundConfigurationObject implements OutboundConfigurationObject {

        public String network;
        public String address;
        public Integer port;
        public Integer userLevel;

    }

    public static class FreedomOutboundConfigurationObject implements OutboundConfigurationObject {

        public String domainStrategy;
        public String timeout;
        public String redirect;
        public Integer userLevel;
        public String protocolReplacement;

    }

    public static class HTTPOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> servers;
        public Boolean h1SkipWaitForReply;

        public static class ServerObject {

            public String address;
            public Integer port;
            public List<HTTPInboundConfigurationObject.AccountObject> users;

        }

    }

    public static class SocksOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> servers;
        public String version;

        public static class ServerObject {

            public String address;
            public Integer port;
            public List<UserObject> users;

            public static class UserObject {

                public String user;
                public String pass;
                public Integer level;

            }

        }

    }

    public static class VMessOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> vnext;
        public String packetEncoding;

        public static class ServerObject {

            public String address;
            public Integer port;
            public List<UserObject> users;

            public static class UserObject {

                public String id;
                public Integer alterId;
                public String security;
                public Integer level;
                public String experiments;

            }

        }

    }

    public static class ShadowsocksOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> servers;

        public static class ServerObject {

            public String address;
            public Integer port;
            public String method;
            public String password;
            public Integer level;
            public String email;
            public Boolean ivCheck;
            public Boolean experimentReducedIvHeadEntropy;

        }

        public String plugin;
        public String pluginOpts;
        public List<String> pluginArgs;

    }

    public static class Shadowsocks2022OutboundConfigurationObject implements OutboundConfigurationObject {

        public String address;
        public Integer port;
        public String method;
        public String key;
        public String plugin;
        public String pluginOpts;
        public List<String> pluginArgs;

    }

    public static class Shadowsocks_2022OutboundConfigurationObject implements OutboundConfigurationObject {

        public String address;
        public Integer port;
        public String method;
        public String psk;
        public List<String> ipsk;
        public String plugin;
        public String pluginOpts;
        public List<String> pluginArgs;

    }

    public static class VLESSOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> vnext;
        public String packetEncoding;

        public static class ServerObject {

            public String address;
            public Integer port;
            public List<UserObject> users;

            public static class UserObject {

                public String id;
                public String encryption;
                public Integer level;
                public String flow;

            }

        }

    }

    public static class TrojanOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> servers;

        public static class ServerObject {

            public String address;
            public Integer port;
            public String password;
            public String email;
            public Integer level;

        }

    }

    public static class LoopbackOutboundConfigurationObject implements OutboundConfigurationObject {

        public String inboundTag;

    }

    public static class VLiteUOutboundConfigurationObject implements OutboundConfigurationObject {

        public String address;
        public Integer port;
        public String password;
        public Boolean scramblePacket;
        public Boolean enableFEC;
        public Boolean enableStabilization;
        public Boolean enableRenegotiation;
        public Integer handshakeMaskingPaddingSize;

    }

    public static class WireGuardOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<String> address;
        public String secretKey;
        public Integer mtu;
        public Integer workers;
        public List<WireGuardPeerObject> peers;
        public List<Integer> reserved;
        public String domainStrategy;

        public static class WireGuardPeerObject {

            public String publicKey;
            public String preSharedKey;
            public String endpoint;
            public Boolean keepAlive;
            public List<String> allowedIPs;

        }

    }

    public static class SSHOutbountConfigurationObject implements OutboundConfigurationObject {

        public String address;
        public Integer port;
        public String user;
        public String password;
        public String privateKey;
        public String publicKey;
        public Integer userLevel;
        public String clientVersion;
        public List<String> hostKeyAlgorithms;

    }

    public static class Hysteria2OutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> servers;

        public static class ServerObject {

            public String address;
            public Integer port;
            public String email;
            public Integer level;

        }

    }

    public TransportObject transport;

    public static class TransportObject {

        public TLSObject tlsSettings;
        public RealityObject realitySettings;
        public TcpObject tcpSettings;
        public KcpObject kcpSettings;
        public WebSocketObject wsSettings;
        public HttpObject httpSettings;
        public QuicObject quicSettings;
        public DomainSocketObject dsSettings;
        public GrpcObject grpcSettings;
        public MeekObject meekSettings;
        public HTTPUpgradeObject httpupgradeSettings;
        public Hysteria2Object hysteria2Settings;
        public SplitHTTPObject splithttpSettings;
        public DTLSObject dtlsSettings;
        public RequestObject requestSettings;

    }

    public static class StreamSettingsObject {

        public String network;
        public String security;
        public TLSObject tlsSettings;
        public UTLSObject utlsSettings;
        public RealityObject realitySettings;
        public TcpObject tcpSettings;
        public KcpObject kcpSettings;
        public WebSocketObject wsSettings;
        public HttpObject httpSettings;
        public QuicObject quicSettings;
        public DomainSocketObject dsSettings;
        public GrpcObject grpcSettings;
        public MeekObject meekSettings;
        public HTTPUpgradeObject httpupgradeSettings;
        public Hysteria2Object hysteria2Settings;
        public SplitHTTPObject splithttpSettings;
        public DTLSObject dtlsSettings;
        public RequestObject requestSettings;
        public SockoptObject sockopt;

        public static class SockoptObject {

            public Integer mark;
            public Boolean tcpFastOpen;
            public Boolean acceptProxyProtocol;
            public String tproxy;
            public Integer tcpKeepAliveInterval;
            public Integer tcpKeepAliveIdle;
            public Integer tcpFastOpenQueueLength;
            public String bindToDevice;
            public Integer rxBufSize;
            public Integer txBufSize;
            public Boolean forceBufSize;
            public FragmentObject fragment;

            public static class FragmentObject {

                public String packets;
                public String length;
                public String interval;

            }

        }

    }

    public static class TLSObject {

        public String serverName;
        public Boolean allowInsecure;
        public List<String> alpn;
        public Boolean enableSessionResumption;
        public List<CertificateObject> certificates;
        public Boolean disableSystemRoot;
        public List<String> pinnedPeerCertificateChainSha256;
        public Boolean verifyClientCertificate;
        public String fingerprint;
        public String minVersion;
        public String maxVersion;

        public static class CertificateObject {

            public String usage;
            public String certificateFile;
            public String keyFile;
            public List<String> certificate;
            public List<String> key;

        }

    }

    public static class UTLSObject {

        public TLSObject tlsConfig;
        public String imitate;
        public Boolean noSNI;
        public String forceALPN;

    }

    public static class RealityObject {

        public Boolean show;
        public String dest; // fxxk
        public String type;
        public Integer xver;
        public List<String> serverNames;
        public String privateKey;
        public String minClientVer;
        public String maxClientVer;
        public Integer maxTimeDiff;
        public List<String> shortIds;
        public String serverName;
        public String publicKey;
        public String shortId;
        public String spiderX;
        public String fingerprint;
        public String version;

    }

    public static class TcpObject {

        public Boolean acceptProxyProtocol;
        public HeaderObject header;

        public static class HeaderObject {

            public String type;

            public HTTPRequestObject request;
            public HTTPResponseObject response;

            public static class HTTPRequestObject {

                public String version;
                public String method;
                public List<String> path;
                public Map<String, StringOrListObject> headers;

            }

            public static class HTTPResponseObject {

                public String version;
                public String status;
                public String reason;
                public Map<String, StringOrListObject> headers;

            }

            public static class StringOrListObject extends JsonOr<String, List<String>> {
                public StringOrListObject() {
                    super(JsonToken.STRING, JsonToken.BEGIN_ARRAY);
                }
            }

        }

    }


    public static class KcpObject {

        public Integer mtu;
        public Integer tti;
        public Integer uplinkCapacity;
        public Integer downlinkCapacity;
        public Boolean congestion;
        public Integer readBufferSize;
        public Integer writeBufferSize;
        public HeaderObject header;
        public String seed;

        public static class HeaderObject {

            public String type;

        }

    }

    public static class WebSocketObject {

        public Boolean acceptProxyProtocol;
        public String path;
        public Map<String, String> headers;
        public Integer maxEarlyData;
        public String earlyDataHeaderName;
        public Boolean useBrowserForwarding;

    }

    public static class HttpObject {

        public List<String> host;
        public String path;
        public String method;
        public Map<String, List<String>> headers;

    }

    public static class QuicObject {

        public String security;
        public String key;
        public HeaderObject header;

        public static class HeaderObject {

            public String type;

        }

    }

    public static class DomainSocketObject {

        public String path;
        @SerializedName("abstract")
        public Boolean isAbstract;
        public Boolean padding;

    }

    public static class GrpcObject {

        public String serviceName;

    }

    public static class MeekObject {

        public String url;

    }

    public static class HTTPUpgradeObject {

        public String host;
        public String path;
        public Map<String, String> headers;

    }

    public static class Hysteria2Object {

        public String password;
        public CongestionObject congestion;
        public Boolean ignoreClientBandwidth;
        public Boolean useUDPExtension;
        public OBFSObject obfs;

        public static class CongestionObject {
            public Integer upMbps;
            public Integer downMbps;
        }

        public static class OBFSObject {
            public String type;
            public String password;
        }

    }

    public static class SplitHTTPObject {

        public String host;
        public String path;
        public Map<String, String> headers;
        public Boolean noSSEHeader;

    }

    public static class DTLSObject {

        public String mode;
        public String psk;
        public Integer mtu;
        public Integer replayProtectionWindow;

    }

    public static class RequestObject {

        public AssemblerObject assembler;
        public RoundTripperObject roundTripper;

        public static class AssemblerObject {
            public String type;
            public SimpleClientObject simpleClientSettings;
            public SimpleServerObject simpleServerSettings;
            public PacketConnClientObject packetconnClientSettings;
            public PacketConnServerObject packetconnServerSettings;
            public static class SimpleClientObject {
                public Integer maxWriteSize;
                public Integer waitSubsequentWriteMs;
                public Integer initialPollingIntervalMs;
                public Integer maxPollingIntervalMs;
                public Integer minPollingIntervalMs;
                public Float backoffFactor;
                public Integer failedRetryIntervalMs;
            }
            public static class SimpleServerObject {
                public Integer maxWriteSize;
            }
            public static class PacketConnClientObject {
                public String underlyingNetwork;
                public KcpObject kcpSettings;
                public DTLSObject dtlsSettings;
                public Integer maxWriteDelay;
                public Integer maxRequestSize;
                public Integer pollingIntervalInitial;
            }
            public static class PacketConnServerObject {
                public String underlyingNetwork;
                public KcpObject kcpSettings;
                public DTLSObject dtlsSettings;
                public Integer maxWriteSize;
                public Integer maxWriteDurationMs;
                public Integer maxSimultaneousWriteConnection;
                public Integer packetWritingBuffer;
            }
        }

        public static class RoundTripperObject {
            public String type;
            public HttprtClientObject httprtClientSettings;
            public HttprtServerObject httprtServerSettings;
            public static class HttprtClientObject {
                public HttprtHTTPObject http;
                public Boolean allowHTTP;
                public Integer h2PoolSize;
            }
            public static class HttprtServerObject {
                public HttprtHTTPObject http;
                public String noDecodingSessionTag;
            }
            public static class HttprtHTTPObject {
                public String path;
                public String urlPrefix;
            }
        }

    }

    public Map<String, Object> stats;

    public List<FakeDnsObject> fakedns; // deprecated

    public static class FakeDnsObject {

        public String ipPool;
        public Integer poolSize;

    }

    public BrowserForwarderObject browserForwarder;

    public static class BrowserForwarderObject {

        public String listenAddr;
        public Integer listenPort;

    }

    public ReverseObject reverse;

    public static class ReverseObject {
        public List<BridgeObject> bridges;
        public List<PortalObject> portals;

        public static class BridgeObject {
            public String tag;
            public String domain;
        }

        public static class PortalObject {
            public String tag;
            public String domain;
        }
    }

    public ObservatoryObject observatory;

    public static class ObservatoryObject {
        public Set<String> subjectSelector;
        public String probeUrl;
        public String probeInterval;
        public Boolean enableConcurrency;
    }

    public MultiObservatoryObject multiObservatory;

    public static class MultiObservatoryObject {

        public List<MultiObservatoryItem> observers;

        public static class MultiObservatoryItem {
            public String type;
            public String tag;
            public ObservatoryObject settings;
        }
    }

    public void init() {
        if (inbounds != null) {
            for (InboundObject inbound : inbounds) inbound.init();
        }
        if (outbounds != null) {
            for (OutboundObject outbound : outbounds) outbound.init();
        }
    }

}