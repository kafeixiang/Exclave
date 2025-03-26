package io.nekohasekai.sagernet.plugin

import cn.hutool.json.JSONObject
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.PackageCache
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipFile

object MatsuriPluginManager {
    const val managerVersion = 2

    val plugins get() = DataStore.matsuriPlugins.split("\n").filter { it.isNotEmpty() }

    // plgID to plgConfig object
    fun getManagedPlugins(): Map<String, JSONObject> {
        val ret = mutableMapOf<String, JSONObject>()
        plugins.forEach {
            tryGetPlgConfig(it)?.apply {
                ret[it] = this
            }
        }
        return ret
    }

    class Protocol(
        val protocolId: String, val plgId: String, val protocolConfig: JSONObject
    )

    fun getProtocols(): List<Protocol> {
        val ret = mutableListOf<Protocol>()
        getManagedPlugins().forEach { (t, u) ->
            u.getJSONArray("protocols")?.forEach { any ->
                if (any is JSONObject) {
                    val name = any.getStr("protocolId")
                    ret.add(Protocol(name, t, any))
                }
            }
        }
        return ret
    }

    fun findProtocol(protocolId: String): Protocol? {
        getManagedPlugins().forEach { (t, u) ->
            u.getJSONArray("protocols")?.forEach { any ->
                if (any is JSONObject) {
                    if (protocolId == any.getStr("protocolId")) {
                        return Protocol(protocolId, t, any)
                    }
                }
            }
        }
        return null
    }

    fun removeManagedPlugin(plgId: String) {
        DataStore.configurationStore.remove(plgId)
        val dir = File(SagerNet.application.filesDir.absolutePath + "/plugins/" + plgId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    fun extractPlugin(plgId: String, install: Boolean) {
        val app = PackageCache.installedApps[plgId] ?: return
        val apk = File(app.publicSourceDir)
        if (!apk.exists()) {
            return
        }
        if (!install && !plugins.contains(plgId)) {
            return
        }

        val zipFile = ZipFile(apk)
        val unzipDir = File(SagerNet.application.filesDir.absolutePath + "/plugins/" + plgId)
        unzipDir.mkdirs()
        for (entry in zipFile.entries()) {
            if (entry.name.startsWith("assets/")) {
                val relativePath = entry.name.removePrefix("assets/")
                val outFile = File(unzipDir, relativePath)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                    continue
                }

                if (outFile.isDirectory) {
                    outFile.delete()
                } else if (outFile.exists()) {
                    val checksum = CRC32()
                    checksum.update(outFile.readBytes())
                    if (checksum.value == entry.crc) {
                        continue
                    }
                }

                val input = zipFile.getInputStream(entry)
                outFile.outputStream().use {
                    input.copyTo(it)
                }
            }
        }
        zipFile.close() // closeQuietly
    }

    suspend fun installPlugin(plgId: String) {
        extractPlugin(plgId, true)
        MatsuriJSInterface.Default.destroyJsi(plgId)
        MatsuriJSInterface.Default.requireJsi(plgId).init()
        MatsuriJSInterface.Default.destroyJsi(plgId)
    }

    const val PLUGIN_APP_VERSION_NAME = "_v_vn"

    // Return null if not managed
    fun tryGetPlgConfig(plgId: String): JSONObject? {
        return try {
            JSONObject(DataStore.configurationStore.getString(plgId))
        } catch (e: Exception) {
            null
        }
    }

    fun updatePlgConfig(plgId: String, plgConfig: JSONObject) {
            PackageCache.installedPluginPackages[plgId]?.apply {
            plgConfig[PLUGIN_APP_VERSION_NAME] = versionName
        }
        DataStore.configurationStore.putString(plgId, plgConfig.toString())
    }

    fun htmlPath(plgId: String): String {
        val htmlFile = File(SagerNet.application.filesDir.absolutePath + "/plugins/" + plgId)
        return htmlFile.absolutePath
    }

    class PluginInternalException(val protocolId: String) : Exception(),
        BaseService.ExpectedException {
        override fun getLocalizedMessage() = "Matsuri plugin internal error: $protocolId"
    }

}