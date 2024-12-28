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

package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import cn.hutool.json.JSONObject
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.LayoutAssetItemBinding
import io.nekohasekai.sagernet.databinding.LayoutAssetsBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import libcore.Libcore
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class AssetsActivity : ThemedActivity() {

    lateinit var adapter: AssetAdapter
    lateinit var layout: LayoutAssetsBinding
    lateinit var undoManager: UndoSnackbarManager<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = LayoutAssetsBinding.inflate(layoutInflater)
        layout = binding
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = bars.top,
                left = bars.left,
                right = bars.right,
            )
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recycler_view)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(4),
                right = bars.right + dp2px(4),
                bottom = bars.bottom + dp2px(4),
            )
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.route_assets)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        binding.recyclerView.layoutManager = FixedLinearLayoutManager(binding.recyclerView)
        adapter = AssetAdapter()
        binding.recyclerView.adapter = adapter

        binding.refreshLayout.setOnRefreshListener {
            adapter.reloadAssets()
            binding.refreshLayout.isRefreshing = false
        }
        binding.refreshLayout.setColorSchemeColors(getColorAttr(R.attr.primaryOrTextPrimary))

        undoManager = UndoSnackbarManager(this, adapter)

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.START
        ) {

            override fun getSwipeDirs(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ): Int {
                val index = viewHolder.adapterPosition
                if (index < 2) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val index = viewHolder.adapterPosition
                adapter.remove(index)
                undoManager.remove(index to (viewHolder as AssetHolder).file)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

        }).attachToRecyclerView(binding.recyclerView)
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(layout.coordinator, text, Snackbar.LENGTH_LONG)
    }

    val internalFiles = arrayOf("geoip.dat", "geosite.dat")

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.import_asset_menu, menu)
        return true
    }

    val importFile = registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
        if (file != null) {
            val fileName = contentResolver.query(file, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
            }?.takeIf { it.isNotBlank() } ?: file.pathSegments.last()
                .substringAfterLast('/')
                .substringAfter(':')

            if (!fileName.endsWith(".dat") && fileName != "index.html" && fileName != "index.js" && fileName != "mozilla_included.pem") {
                runOnMainDispatcher {
                    alert(getString(R.string.route_not_asset, fileName)).show()
                }
                return@registerForActivityResult
            }

            runOnDefaultDispatcher {
                val outFile = File(app.externalAssets, fileName).apply {
                    parentFile?.mkdirs()
                }

                contentResolver.openInputStream(file)?.use(outFile.outputStream())

                File(outFile.parentFile, outFile.nameWithoutExtension + ".version.txt").apply {
                    if (isFile) delete()
                }

                adapter.reloadAssets()
            }

        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_import_file -> {
                startFilesForResult(importFile, "*/*")
                return true
            }
            R.id.action_import_url -> {
                startActivity(Intent(this, AssetEditActivity::class.java))
                adapter.reloadAssets()
                return true
            }
        }
        return false
    }

    inner class AssetAdapter : RecyclerView.Adapter<AssetHolder>(),
        UndoSnackbarManager.Interface<File> {

        val assets = ArrayList<File>()

        init {
            reloadAssets()
        }

        fun reloadAssets() {
            assets.clear()
            assets.add(File(app.externalAssets, "geoip.dat"))
            assets.add(File(app.externalAssets, "geosite.dat"))

            val managedAssets = SagerDatabase.assetDao.getAll().associateBy { it.name }
            managedAssets.forEach {
                assets.add(File(app.externalAssets, it.key))
            }

            val unmanagedAssets = app.externalAssets.listFiles()?.filter {
                it.isFile && it.name.endsWith(".dat") && it.name !in internalFiles && it !in assets
            }
            if (unmanagedAssets != null) assets.addAll(unmanagedAssets)

            layout.refreshLayout.post {
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetHolder {
            return AssetHolder(LayoutAssetItemBinding.inflate(layoutInflater, parent, false))
        }

        override fun onBindViewHolder(holder: AssetHolder, position: Int) {
            holder.bind(assets[position])
        }

        override fun getItemCount(): Int {
            return assets.size
        }

        fun remove(index: Int) {
            assets.removeAt(index)
            notifyItemRemoved(index)
        }

        override fun undo(actions: List<Pair<Int, File>>) {
            for ((index, item) in actions) {
                assets.add(index, item)
                notifyItemInserted(index)
            }
        }

        override fun commit(actions: List<Pair<Int, File>>) {
            val groups = actions.map { it.second }.toTypedArray()
            runOnDefaultDispatcher {
                groups.forEach {
                    it.deleteRecursively()
                    SagerDatabase.assetDao.delete(it.name)
                }
            }
        }

    }

    val updating = AtomicInteger()

    inner class AssetHolder(val binding: LayoutAssetItemBinding) : RecyclerView.ViewHolder(binding.root) {
        lateinit var file: File

        fun bind(file: File) {
            this.file = file

            binding.assetName.text = file.name
            val versionFile = File(file.parentFile, "${file.nameWithoutExtension}.version.txt")

            val localVersion = if (file.isFile) {
                if (versionFile.isFile) {
                    versionFile.readText().trim()
                } else {
                    DateFormat.getDateFormat(app).format(Date(file.lastModified()))
                }
            } else {
                "<unknown>"
            }

            binding.assetStatus.text = getString(R.string.route_asset_status, localVersion)

            val assetEntity = SagerDatabase.assetDao.get(file.name)
            binding.rulesUpdate.isInvisible = file.name !in internalFiles && assetEntity == null
            binding.rulesUpdate.setOnClickListener {
                updating.incrementAndGet()
                layout.refreshLayout.isEnabled = false
                binding.subscriptionUpdateProgress.isInvisible = false
                binding.rulesUpdate.isInvisible = true
                runOnDefaultDispatcher {
                    runCatching {
                        if (file.name in internalFiles) {
                            updateAsset(file, versionFile, localVersion)
                        } else {
                            updateCustomAsset(file, assetEntity!!.url)
                        }
                    }.onFailure {
                        onMainDispatcher {
                            alert(it.readableMessage).show()
                        }
                    }

                    onMainDispatcher {
                        binding.rulesUpdate.isInvisible = false
                        binding.subscriptionUpdateProgress.isInvisible = true
                        if (updating.decrementAndGet() == 0) {
                            layout.refreshLayout.isEnabled = true
                        }
                    }
                }
            }

            binding.edit.isVisible = file.name !in internalFiles && assetEntity != null
            binding.edit.setOnClickListener {
                startActivity(Intent(this@AssetsActivity, AssetEditActivity::class.java).apply {
                    putExtra(AssetEditActivity.EXTRA_ASSET_NAME, file.name)
                })
                adapter.reloadAssets()
            }

        }

    }

    suspend fun updateAsset(file: File, versionFile: File, localVersion: String) {
        val repo: String
        var fileName = file.name
        when (DataStore.rulesProvider) {
            0 -> {
                if (file.name == internalFiles[0]) {
                    repo = "v2fly/geoip"
                } else {
                    repo = "v2fly/domain-list-community"
                    fileName = "dlc.dat"
                }
            }
            1 -> repo = "Loyalsoldier/v2ray-rules-dat"
            2 -> repo = "Chocolate4U/Iran-v2ray-rules"
            else -> return updateGeoAsset(file, versionFile)
        }

        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            if (SagerNet.started && DataStore.startedProfile > 0) {
                useSocks5(DataStore.socksPort)
            }
        }

        try {
            var response = client.newRequest().apply {
                setURL("https://api.github.com/repos/$repo/releases/latest")
            }.execute()

            val release = JSONObject(response.contentString)
            val tagName = release.getStr("tag_name")

            if (tagName == localVersion) {
                onMainDispatcher {
                    snackbar(R.string.route_asset_no_update).show()
                }
                return
            }

            val releaseAssets = release.getJSONArray("assets").filterIsInstance<JSONObject>()
            val assetToDownload = releaseAssets.find { it.getStr("name") == fileName }
                ?: error("File $fileName not found in release ${release["url"]}")
            val browserDownloadUrl = assetToDownload.getStr("browser_download_url")

            response = client.newRequest().apply {
                setURL(browserDownloadUrl)
            }.execute()

            val cacheFile = File(file.parentFile, file.name + ".tmp")
            cacheFile.parentFile?.mkdirs()

            response.writeTo(cacheFile.canonicalPath)

            cacheFile.renameTo(file)

            versionFile.writeText(tagName)

            adapter.reloadAssets()

            onMainDispatcher {
                snackbar(R.string.route_asset_updated).show()
            }
        } finally {
            client.close()
        }
    }

    suspend fun updateGeoAsset(file: File, versionFile: File) {
        try {
            updateCustomAsset(file, if (file.name == internalFiles[0]) DataStore.rulesGeoipUrl else DataStore.rulesGeositeUrl)
        } finally {
            if (versionFile.isFile) {
                versionFile.delete()
            }
        }
    }

    suspend fun updateCustomAsset(file: File, url: String) {
        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            if (SagerNet.started && DataStore.startedProfile > 0) {
                useSocks5(DataStore.socksPort)
            }
        }
        try {
            val response = client.newRequest().apply {
                setURL(url)
            }.execute()
            val cacheFile = File(file.parentFile, file.name + ".tmp")
            cacheFile.parentFile?.mkdirs()
            response.writeTo(cacheFile.canonicalPath)
            cacheFile.renameTo(file)
            adapter.reloadAssets()
            onMainDispatcher {
                snackbar(R.string.route_asset_updated).show()
            }
        } finally {
            client.close()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onResume() {
        super.onResume()

        if (::adapter.isInitialized) {
            adapter.reloadAssets()
        }
    }


}