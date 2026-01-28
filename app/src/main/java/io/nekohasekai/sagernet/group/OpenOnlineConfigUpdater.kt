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

package io.nekohasekai.sagernet.group

import com.github.shadowsocks.plugin.PluginOptions
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.ExtraType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.hysteria2.Hysteria2Bean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.tuic5.Tuic5Bean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libexclavecore.Libexclavecore
import libexclavecore.URL
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64

object OpenOnlineConfigUpdater : GroupUpdater() {

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean
    ) {
        val apiToken: JsonObject
        val baseUrlString: String
        val baseLink: URL
        val certSha256: String?
        try {
            apiToken = parseJson(subscription.token).asJsonObject

            val version = apiToken.getInt("version")
            if (version != 1) {
                if (version != null) {
                    error("Unsupported OOC version $version")
                } else {
                    error("Missing field: version")
                }
            }
            val baseUrl = apiToken.getString("baseUrl")
            when {
                baseUrl.isNullOrEmpty() -> {
                    error("Missing field: baseUrl")
                }
                baseUrl.endsWith("/") -> {
                    error("baseUrl must not contain a trailing slash")
                }
                !baseUrl.startsWith("https://", ignoreCase = true) -> {
                    error("Protocol scheme must be https")
                }
                else -> {
                    baseUrlString = baseUrl
                    baseLink = Libexclavecore.parseURL(baseUrl)
                }
            }
            val secret = apiToken.getString("secret")
            if (secret.isNullOrEmpty()) error("Missing field: secret")
            baseLink.addPathSegments(secret, "ooc/v1")

            val userId = apiToken.getString("userId")
            if (userId.isNullOrEmpty()) error("Missing field: userId")
            baseLink.addPathSegments(userId)
            
            certSha256 = apiToken.getString("certSha256")
            if (!certSha256.isNullOrEmpty()) {
                if (certSha256.length != 64 || !certSha256.all { (it in '0'..'9') || (it in 'a'..'f') }) {
                    error("certSha256 must be a 64-char hexadecimal string with lowercase letters")
                }
            }
        } catch (e: Exception) {
            error(e.message ?: app.getString(R.string.ooc_subscription_token_invalid))
        }

        // 使用 OkHttp 重新实现请求逻辑，以支持已删除的 pinnedSHA256 功能
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS, ConnectionSpec.CLEARTEXT))

        // 实现 pinnedSHA256 功能
        if (!certSha256.isNullOrEmpty()) {
            val host = java.net.URL(baseUrlString).host
            val base64 = Base64.Default.encode(certSha256.hexToByteArray())
            val pinner = CertificatePinner.Builder()
                .add(host, "sha256/$base64")
                .build()
            clientBuilder.certificatePinner(pinner)
        }

        // 实现 useSocks5 功能
        if (SagerNet.started && DataStore.startedProfile > 0) {
            clientBuilder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", DataStore.socksPort)))
        }

        val client = clientBuilder.build()
        val request = Request.Builder()
            .url(baseLink.toString())
            .header("User-Agent", subscription.customUserAgent.takeIf { it.isNotEmpty() } ?: USER_AGENT)
            .build()

        val responseContent = withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.body?.string() ?: error("empty response")
                }
            } catch (e: Exception) {
                error("Failed to fetch OOC: ${e.readableMessage}")
            }
        }

        val oocResponse = try {
            parseJson(responseContent).asJsonObject
        } catch(_: Exception) {
            error("invalid response")
        }

        subscription.username = oocResponse.getString("username") ?: ""
        subscription.bytesUsed = oocResponse.getLong("bytesUsed") ?: -1
        subscription.bytesRemaining = oocResponse.getLong("bytesRemaining") ?: -1
        subscription.expiryDate = oocResponse.getLong("expiryDate") ?: -1
        subscription.protocols = oocResponse.getStringList("protocols")
            ?: error("missing protocols")
        subscription.applyDefaultValues()

        for (protocol in subscription.protocols) {
            if (protocol !in supportedProtocols) {
                userInterface?.onUpdateFailure(proxyGroup, app.getString(R.string.ooc_missing_protocol, protocol))
            }
        }

        var profiles = mutableListOf<AbstractBean>()

        val pattern = Regex(subscription.nameFilter)
        val pattern1 = Regex(subscription.nameFilter1)
        for (protocol in subscription.protocols) {
            val profilesInProtocol = oocResponse.getArray(protocol) ?: continue

            for (profile in profilesInProtocol) {
                val bean: AbstractBean = when (protocol) {
                    "shadowsocks" -> ShadowsocksBean().apply {
                        serverAddress = profile.getString("address") ?: error("missing address")
                        serverPort = profile.getInt("port") ?: error("missing port")
                        method = profile.getString("method") ?: error("missing method")
                        password = profile.getString("password")

                        val pluginId = when (val id = profile.getString("pluginName")) {
                            "simple-obfs" -> "obfs-local"
                            else -> id
                        }
                        if (!pluginId.isNullOrEmpty()) {
                            plugin = PluginOptions(pluginId, profile.getString("pluginOptions")).toString(trimId = false)
                        }
                    }
                    "trojan" -> TrojanBean().apply {
                        serverAddress = profile.getString("address") ?: error("missing address")
                        serverPort = profile.getInt("port") ?: error("missing port")
                        password = profile.getString("password")
                        parseV2RayStream(profile, this)
                    }
                    "vmess" -> VMessBean().apply {
                        serverAddress = profile.getString("address") ?: error("missing address")
                        serverPort = profile.getInt("port") ?: error("missing port")
                        uuid = profile.getString("uuid") ?: error("missing uuid")
                        alterId = profile.getInt("alterId") ?: 0
                        encryption = profile.getString("security") ?: "auto"
                        parseV2RayStream(profile, this)
                    }
                    "vless" -> VLESSBean().apply {
                        serverAddress = profile.getString("address") ?: error("missing address")
                        serverPort = profile.getInt("port") ?: error("missing port")
                        uuid = profile.getString("uuid") ?: error("missing uuid")
                        encryption = profile.getString("encryption") ?: "none"
                        flow = profile.getString("flow") ?: ""
                        parseV2RayStream(profile, this)
                    }
                    "hysteria2" -> Hysteria2Bean().apply {
                        serverAddress = profile.getString("address") ?: error("missing address")
                        serverPort = profile.getInt("port") ?: error("missing port")
                        auth = profile.getString("auth") ?: ""
                        obfsPassword = profile.getString("obfs") ?: ""
                        if (obfsPassword.isNotEmpty()) {
                            obfsType = profile.getString("obfsType") ?: "salamander"
                        }
                        sni = profile.getString("sni") ?: ""
                        allowInsecure = profile.getBoolean("allowInsecure") ?: false
                        uploadMbps = profile.getLong("uploadMbps") ?: 0L
                        downloadMbps = profile.getLong("downloadMbps") ?: 0L
                    }
                    "tuic" -> Tuic5Bean().apply {
                        serverAddress = profile.getString("address") ?: error("missing address")
                        serverPort = profile.getInt("port") ?: error("missing port")
                        uuid = profile.getString("uuid") ?: ""
                        password = profile.getString("password") ?: ""
                        sni = profile.getString("sni") ?: ""
                        allowInsecure = profile.getBoolean("allowInsecure") ?: false
                        congestionControl = profile.getString("congestionControl") ?: "cubic"
                        udpRelayMode = profile.getString("udpRelayMode") ?: "native"
                    }
                    else -> continue
                }

                bean.name = profile.getString("name") ?: ""
                appendExtraInfo(profile, bean)

                if (subscription.nameFilter.isNotEmpty() && pattern.containsMatchIn(bean.name)) {
                    continue
                }
                if (subscription.nameFilter1.isNotEmpty() && !pattern1.containsMatchIn(bean.name)) {
                    continue
                }

                profiles.add(bean)
            }
        }

        profiles.forEach { it.applyDefaultValues() }

        val exists = SagerDatabase.proxyDao.getByGroup(proxyGroup.id)
        val duplicate = ArrayList<String>()
        if (subscription.deduplication) {
            val uniqueProfiles = LinkedHashSet<AbstractBean>()
            val uniqueNames = HashMap<AbstractBean, String>()
            for (proxy in profiles) {
                if (!uniqueProfiles.add(proxy)) {
                    val index = uniqueProfiles.indexOf(proxy)
                    if (uniqueNames.containsKey(proxy)) {
                        val name = uniqueNames[proxy]!!.replace(" ($index)", "")
                        if (name.isNotEmpty()) {
                            duplicate.add("$name ($index)")
                            uniqueNames[proxy] = ""
                        }
                    }
                    duplicate.add(proxy.displayName() + " ($index)")
                } else {
                    uniqueNames[proxy] = proxy.displayName()
                }
            }
            uniqueProfiles.retainAll(uniqueNames.keys)
            profiles = uniqueProfiles.toMutableList()
        }

        val profileMap = profiles.associateBy { it.profileId }
        val toDelete = ArrayList<ProxyEntity>()
        val toReplace = exists.mapNotNull { entity ->
            val profileId = entity.requireBean().profileId
            if (profileMap.contains(profileId)) profileId to entity else let {
                toDelete.add(entity)
                null
            }
        }.toMap()

        val toUpdate = ArrayList<ProxyEntity>()
        val added = mutableListOf<String>()
        val updated = mutableMapOf<String, String>()
        val deleted = toDelete.map { it.displayName() }

        var userOrder = 1L
        var changed = toDelete.size
        for ((profileId, bean) in profileMap.entries) {
            val name = bean.displayName()
            if (toReplace.contains(profileId)) {
                val entity = toReplace[profileId]!!
                val existsBean = entity.requireBean()
                existsBean.applyFeatureSettings(bean)
                when {
                    existsBean != bean -> {
                        changed++
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        updated[entity.displayName()] = name
                    }
                    entity.userOrder != userOrder -> {
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        entity.userOrder = userOrder
                    }
                }
            } else {
                changed++
                SagerDatabase.proxyDao.addProxy(ProxyEntity(
                    groupId = proxyGroup.id, userOrder = userOrder
                ).apply {
                    putBean(bean)
                })
                added.add(name)
            }
            userOrder++
        }

        SagerDatabase.proxyDao.updateProxy(toUpdate)
        SagerDatabase.proxyDao.deleteProxy(toDelete)

        subscription.lastUpdated = System.currentTimeMillis() / 1000
        SagerDatabase.groupDao.updateGroup(proxyGroup)
        finishUpdate(proxyGroup)

        userInterface?.onUpdateSuccess(
            proxyGroup, changed, added, updated, deleted, duplicate
        )
    }

    private fun parseV2RayStream(profile: JsonObject, bean: StandardV2RayBean) {
        bean.type = profile.getString("network") ?: "tcp"
        bean.headerType = profile.getString("headerType") ?: "none"
        bean.host = profile.getString("host") ?: ""
        bean.path = profile.getString("path") ?: ""
        bean.security = profile.getString("tls") ?: "none"
        bean.sni = profile.getString("sni") ?: ""
        bean.alpn = profile.getString("alpn") ?: ""
        bean.allowInsecure = profile.getBoolean("allowInsecure") ?: false
        bean.utlsFingerprint = profile.getString("utlsFingerprint") ?: ""
        bean.grpcServiceName = profile.getString("serviceName") ?: ""
        if (bean.security == "reality") {
            bean.realityPublicKey = profile.getString("realityPublicKey") ?: ""
            bean.realityShortId = profile.getString("realityShortId") ?: ""
            bean.realityFingerprint = profile.getString("realityFingerprint") ?: "chrome"
        }
    }

    fun appendExtraInfo(profile: JsonObject, bean: AbstractBean) {
        bean.extraType = ExtraType.OOCv1
        bean.profileId = profile.getString("id")
        bean.group = profile.getString("group")
        bean.owner = profile.getString("owner")
        bean.tags = profile.getStringList("tags")
    }

    val supportedProtocols = arrayOf("shadowsocks", "trojan", "vmess", "vless", "hysteria2", "tuic")

}
