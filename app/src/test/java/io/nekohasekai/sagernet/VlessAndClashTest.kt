package io.nekohasekai.sagernet

import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.parseV2Ray
import io.nekohasekai.sagernet.fmt.v2ray.toUri
import io.nekohasekai.sagernet.group.parseClashProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessAndClashTest {

    @Test
    fun testParseVlessLinkFromUser() {
        val link = "vless://52ebd9d7-afde-4a6c-a483-9e4f083933e0@pq.aws52.yydjc.top:443/?type=tcp&encryption=none&flow=xtls-rprx-vision&sni=cdn-dynmedia-1.microsoft.com&fp=safari&security=reality&pbk=FrLhPuCVfzdM5rppzxHXQ5BZSGf1YHrXgo-cCtecGSo&sid=f3b38af2&packetEncoding=xudp#%F0%9F%87%AF%F0%9F%87%B5AWS%E6%97%A5%E6%9C%AC05%20%7C%20%E7%94%B5%E4%BF%A1%E7%A7%BB%E5%8A%A8%E8%81%94%E9%80%9A%E6%8E%A8%E8%8D%90"
        val bean = parseV2Ray(link) as VLESSBean

        assertEquals("52ebd9d7-afde-4a6c-a483-9e4f083933e0", bean.uuid)
        assertEquals("pq.aws52.yydjc.top", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("tcp", bean.type)
        assertEquals("none", bean.encryption)
        assertEquals("xtls-rprx-vision", bean.flow)
        assertEquals("cdn-dynmedia-1.microsoft.com", bean.sni)
        assertEquals("reality", bean.security)
        assertEquals("safari", bean.realityFingerprint)
        assertEquals("safari", bean.utlsFingerprint)
        assertEquals("FrLhPuCVfzdM5rppzxHXQ5BZSGf1YHrXgo-cCtecGSo", bean.realityPublicKey)
        assertEquals("f3b38af2", bean.realityShortId)
        assertEquals("xudp", bean.packetEncoding)

        val uri = bean.toUri()
        assertNotNull(uri)
        assertTrue(uri!!.contains("flow=xtls-rprx-vision"))
        assertTrue(uri.contains("fp=safari"))
        assertTrue(uri.contains("packetEncoding=xudp"))
    }

    @Test
    fun testClashVlessReality() {
        val proxyMap = mapOf(
            "name" to "clash-vless",
            "type" to "vless",
            "server" to "pq.aws52.yydjc.top",
            "port" to 443,
            "uuid" to "52ebd9d7-afde-4a6c-a483-9e4f083933e0",
            "network" to "tcp",
            "tls" to true,
            "servername" to "cdn-dynmedia-1.microsoft.com",
            "flow" to "xtls-rprx-vision",
            "client-fingerprint" to "safari",
            "packet-encoding" to "xudp",
            "reality-opts" to mapOf(
                "public-key" to "FrLhPuCVfzdM5rppzxHXQ5BZSGf1YHrXgo-cCtecGSo",
                "short-id" to "f3b38af2"
            )
        )

        val beans = parseClashProxy(proxyMap)
        assertEquals(1, beans.size)
        val bean = beans[0] as VLESSBean
        assertEquals("xtls-rprx-vision", bean.flow)
        assertEquals("reality", bean.security)
        assertEquals("safari", bean.realityFingerprint)
        assertEquals("safari", bean.utlsFingerprint)
        assertEquals("xudp", bean.packetEncoding)
        assertEquals("cdn-dynmedia-1.microsoft.com", bean.sni)
    }

    @Test
    fun testClashVlessRealityOptsFingerprintAndSni() {
        val proxyMap = mapOf(
            "name" to "clash-vless-opts",
            "type" to "vless",
            "server" to "1.2.3.4",
            "port" to 443,
            "uuid" to "52ebd9d7-afde-4a6c-a483-9e4f083933e0",
            "network" to "tcp",
            "sni" to "my-sni.com",
            "flow" to "xtls-rprx-vision-udp443",
            "packet-encoding" to "none",
            "reality-opts" to mapOf(
                "public-key" to "FrLhPuCVfzdM5rppzxHXQ5BZSGf1YHrXgo-cCtecGSo",
                "short-id" to "f3b38af2",
                "client-fingerprint" to "chrome"
            )
        )

        val beans = parseClashProxy(proxyMap)
        assertEquals(1, beans.size)
        val bean = beans[0] as VLESSBean
        assertEquals("xtls-rprx-vision-udp443", bean.flow)
        assertEquals("reality", bean.security)
        assertEquals("chrome", bean.realityFingerprint)
        assertEquals("chrome", bean.utlsFingerprint)
        assertEquals("none", bean.packetEncoding)
        assertEquals("my-sni.com", bean.sni)
    }

    @Test
    fun testClashFingerprintCertSha256VsUtls() {
        // 64-char hex should be cert pin
        val certSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val proxyMapCert = mapOf(
            "name" to "cert-pin",
            "type" to "vless",
            "server" to "1.2.3.4",
            "port" to 443,
            "uuid" to "52ebd9d7-afde-4a6c-a483-9e4f083933e0",
            "tls" to true,
            "fingerprint" to certSha256
        )
        val beanCert = parseClashProxy(proxyMapCert)[0] as VLESSBean
        assertEquals(certSha256, beanCert.pinnedPeerCertificateSha256)
        assertTrue(beanCert.allowInsecure)

        // Non-64-char (e.g. "firefox") should be uTLS fingerprint
        val proxyMapUtls = mapOf(
            "name" to "utls-fp",
            "type" to "vless",
            "server" to "1.2.3.4",
            "port" to 443,
            "uuid" to "52ebd9d7-afde-4a6c-a483-9e4f083933e0",
            "tls" to true,
            "fingerprint" to "firefox"
        )
        val beanUtls = parseClashProxy(proxyMapUtls)[0] as VLESSBean
        assertEquals("firefox", beanUtls.utlsFingerprint)
        assertTrue(beanUtls.pinnedPeerCertificateSha256.isNullOrEmpty())
    }
}
