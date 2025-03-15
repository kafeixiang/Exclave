package io.nekohasekai.sagernet.fmt.matsuri

import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.plugin.MatsuriJSInterface
import io.nekohasekai.sagernet.plugin.MatsuriPluginManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun parseShareLink(plgId: String, protocolId: String, link: String): MatsuriBean =
    suspendCoroutine {
        runOnIoDispatcher {
            val jsi = MatsuriJSInterface.Default.requireJsi(plgId)
            jsi.lock()

            try {
                jsi.init()

                val jsip = jsi.switchProtocol(protocolId)
                val sharedStorage = jsip.parseShareLink(link)

                val bean = MatsuriBean()
                bean.plgId = plgId
                bean.protocolId = protocolId
                bean.sharedStorage = MatsuriBean.tryParseJSON(sharedStorage)
                bean.onSharedStorageSet()

                it.resume(bean)
            } catch (e: Exception) {
                Logs.e(e.toString())
                it.resume(MatsuriBean().apply {
                    this.plgId = plgId
                    this.protocolId = protocolId
                })
            }

            jsi.unlock()
            // destroy when all link parsed
        }
    }

fun MatsuriBean.toUri(): String {
    return sharedStorage.getStr("shareLink")
}

// Only run in bg process
// seems no concurrent
suspend fun MatsuriBean.updateAllConfig(port: Int) = suspendCoroutine {
    allConfig = null

    runOnIoDispatcher {
        val jsi = MatsuriJSInterface.Default.requireJsi(plgId)
        jsi.lock()

        try {
            jsi.init()
            val jsip = jsi.switchProtocol(protocolId)

            // runtime arguments
            val otherArgs = mutableMapOf<String, Any>()
            otherArgs["finalAddress"] = finalAddress
            otherArgs["finalPort"] = finalPort

            val ret = jsip.buildAllConfig(port, this@updateAllConfig, otherArgs)

            // result
            allConfig = JSONObject(ret)
        } catch (e: Exception) {
            Logs.e("$e")
        }

        jsi.unlock()
        it.resume(Unit)
        // destroy when config generated / all tests finished
    }
}

// must call it to update something like serverAddress
fun MatsuriBean.onSharedStorageSet() {
    serverAddress = sharedStorage.getStr("serverAddress") ?: "127.0.0.1"
    serverPort = sharedStorage.getStr("serverPort")?.toInt() ?: 1080
    name = sharedStorage.getStr("name")
}

fun MatsuriBean.needBypassRootUid(): Boolean {
    val p = MatsuriPluginManager.findProtocol(protocolId) ?: return false
    return p.protocolConfig.getBool("needBypassRootUid")
}

fun MatsuriBean.haveStandardLink(): Boolean {
    val p = MatsuriPluginManager.findProtocol(protocolId) ?: return false
    return p.protocolConfig.getBool("haveStandardLink")
}

fun MatsuriBean.canShare(): Boolean {
    val p = MatsuriPluginManager.findProtocol(protocolId) ?: return false
    return p.protocolConfig.getBool("canShare")
}