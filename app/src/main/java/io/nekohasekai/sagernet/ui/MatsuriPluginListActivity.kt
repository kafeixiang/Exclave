package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.SparseBooleanArray
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.util.set
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAppListBinding
import io.nekohasekai.sagernet.databinding.LayoutAppsItemBinding
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.applyGlassBlur
import io.nekohasekai.sagernet.ktx.crossFadeFrom
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.plugin.MatsuriJSInterface
import io.nekohasekai.sagernet.plugin.MatsuriPluginManager
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MatsuriPluginListActivity : ThemedActivity() {
    companion object {
        private const val SWITCH = "switch"
    }

    private class SelectedApp(
        private val pm: PackageManager,
        private val appInfo: ApplicationInfo,
        val packageName: String,
        val versionName: String,
    ) {
        val name: CharSequence = appInfo.loadLabel(pm) // cached for sorting
        val icon: Drawable get() = appInfo.loadIcon(pm)
        val uid get() = appInfo.uid
        val sys get() = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    private inner class AppViewHolder(val binding: LayoutAppsItemBinding) : RecyclerView.ViewHolder(
        binding.root,
    ), View.OnClickListener {
        private lateinit var item: SelectedApp

        init {
            binding.root.setOnClickListener(this)
        }

        fun bind(app: SelectedApp) {
            item = app
            binding.itemicon.setImageDrawable(app.icon)
            binding.title.text = app.name
            binding.desc.text = if (app.versionName.isNotBlank()) {
                "${app.packageName} (${app.versionName})"
            } else {
                "${app.packageName} (${app.uid})"
            }

            binding.button.isVisible = true
            binding.button.setImageDrawable(
                AppCompatResources.getDrawable(
                    this@MatsuriPluginListActivity,
                    R.drawable.ic_baseline_info_24,
                )
            )
            binding.button.setOnClickListener {
                runOnIoDispatcher {
                    try {
                        val jsi = MatsuriJSInterface(app.packageName)
                        jsi.init()
                        val about = jsi.getAbout()
                        MatsuriJSInterface.Default.destroyJsi(app.packageName)
                        onMainDispatcher {
                            val dialog = MaterialAlertDialogBuilder(this@MatsuriPluginListActivity)
                                .setTitle(app.name.toString())
                                .setMessage(
                                    "PackageName: ${app.packageName}\n" +
                                            "Version: ${app.versionName}\n" +
                                            "------------------------\n" + about,
                                )
                                .setPositiveButton(android.R.string.ok, null)
                                .create()
                            dialog.apply { applyGlassBlur() }.show()
                            dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
                        }
                    } catch (e: Exception) {
                        onMainDispatcher {
                            alert(e.localizedMessage ?: e.toString()).show()
                        }
                    }
                }
            }

            handlePayload(listOf(SWITCH))
        }

        fun handlePayload(payloads: List<String>) {
            if (payloads.contains(SWITCH)) {
                val selected = isSelectedApp(item)
                binding.itemcheck.isChecked = selected
                binding.button.isVisible = selected
            }
        }

        override fun onClick(v: View?) {
            val wasSelected = isSelectedApp(item)
            if (wasSelected) {
                selectedUids.delete(item.uid)
            } else {
                selectedUids[item.uid] = true
            }

            val nowSelected = isSelectedApp(item)
            DataStore.matsuriPlugins = apps.filter { isSelectedApp(it) }
                .joinToString("\n") { it.packageName }

            if (nowSelected) {
                runOnIoDispatcher {
                    try {
                        MatsuriPluginManager.installPlugin(item.packageName)
                    } catch (e: Exception) {
                        onMainDispatcher {
                            // Revert selection state on failure
                            selectedUids.delete(item.uid)
                            DataStore.matsuriPlugins = apps.filter { isSelectedApp(it) }
                                .joinToString("\n") { it.packageName }
                            appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
                            alert("Failed to install plugin ${item.packageName}:\n${e.localizedMessage ?: e.toString()}").show()
                        }
                    }
                }
            } else {
                MatsuriPluginManager.removeManagedPlugin(item.packageName)
            }

            appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
        }
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppViewHolder>(),
        Filterable,
        FastScrollRecyclerView.SectionedAdapter {
        var filteredApps = apps

        suspend fun reload() {
            val coroutineCtx = currentCoroutineContext()
            apps = getCachedApps().mapNotNull { (packageName, packageInfo) ->
                coroutineCtx.ensureActive()
                packageInfo.applicationInfo?.let { appInfo ->
                    SelectedApp(
                        packageManager,
                        appInfo,
                        packageName,
                        packageInfo.versionName ?: "",
                    )
                }
            }.sortedWith(compareBy({ !isSelectedApp(it) }, { it.name.toString() }))
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) =
            holder.bind(filteredApps[position])

        override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST") holder.handlePayload(payloads as List<String>)
                return
            }

            onBindViewHolder(holder, position)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder =
            AppViewHolder(LayoutAppsItemBinding.inflate(layoutInflater, parent, false))

        override fun getItemCount(): Int = filteredApps.size

        private val filterImpl = object : Filter() {
            override fun performFiltering(constraint: CharSequence) = FilterResults().apply {
                var filteredApps = if (constraint.isEmpty()) apps else apps.filter {
                    it.name.contains(constraint, ignoreCase = true) || it.packageName.contains(
                        constraint, ignoreCase = true,
                    ) || it.uid.toString().contains(constraint)
                }
                if (!sysApps) filteredApps = filteredApps.filter { !it.sys }
                count = filteredApps.size
                values = filteredApps
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                @Suppress("UNCHECKED_CAST")
                filteredApps = results.values as List<SelectedApp>
                notifyDataSetChanged()
            }
        }

        override fun getFilter(): Filter = filterImpl

        override fun getSectionName(position: Int): String {
            return filteredApps[position].name.firstOrNull()?.toString() ?: ""
        }

    }

    private val loading by lazy { findViewById<View>(R.id.loading) }

    private lateinit var binding: LayoutAppListBinding
    private val selectedUids = SparseBooleanArray()
    private var loader: Job? = null
    private var apps = emptyList<SelectedApp>()
    private val appsAdapter = AppsAdapter()

    private fun initSelectedUids(str: String = DataStore.matsuriPlugins) {
        selectedUids.clear()
        val installedMap = getCachedApps()
        for (line in str.lineSequence()) {
            val pkg = installedMap[line] ?: continue
            val uid = pkg.applicationInfo?.uid ?: continue
            selectedUids[uid] = true
        }
    }

    private fun isSelectedApp(app: SelectedApp) = selectedUids[app.uid]

    @UiThread
    private fun loadApps() {
        loader?.cancel()
        loader = lifecycleScope.launch {
            loading.crossFadeFrom(binding.list)
            val adapter = binding.list.adapter as AppsAdapter
            withContext(Dispatchers.IO) { adapter.reload() }
            val search = binding.appbarLayout.search
            adapter.filter.filter(search.text?.toString() ?: "")
            binding.list.crossFadeFrom(loading)
        }
    }

    fun getCachedApps(): MutableMap<String, PackageInfo> {
        PackageCache.awaitLoadSync()
        return PackageCache.installedPluginPackages.toMutableMap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) && (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.appbarLayout.appbar) { v, insets ->
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.list) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }

        setSupportActionBar(binding.appbarLayout.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.matsuri_plugins)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        initSelectedUids()
        binding.list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.list.itemAnimator = DefaultItemAnimator()
        binding.list.adapter = appsAdapter

        binding.appbarLayout.matsuriSearchLayout.isVisible = true

        binding.appbarLayout.search.addTextChangedListener {
            appsAdapter.filter.filter(it?.toString() ?: "")
        }

        binding.appbarLayout.showSystemApps.isChecked = sysApps
        binding.appbarLayout.showSystemApps.setOnCheckedChangeListener { _, isChecked ->
            sysApps = isChecked
            appsAdapter.filter.filter(binding.appbarLayout.search.text?.toString() ?: "")
        }

        loadApps()
    }

    private var sysApps = false

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.matsuri_plugin_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.uninstall_all -> {
                runOnDefaultDispatcher {
                    selectedUids.clear()
                    DataStore.matsuriPlugins = ""
                    apps = apps.sortedWith(compareBy({ !isSelectedApp(it) }, { it.name.toString() }))
                    MatsuriPluginManager.plugins.forEach {
                        MatsuriPluginManager.removeManagedPlugin(it)
                    }
                    onMainDispatcher {
                        appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
                    }
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun supportNavigateUpTo(upIntent: Intent) =
        super.supportNavigateUpTo(upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    override fun onKeyUp(keyCode: Int, event: KeyEvent?) = if (keyCode == KeyEvent.KEYCODE_MENU) {
        if (binding.appbarLayout.toolbar.isOverflowMenuShowing) binding.appbarLayout.toolbar.hideOverflowMenu() else binding.appbarLayout.toolbar.showOverflowMenu()
    } else super.onKeyUp(keyCode, event)

    override fun onDestroy() {
        loader?.cancel()
        super.onDestroy()
    }
}
