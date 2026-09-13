/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.*
import com.jakewharton.processphoenix.ProcessPhoenix
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.databinding.LayoutBackupBinding
import io.nekohasekai.sagernet.databinding.LayoutImportBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressBinding
import io.nekohasekai.sagernet.ktx.*
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64

class BackupFragment : NamedFragment(R.layout.layout_backup) {

    private var isWebDAVBackup = false
    private var isBackupInProgress = false
    private var isRestoreInProgress = false
    private var restoreJob: kotlinx.coroutines.Job? = null

    override fun onDestroyView() {
        super.onDestroyView()
        if (isRestoreInProgress) {
            restoreJob?.cancel()
            restoreJob = null
            isRestoreInProgress = false
        }
    }

    var content = ""
    private val exportSettings = registerForActivityResult(ActivityResultContracts.CreateDocument()) { data ->
        if (data != null) {
            runOnDefaultDispatcher {
                try {
                    requireActivity().contentResolver.openOutputStream(
                        data
                    )!!.bufferedWriter().use {
                        it.write(content)
                    }
                    onMainDispatcher {
                        (requireActivity() as MainActivity).snackbar(R.string.action_export_msg).show()
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                    onMainDispatcher {
                        (requireActivity() as MainActivity).snackbar(e.readableMessage).show()
                    }
                }

            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutBackupBinding.bind(view)
        binding.card.applyGlassBlur()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(16),
                right = bars.right + dp2px(16),
                bottom = bars.bottom + dp2px(64),
            )
            insets
        }

        binding.resetSettings.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(android.R.string.dialog_alert_title)
                .setMessage(R.string.reset_settings_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    DataStore.configurationStore.reset()
                    ProcessPhoenix.triggerRebirth(requireContext())
                }
                .show()
        }

        binding.actionExport.setOnClickListener {
            runOnDefaultDispatcher {
                content = doBackup(
                    binding.backupConfigurations.isChecked,
                    binding.backupRules.isChecked,
                    binding.backupSettings.isChecked
                )
                onMainDispatcher {
                    startFilesForResult(
                        exportSettings, "exclave_backup_${System.currentTimeMillis()}.json"
                    )
                }
            }
        }

        binding.actionShare.setOnClickListener {
            runOnDefaultDispatcher {
                content = doBackup(
                    binding.backupConfigurations.isChecked,
                    binding.backupRules.isChecked,
                    binding.backupSettings.isChecked
                )
                app.cacheDir.mkdirs()
                val cacheFile = File(
                    app.cacheDir, "exclave_backup_${System.currentTimeMillis()}.json"
                )
                cacheFile.writeText(content)
                onMainDispatcher {
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("application/json")
                                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .putExtra(
                                    Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                                        app, BuildConfig.APPLICATION_ID + ".cache", cacheFile
                                    )
                                ), app.getString(androidx.appcompat.R.string.abc_shareactionprovider_share_with)
                        )
                    )
                }

            }
        }

        binding.actionImportFile.setOnClickListener {
            startFilesForResult(importFile, "*/*")
        }

        binding.webdavSettings.setOnClickListener {
            startActivity(Intent(requireContext(), WebDAVSettingsActivity::class.java))
        }

        binding.backupToWebdav.setOnClickListener {
            if (DataStore.webdavServer.isBlank()) {
                (requireActivity() as MainActivity).snackbar(R.string.webdav_server_empty).show()
                return@setOnClickListener
            }
            backupToWebDAV()
        }

        binding.restoreFromWebdav.setOnClickListener {
            if (DataStore.webdavServer.isBlank()) {
                (requireActivity() as MainActivity).snackbar(R.string.webdav_server_empty).show()
                return@setOnClickListener
            }
            restoreFromWebDAV()
        }
    }

    private fun backupToWebDAV() {
        if (isBackupInProgress) {
            (requireActivity() as MainActivity).snackbar(R.string.backup_in_progress).show()
            return
        }
        isBackupInProgress = true
        val activity = requireActivity() as MainActivity
        runOnDefaultDispatcher {
            try {
                isWebDAVBackup = true
                val backupData = doBackupBytes(true, true, true)
                isWebDAVBackup = false

                val client = OkHttpClient()
                val baseUrl = DataStore.webdavServer.trimEnd('/')
                val path = DataStore.webdavPath.trim('/').takeIf { it.isNotEmpty() } ?: "Exclave"
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val version = BuildConfig.VERSION_NAME
                val fileName = "exclave_backup_${version}_$timestamp.zip"

                if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                    throw Exception("Invalid server URL: must start with http:// or https://")
                }

                val baseHttpUrl = baseUrl.toHttpUrlOrNull()
                    ?: throw Exception("Invalid server URL: $baseUrl")

                val dirUrl = baseHttpUrl.newBuilder().apply {
                    path.split('/').filter { it.isNotEmpty() }.forEach { segment ->
                        addPathSegment(segment)
                    }
                }.build()

                val fileUrl = dirUrl.newBuilder()
                    .addPathSegment(fileName)
                    .build()

                val propfindRequest = Request.Builder()
                    .url(dirUrl)
                    .method("PROPFIND", null)
                    .header("Authorization", Credentials.basic(
                        DataStore.webdavUsername,
                        DataStore.webdavPassword
                    ))
                    .header("Depth", "0")
                    .build()

                var needCreateDir = false
                client.newCall(propfindRequest).execute().use { response ->
                    when (response.code) {
                        404 -> needCreateDir = true
                        207 -> needCreateDir = false
                        401 -> throw Exception("Authentication failed")
                        else -> {
                            if (!response.isSuccessful) {
                                throw Exception("Failed to check directory (${response.code}): ${response.message}")
                            }
                        }
                    }
                }

                if (needCreateDir) {
                    val mkcolRequest = Request.Builder()
                        .url(dirUrl)
                        .method("MKCOL", null)
                        .header("Authorization", Credentials.basic(
                            DataStore.webdavUsername,
                            DataStore.webdavPassword
                        ))
                        .build()

                    client.newCall(mkcolRequest).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("Failed to create directory (${response.code}): ${response.message}")
                        }
                    }
                }

                val putRequest = Request.Builder()
                    .url(fileUrl)
                    .put(backupData.toRequestBody("application/zip".toMediaType()))
                    .header("Authorization", Credentials.basic(
                        DataStore.webdavUsername,
                        DataStore.webdavPassword
                    ))
                    .build()

                client.newCall(putRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Upload failed (${response.code}): ${response.message}")
                    }
                }

                onMainDispatcher {
                    activity.snackbar(R.string.webdav_backup_success).show()
                }
            } catch (e: Exception) {
                isWebDAVBackup = false
                Logs.w(e)
                onMainDispatcher {
                    activity.snackbar(getString(R.string.webdav_backup_failed, e.message ?: "")).show()
                }
            } finally {
                isBackupInProgress = false
            }
        }
    }

    private fun restoreFromWebDAV() {
        if (isRestoreInProgress) {
            (requireActivity() as MainActivity).snackbar(R.string.restore_in_progress).show()
            return
        }
        isRestoreInProgress = true
        val activity = requireActivity() as MainActivity
        restoreJob = runOnDefaultDispatcher {
            try {
                val client = OkHttpClient()
                val baseUrl = DataStore.webdavServer.trimEnd('/')
                val path = DataStore.webdavPath.trim('/').takeIf { it.isNotEmpty() } ?: "Exclave"

                if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                    throw Exception("Invalid server URL: must start with http:// or https://")
                }

                val baseHttpUrl = baseUrl.toHttpUrlOrNull()
                    ?: throw Exception("Invalid server URL: $baseUrl")

                val dirUrl = baseHttpUrl.newBuilder().apply {
                    path.split('/').filter { it.isNotEmpty() }.forEach { segment ->
                        addPathSegment(segment)
                    }
                }.build()

                val propfindRequest = Request.Builder()
                    .url(dirUrl)
                    .method("PROPFIND", null)
                    .header("Authorization", Credentials.basic(
                        DataStore.webdavUsername,
                        DataStore.webdavPassword
                    ))
                    .header("Depth", "1")
                    .build()

                val latestBackup = client.newCall(propfindRequest).execute().use { response ->
                    if (!response.isSuccessful && response.code != 207) {
                        throw Exception("Failed to list directory: ${response.message}")
                    }

                    val responseBody = response.body?.string() ?: throw Exception("Empty response")
                    val patterns = listOf(
                        """<D:href>[^<]*?exclave_backup_[^<]*?\d{8}_\d{6}\.(json|zip)</D:href>""".toRegex(),
                        """<d:href>[^<]*?exclave_backup_[^<]*?\d{8}_\d{6}\.(json|zip)</d:href>""".toRegex(),
                        """<href>[^<]*?exclave_backup_[^<]*?\d{8}_\d{6}\.(json|zip)</href>""".toRegex()
                    )

                    val backupFiles = mutableListOf<String>()
                    for (pattern in patterns) {
                        pattern.findAll(responseBody).forEach { match ->
                            val href = match.value
                            """exclave_backup_[^<]*?\d{8}_\d{6}\.(json|zip)""".toRegex()
                                .find(href)?.value?.let { backupFiles.add(it) }
                        }
                        if (backupFiles.isNotEmpty()) break
                    }

                    backupFiles.maxByOrNull { fileName ->
                        """(\d{8}_\d{6})""".toRegex().find(fileName)?.value ?: ""
                    } ?: throw Exception("No backup found")
                }

                val fileUrl = dirUrl.newBuilder().addPathSegment(latestBackup).build()
                val getRequest = Request.Builder()
                    .url(fileUrl)
                    .get()
                    .header("Authorization", Credentials.basic(
                        DataStore.webdavUsername,
                        DataStore.webdavPassword
                    ))
                    .build()

                val content = client.newCall(getRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Download failed (${response.code}): ${response.message}")
                    }
                    response.body?.bytes() ?: throw Exception("Empty backup file")
                }

                val backupContent = if (latestBackup.endsWith(".zip")) {
                    ZipInputStream(content.inputStream()).use { zis ->
                        zis.nextEntry?.let { entry ->
                            if (entry.name.endsWith(".json")) {
                                zis.readBytes().toString(Charsets.UTF_8)
                            } else {
                                throw Exception("Invalid backup file format")
                            }
                        } ?: throw Exception("Invalid backup file format")
                    }
                } else {
                    content.toString(Charsets.UTF_8)
                }

                val json = parseJson(backupContent).asJsonObject
                onMainDispatcher {
                    if (!isAdded) return@onMainDispatcher

                    val import = LayoutImportBinding.inflate(layoutInflater)
                    if (!json.contains("profiles")) import.backupConfigurations.isVisible = false
                    if (!json.contains("rules")) import.backupRules.isVisible = false
                    if (!json.contains("settings")) import.backupSettings.isVisible = false

                    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.backup_import)
                        .setView(import.root)
                        .setPositiveButton(R.string.backup_import) { _, _ ->
                            SagerNet.stopService()
                            val binding = LayoutProgressBinding.inflate(layoutInflater)
                            binding.content.text = getString(R.string.backup_importing)
                            val dialog = AlertDialog.Builder(requireContext())
                                .setView(binding.root)
                                .setCancelable(false)
                                .show()
                            runOnDefaultDispatcher {
                                runCatching {
                                    if (!isAdded) return@runOnDefaultDispatcher
                                    finishImport(
                                        json,
                                        import.backupConfigurations.isChecked,
                                        import.backupRules.isChecked,
                                        import.backupSettings.isChecked
                                    )
                                    ProcessPhoenix.triggerRebirth(
                                        activity, Intent(activity, MainActivity::class.java)
                                    )
                                }.onFailure {
                                    Logs.w(it)
                                    onMainDispatcher {
                                        activity.snackbar(it.readableMessage).show()
                                    }
                                }
                                onMainDispatcher { dialog.dismiss() }
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    activity.snackbar(e.readableMessage).show()
                }
            } finally {
                isRestoreInProgress = false
            }
        }
    }

    fun Parcelable.toBase64Str(): String {
        val parcel = Parcel.obtain()
        writeToParcel(parcel, 0)
        try {
            return Base64.encode(parcel.marshall())
        } finally {
            parcel.recycle()
        }
    }

    private fun doBackupBytes(profile: Boolean, rule: Boolean, setting: Boolean): ByteArray {
        val jsonContent = doBackup(profile, rule, setting)
        return ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.setLevel(Deflater.BEST_COMPRESSION)
                val entry = ZipEntry("exclave_backup.json").apply {
                    method = ZipEntry.DEFLATED
                }
                zos.putNextEntry(entry)
                zos.write(jsonContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                zos.finish()
            }
            bos.toByteArray()
        }
    }

    fun doBackup(profile: Boolean, rule: Boolean, setting: Boolean): String {
        val out = JsonObject()
        out.addProperty("version", 1)
        if (profile) {
            out.add("profiles", JsonArray().apply {
                SagerDatabase.proxyDao.getAll().forEach {
                    add(it.toBase64Str())
                }
            })

            out.add("groups", JsonArray().apply {
                SagerDatabase.groupDao.allGroups().forEach {
                    add(it.toBase64Str())
                }
            })
        }
        if (rule) {
            out.add("rules", JsonArray().apply {
                SagerDatabase.rulesDao.allRules().forEach {
                    add(it.toBase64Str())
                }
            })
            out.add("assets", JsonArray().apply {
                SagerDatabase.assetDao.getAll().forEach {
                    add(it.toBase64Str())
                }
            })
        }
        if (setting) {
            out.add("settings", JsonArray().apply {
                PublicDatabase.kvPairDao.all().forEach {
                    add(it.toBase64Str())
                }
            })
        }
        return GsonBuilder().setPrettyPrinting().create().toJson(out)
    }

    val importFile = registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
        if (file != null) {
            runOnDefaultDispatcher {
                startImport(file)
            }
        }
    }

    suspend fun startImport(file: Uri) {
        val activity = requireActivity() as MainActivity
        val fileName = requireContext().contentResolver.query(file, null, null, null, null)
            ?.use { cursor ->
                cursor.moveToFirst()
                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
            }
            ?.takeIf { it.isNotBlank() } ?: file.pathSegments.last()
            .substringAfterLast('/')
            .substringAfter(':')

        if (!fileName.endsWith(".json") && !fileName.endsWith(".zip")) {
            onMainDispatcher {
                activity.snackbar(getString(R.string.backup_not_file, fileName)).show()
            }
            return
        }

        try {
            val content = requireContext().contentResolver.openInputStream(file)!!.use { input ->
                if (fileName.endsWith(".zip")) {
                    ZipInputStream(BufferedInputStream(input)).use { zis ->
                        zis.nextEntry?.let { entry ->
                            if (entry.name.endsWith(".json")) {
                                zis.readBytes().toString(Charsets.UTF_8)
                            } else {
                                throw Exception("Invalid backup file format")
                            }
                        } ?: throw Exception("Invalid backup file format")
                    }
                } else {
                    input.readBytes().toString(Charsets.UTF_8)
                }
            }

            val json = parseJson(content).asJsonObject
            onMainDispatcher {
                val import = LayoutImportBinding.inflate(layoutInflater)
                if (!json.contains("profiles")) {
                    import.backupConfigurations.isVisible = false
                }
                if (!json.contains("rules")) {
                    import.backupRules.isVisible = false
                }
                if (!json.contains("settings")) {
                    import.backupSettings.isVisible = false
                }
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.backup_import)
                    .setView(import.root)
                    .setPositiveButton(R.string.backup_import) { _, _ ->
                        SagerNet.stopService()

                        val binding = LayoutProgressBinding.inflate(layoutInflater)
                        binding.content.text = getString(R.string.backup_importing)
                        val dialog = AlertDialog.Builder(requireContext())
                            .setView(binding.root)
                            .setCancelable(false)
                            .show()
                        runOnDefaultDispatcher {
                            runCatching {
                                finishImport(
                                    json,
                                    import.backupConfigurations.isChecked,
                                    import.backupRules.isChecked,
                                    import.backupSettings.isChecked
                                )
                                ProcessPhoenix.triggerRebirth(
                                    activity, Intent(activity, MainActivity::class.java)
                                )
                            }.onFailure {
                                Logs.w(it)
                                onMainDispatcher {
                                    activity.snackbar(it.readableMessage).show()
                                }
                            }

                            onMainDispatcher {
                                dialog.dismiss()
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        } catch (e: Exception) {
            Logs.w(e)
            onMainDispatcher {
                activity.snackbar(e.readableMessage).show()
            }
        }
    }

    fun finishImport(
        content: JsonObject, profile: Boolean, rule: Boolean, setting: Boolean
    ) {
        if (profile && content.contains("profiles")) {
            val profiles = mutableListOf<ProxyEntity>()
            content.getAsJsonArray("profiles")?.forEach { element: JsonElement ->
                val data = Base64.decode(element.asString)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                profiles.add(ProxyEntity.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            SagerDatabase.proxyDao.reset()
            SagerDatabase.proxyDao.insert(profiles)

            val groups = mutableListOf<ProxyGroup>()
            content.getAsJsonArray("groups")?.forEach { element: JsonElement ->
                val data = Base64.decode(element.asString)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                groups.add(ProxyGroup.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            SagerDatabase.groupDao.reset()
            SagerDatabase.groupDao.insert(groups)
        }
        if (rule && content.contains("rules")) {
            val rules = mutableListOf<RuleEntity>()
            content.getAsJsonArray("rules")?.forEach { element: JsonElement ->
                val data = Base64.decode(element.asString)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                rules.add(ParcelizeBridge.createRule(parcel))
                parcel.recycle()
            }
            SagerDatabase.rulesDao.reset()
            SagerDatabase.rulesDao.insert(rules)

            val assets = mutableListOf<AssetEntity>()
            content.getAsJsonArray("assets")?.forEach { element: JsonElement ->
                val data = Base64.decode(element.asString)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                assets.add(ParcelizeBridge.createAsset(parcel))
                parcel.recycle()
            }
            SagerDatabase.assetDao.reset()
            SagerDatabase.assetDao.insert(assets)
        }
        if (setting && content.contains("settings")) {
            val settings = mutableListOf<KeyValuePair>()
            content.getAsJsonArray("settings")?.forEach { element: JsonElement ->
                val data = Base64.decode(element.asString)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                settings.add(KeyValuePair.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            PublicDatabase.kvPairDao.reset()
            PublicDatabase.kvPairDao.insert(settings)
        }
    }

}
