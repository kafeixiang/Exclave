package io.nekohasekai.sagernet.ui

import io.nekohasekai.sagernet.aidl.AppStats
import kotlin.time.Duration.Companion.seconds

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.databinding.*
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.widget.QRCodeDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import kotlin.system.measureTimeMillis

private const val MENU_ID_EDIT = 1001
private const val MENU_ID_ADD = 1002
private const val MENU_ID_DONE = 1003

data class ServiceTarget(
    val id: String,
    val nameResId: Int,
    val defaultName: String,
    val iconResId: Int,
    val testUrl: String
)

class DashboardFragment : Fragment(R.layout.layout_dashboard) {

    companion object {
        val SERVICE_TARGETS = listOf(
            ServiceTarget("google", R.string.dashboard_google, "Google", R.drawable.ic_service_google, "https://www.google.com/generate_204"),
            ServiceTarget("youtube", R.string.dashboard_youtube, "YouTube", R.drawable.ic_service_youtube, "https://www.youtube.com"),
            ServiceTarget("github", R.string.dashboard_github, "GitHub", R.drawable.ic_service_github, "https://github.com"),
            ServiceTarget("chatgpt", R.string.dashboard_chatgpt, "ChatGPT", R.drawable.ic_service_openai, "https://chatgpt.com"),
            ServiceTarget("claude", R.string.dashboard_claude, "Claude", R.drawable.ic_service_claude, "https://claude.ai"),
            ServiceTarget("gemini", R.string.dashboard_gemini, "Gemini", R.drawable.ic_service_gemini, "https://gemini.google.com"),
            ServiceTarget("netflix", R.string.dashboard_netflix, "Netflix", R.drawable.ic_service_netflix, "https://www.netflix.com"),
            ServiceTarget("disneyplus", R.string.dashboard_disneyplus, "Disney+", R.drawable.ic_service_disneyplus, "https://www.disneyplus.com"),
            ServiceTarget("primevideo", R.string.dashboard_prime_video, "Prime Video", R.drawable.ic_service_primevideo, "https://www.primevideo.com"),
            ServiceTarget("spotify", R.string.dashboard_spotify, "Spotify", R.drawable.ic_service_spotify, "https://www.spotify.com"),
            ServiceTarget("tiktok", R.string.dashboard_tiktok, "TikTok", R.drawable.ic_service_tiktok, "https://www.tiktok.com"),
            ServiceTarget("bilibili", R.string.dashboard_bilibili, "Bilibili", R.drawable.ic_service_bilibili, "https://www.bilibili.com")
        )
    }

    enum class ItemType(
        val id: String,
        val titleResId: Int,
        val descResId: Int,
        val iconResId: Int,
        val defaultSpan: Int = 2
    ) {
        STATUS("status", R.string.dashboard_status, R.string.dashboard_status, R.drawable.ic_service_idle, 2),
        SPEED("speed", R.string.dashboard_speed, R.string.dashboard_speed, R.drawable.ic_baseline_speed_24, 2),
        TRAFFIC("traffic", R.string.dashboard_traffic, R.string.dashboard_traffic, R.drawable.ic_device_data_usage, 1),
        GEOIP("geoip", R.string.dashboard_geoip, R.string.dashboard_geoip, R.drawable.baseline_public_24, 1),
        LATENCY("latency", R.string.dashboard_latency, R.string.dashboard_latency_desc, R.drawable.ic_baseline_bolt_24, 2),
        QUICK_TOOLS("quick_tools", R.string.dashboard_quick_tools, R.string.dashboard_quick_tools, R.drawable.baseline_widgets_24, 2),
        PROFILES("profiles", R.string.dashboard_card_profiles, R.string.dashboard_card_profiles_desc, R.drawable.ic_baseline_view_list_24, 1),
        CONNECTIONS("connections", R.string.dashboard_card_connections, R.string.dashboard_card_connections_desc, R.drawable.ic_baseline_compare_arrows_24, 1),
        RUN_TIME("run_time", R.string.dashboard_card_runtime, R.string.dashboard_card_runtime_desc, R.drawable.ic_baseline_timelapse_24, 1),
        DNS_QUERIES("dns_queries", R.string.dashboard_card_dns, R.string.dashboard_card_dns_desc, R.drawable.ic_action_dns, 1),
        MEMORY_INFO("memory_info", R.string.dashboard_card_memory, R.string.dashboard_card_memory_desc, R.drawable.baseline_developer_board_24, 1)
    }

    private var recyclerView: RecyclerView? = null
    private lateinit var adapter: DashboardAdapter
    var isEditMode = false
        private set

    // 缓存状态数据
    private var lastState: BaseService.State = BaseService.State.Stopped
    private var lastProfileName: String? = null
    private var lastTrafficStats: TrafficStats? = null
    private var geoIpInfo = GeoIpData()
    private var latencyMap = mutableMapOf<String, Int>()
    private var serviceConnectedTime = 0L
    private var isGeoIpHidden = false
    private val speedHistory = mutableListOf<Long>()
    private var lastAppStatsList: List<AppStats> = emptyList()
    private var activeConnectionsSheet: ConnectionsBottomSheet? = null
    private var activeSpeedStatsSheet: SpeedStatsBottomSheet? = null

    // 编辑浮动 Dock 引用
    private var editModeFloatingBar: View? = null
    private var btnAddCard: View? = null
    private var btnDoneEdit: View? = null

    data class GeoIpData(var ip: String = "", var location: String = "", var isp: String = "", var asn: String = "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.dashboard_recycler)

        editModeFloatingBar = view.findViewById(R.id.edit_mode_floating_bar)
        btnAddCard = view.findViewById(R.id.btn_add_card)
        btnDoneEdit = view.findViewById(R.id.btn_done_edit)

        btnAddCard?.setOnClickListener { openAddCardSheet() }
        btnDoneEdit?.setOnClickListener { toggleEditMode(false) }

        adapter = DashboardAdapter()

        val gridLayoutManager = GridLayoutManager(context, 2)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                if (position !in adapter.items.indices) return 2
                val itemType = adapter.items[position]
                return getCardSpan(itemType.id, itemType.defaultSpan)
            }
        }

        recyclerView?.layoutManager = gridLayoutManager
        recyclerView?.applyGlassBlur()
        recyclerView?.adapter = adapter

        setupTouchHelper()

        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            lastState = mainActivity.state
            if (lastState == BaseService.State.Connected) {
                if (serviceConnectedTime == 0L) serviceConnectedTime = SystemClock.elapsedRealtime()
                fetchGeoIP()
                testLatency()
            }
        }

        ProfileManager.addListener(object : ProfileManager.Listener {
            override suspend fun onAdd(profile: ProxyEntity) {}
            override suspend fun onUpdated(profileId: Long, trafficStats: TrafficStats) {}
            override suspend fun onUpdated(profile: ProxyEntity) {
                runOnMainDispatcher {
                    if (isAdded) {
                        adapter.updateItem(ItemType.TRAFFIC)
                        adapter.updateItem(ItemType.STATUS)
                        adapter.updateItem(ItemType.PROFILES)
                    }
                }
            }
            override suspend fun onRemoved(groupId: Long, profileId: Long) {}
        })

        startUptimeTimer()
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        if (isEditMode) {
            val addMenuItem = menu.add(Menu.NONE, MENU_ID_ADD, Menu.NONE, R.string.dashboard_add_card)
            addMenuItem.setIcon(R.drawable.ic_baseline_add_road_24)
            addMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

            val doneMenuItem = menu.add(Menu.NONE, MENU_ID_DONE, Menu.NONE, R.string.dashboard_edit_done)
            doneMenuItem.setIcon(R.drawable.ic_action_done)
            doneMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        } else {
            val editMenuItem = menu.add(Menu.NONE, MENU_ID_EDIT, Menu.NONE, R.string.dashboard_edit)
            editMenuItem.setIcon(R.drawable.ic_image_edit)
            editMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        @Suppress("DEPRECATION")
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_ID_EDIT -> {
                toggleEditMode(true)
                true
            }
            MENU_ID_DONE -> {
                toggleEditMode(false)
                true
            }
            MENU_ID_ADD -> {
                openAddCardSheet()
                true
            }
            else -> @Suppress("DEPRECATION") super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun toggleEditMode(enable: Boolean) {
        isEditMode = enable
        editModeFloatingBar?.isVisible = enable

        activity?.invalidateOptionsMenu()
        adapter.notifyDataSetChanged()
    }

    fun openAddCardSheet() {
        val hiddenCards = getHiddenCards()
        val sheet = AddDashboardCardBottomSheet(hiddenCards) { cardType ->
            addCard(cardType)
        }
        sheet.show(parentFragmentManager, "AddDashboardCardSheet")
    }

    private fun parseCardSpans(): MutableMap<String, Int> {
        val map = mutableMapOf<String, Int>()
        val raw = DataStore.dashboardSizes
        if (raw.isNotEmpty()) {
            raw.split(",").forEach { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val span = parts[1].toIntOrNull()
                    if (span != null) map[parts[0]] = span
                }
            }
        }
        return map
    }

    private fun saveCardSpans(map: Map<String, Int>) {
        DataStore.dashboardSizes = map.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    fun getCardSpan(id: String, defaultSpan: Int): Int {
        val map = parseCardSpans()
        return map[id] ?: defaultSpan
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setCardSpan(id: String, span: Int) {
        val map = parseCardSpans()
        map[id] = span
        saveCardSpans(map)
        recyclerView?.layoutManager?.requestLayout()
        adapter.notifyDataSetChanged()
    }

    fun getHiddenCards(): List<ItemType> {
        val activeIds = adapter.items.map { it.id }.toSet()
        return ItemType.entries.filter { it.id !in activeIds }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun addCard(type: ItemType) {
        if (type !in adapter.items) {
            adapter.items.add(type)
            saveDashboardOrder()
            adapter.notifyDataSetChanged()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun removeCard(type: ItemType) {
        val index = adapter.items.indexOf(type)
        if (index != -1) {
            adapter.items.removeAt(index)
            saveDashboardOrder()
            adapter.notifyDataSetChanged()
        }
    }

    private fun saveDashboardOrder() {
        DataStore.dashboardOrder = adapter.items.joinToString(",") { it.id }
    }

    private fun setupTouchHelper() {
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                Collections.swap(adapter.items, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                saveDashboardOrder()
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
    }

    private fun startUptimeTimer() {
        lifecycleScope.launch(Dispatchers.Main) {
            while (isAdded) {
                if (lastState == BaseService.State.Connected) {
                    adapter.updateItem(ItemType.RUN_TIME)
                }
                adapter.updateItem(ItemType.MEMORY_INFO)
                delay(2.seconds)
            }
        }
    }

    private var isFetchingGeoIP = false

    fun fetchGeoIP() {
        if (isFetchingGeoIP) return
        isFetchingGeoIP = true

        geoIpInfo.ip = "获取中..."
        geoIpInfo.location = "正在精准定位外网 IP 与地理位置..."
        geoIpInfo.isp = ""
        geoIpInfo.asn = ""
        adapter.updateItem(ItemType.GEOIP)

        lifecycleScope.launch(Dispatchers.IO) {
            var success = false

            // 尝试 1: api.ip.sb
            try {
                val url = URL("https://api.ip.sb/geoip")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val ip = json.optString("ip", "")
                    if (ip.isNotEmpty()) {
                        geoIpInfo.ip = ip
                        val country = json.optString("country", "")
                        val city = json.optString("city", "")
                        geoIpInfo.location = if (city.isNotEmpty()) "$country, $city" else country
                        geoIpInfo.isp = json.optString("isp", "")
                        val asn = json.optInt("asn", 0)
                        geoIpInfo.asn = if (asn != 0) "AS$asn" else ""
                        success = true
                    }
                }
            } catch (_: Exception) {}

            // 尝试 2: ip-api.com (双路线备用)
            if (!success) {
                try {
                    val url = URL("http://ip-api.com/json")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 4000
                    connection.readTimeout = 4000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().readText()
                        val json = JSONObject(response)
                        val ip = json.optString("query", "")
                        if (ip.isNotEmpty()) {
                            geoIpInfo.ip = ip
                            val country = json.optString("country", "")
                            val city = json.optString("city", "")
                            geoIpInfo.location = if (city.isNotEmpty()) "$country, $city" else country
                            geoIpInfo.isp = json.optString("isp", "")
                            geoIpInfo.asn = json.optString("as", "")
                            success = true
                        }
                    }
                } catch (_: Exception) {}
            }

            if (!success) {
                geoIpInfo.ip = getString(R.string.unavailable)
                geoIpInfo.location = getString(R.string.unavailable)
                geoIpInfo.isp = ""
                geoIpInfo.asn = ""
            }

            isFetchingGeoIP = false
            withContext(Dispatchers.Main) {
                if (isAdded) adapter.updateItem(ItemType.GEOIP)
            }
        }
    }

    private fun testLatency(singleTarget: ServiceTarget? = null) {
        val targetsToTest = singleTarget?.let { listOf(it) } ?: SERVICE_TARGETS
        targetsToTest.forEach { target ->
            latencyMap[target.id] = -2 // 测试中
        }
        adapter.updateItem(ItemType.LATENCY)

        targetsToTest.forEach { target ->
            lifecycleScope.launch(Dispatchers.IO) {
                val time = try {
                    measureTimeMillis {
                        val connection = URL(target.testUrl).openConnection() as HttpURLConnection
                        connection.connectTimeout = 4000
                        connection.readTimeout = 4000
                        connection.instanceFollowRedirects = true
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                        connection.connect()
                        connection.disconnect()
                    }
                } catch (_: Exception) {
                    -1L
                }

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        latencyMap[target.id] = time.toInt()
                        adapter.updateItem(ItemType.LATENCY)
                    }
                }
            }
        }
    }

    fun stateChanged(state: BaseService.State, profileName: String?) {
        runOnMainDispatcher {
            if (!isAdded) return@runOnMainDispatcher
            lastState = state
            lastProfileName = profileName
            if (state == BaseService.State.Connected) {
                if (serviceConnectedTime == 0L) serviceConnectedTime = SystemClock.elapsedRealtime()
                lifecycleScope.launch {
                    delay(1.seconds)
                    fetchGeoIP()
                }
                testLatency()
            } else {
                serviceConnectedTime = 0L
            }
            adapter.updateItem(ItemType.STATUS)
            adapter.updateItem(ItemType.RUN_TIME)
            adapter.updateItem(ItemType.TRAFFIC)
        }
    }

    fun trafficUpdated(stats: TrafficStats) {
        runOnMainDispatcher {
            if (!isAdded) return@runOnMainDispatcher
            lastTrafficStats = stats
            val currentRate = stats.rxRateProxy + stats.txRateProxy
            speedHistory.add(currentRate)
            if (speedHistory.size > 30) speedHistory.removeAt(0)

            adapter.updateItem(ItemType.SPEED)
            adapter.updateItem(ItemType.TRAFFIC)
            adapter.updateItem(ItemType.CONNECTIONS)
        }
    }

    fun appStatsUpdated(statsList: List<AppStats>) {
        runOnMainDispatcher {
            if (!isAdded) return@runOnMainDispatcher
            lastAppStatsList = statsList
            activeConnectionsSheet?.takeIf { it.isAdded }?.updateStats(statsList)
            activeSpeedStatsSheet?.takeIf { it.isAdded }?.updateStats(statsList)
        }
    }

    private fun handleImportSubscription() {
        val text = SagerNet.getClipboardText().trim()
        val isUrl = text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)

        if (isUrl) {
            lifecycleScope.launch(Dispatchers.IO) {
                val group = ProxyGroup(type = GroupType.SUBSCRIPTION).apply {
                    subscription = SubscriptionBean().apply {
                        link = text
                        name = getString(R.string.subscription)
                    }
                }
                GroupManager.createGroup(group)
                GroupUpdater.startUpdate(group, true)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        (activity as? MainActivity)?.snackbar("已解析剪贴板订阅链接并开始更新！")?.show()
                        adapter.updateItem(ItemType.PROFILES)
                        adapter.updateItem(ItemType.TRAFFIC)
                    }
                }
            }
        } else if (text.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val proxies = RawUpdater.parseRaw(text)
                    if (!proxies.isNullOrEmpty()) {
                        val targetGroupId = DataStore.currentGroupId()
                        proxies.forEach { bean ->
                            ProfileManager.createProfile(targetGroupId, bean)
                        }
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                (activity as? MainActivity)?.snackbar("成功从剪贴板导入 ${proxies.size} 个节点配置！")?.show()
                                adapter.updateItem(ItemType.PROFILES)
                            }
                        }
                        return@launch
                    }
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    if (isAdded) showImportSubscriptionDialog(text)
                }
            }
        } else {
            showImportSubscriptionDialog("")
        }
    }

    private fun showImportSubscriptionDialog(prefill: String) {
        val context = requireContext()
        val input = TextInputEditText(context).apply {
            hint = "https://..."
            if (prefill.isNotEmpty()) setText(prefill)
        }
        val container = FrameLayout(context).apply {
            val dp16 = dp2px(16)
            setPadding(dp16, dp16 / 2, dp16, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.subscription_import)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text?.toString()?.trim() ?: ""
                if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val group = ProxyGroup(type = GroupType.SUBSCRIPTION).apply {
                            subscription = SubscriptionBean().apply {
                                link = url
                                name = getString(R.string.subscription)
                            }
                        }
                        GroupManager.createGroup(group)
                        GroupUpdater.startUpdate(group, true)
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                (activity as? MainActivity)?.snackbar("已成功添加订阅并更新！")?.show()
                                adapter.updateItem(ItemType.PROFILES)
                                adapter.updateItem(ItemType.TRAFFIC)
                            }
                        }
                    }
                } else {
                    (activity as? MainActivity)?.snackbar(getString(R.string.subscription_import))?.show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .apply { applyGlassBlur() }
            .show()
    }

    private fun handleRefreshDns() {
        if (SagerNet.started) {
            SagerNet.reloadService()
            (activity as? MainActivity)?.snackbar("DNS 缓存已刷新（服务配置已重载）")?.show()
        } else {
            (activity as? MainActivity)?.snackbar("DNS 缓存已重置")?.show()
        }
        adapter.updateItem(ItemType.DNS_QUERIES)
    }

    inner class DashboardAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        val items = DataStore.dashboardOrder.split(",").mapNotNull { id ->
            ItemType.entries.find { it.id == id }
        }.toMutableList()

        override fun getItemViewType(position: Int): Int = items[position].ordinal

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val type = ItemType.entries[viewType]
            val inflater = LayoutInflater.from(parent.context)
            return when (type) {
                ItemType.STATUS -> StatusViewHolder(ItemDashboardStatusBinding.inflate(inflater, parent, false))
                ItemType.SPEED -> SpeedViewHolder(ItemDashboardSpeedBinding.inflate(inflater, parent, false))
                ItemType.TRAFFIC -> TrafficViewHolder(ItemDashboardTrafficBinding.inflate(inflater, parent, false))
                ItemType.GEOIP -> GeoIpViewHolder(ItemDashboardGeoipBinding.inflate(inflater, parent, false))
                ItemType.LATENCY -> LatencyViewHolder(ItemDashboardLatencyBinding.inflate(inflater, parent, false))
                ItemType.QUICK_TOOLS -> QuickToolsViewHolder(ItemDashboardQuickToolsBinding.inflate(inflater, parent, false))
                ItemType.PROFILES -> ProfilesViewHolder(ItemDashboardProfilesBinding.inflate(inflater, parent, false))
                ItemType.CONNECTIONS -> ConnectionsViewHolder(ItemDashboardConnectionsBinding.inflate(inflater, parent, false))
                ItemType.RUN_TIME -> RunTimeViewHolder(ItemDashboardRuntimeBinding.inflate(inflater, parent, false))
                ItemType.DNS_QUERIES -> DnsQueriesViewHolder(ItemDashboardDnsBinding.inflate(inflater, parent, false))
                ItemType.MEMORY_INFO -> MemoryInfoViewHolder(ItemDashboardMemoryBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is StatusViewHolder -> holder.bind()
                is SpeedViewHolder -> holder.bind()
                is TrafficViewHolder -> holder.bind()
                is GeoIpViewHolder -> holder.bind()
                is LatencyViewHolder -> holder.bind()
                is QuickToolsViewHolder -> holder.bind()
                is ProfilesViewHolder -> holder.bind()
                is ConnectionsViewHolder -> holder.bind()
                is RunTimeViewHolder -> holder.bind()
                is DnsQueriesViewHolder -> holder.bind()
                is MemoryInfoViewHolder -> holder.bind()
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateItem(type: ItemType) {
            val index = items.indexOf(type)
            if (index != -1) notifyItemChanged(index)
        }

        private fun setupEditControls(
            itemType: ItemType,
            itemView: View,
            editBar: View,
            resizeBtn: View,
            deleteBtn: View
        ) {
            editBar.isVisible = isEditMode
            itemView.applyCardPressAnimation()

            itemView.setOnLongClickListener {
                if (!isEditMode) {
                    itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    toggleEditMode(true)
                    (activity as? MainActivity)?.snackbar("已进入卡片编辑模式（可拖拽、切大小、删除卡片）")?.show()
                    true
                } else {
                    false
                }
            }

            if (isEditMode) {
                resizeBtn.setOnClickListener {
                    val currentSpan = getCardSpan(itemType.id, itemType.defaultSpan)
                    val newSpan = if (currentSpan == 2) 1 else 2
                    setCardSpan(itemType.id, newSpan)
                }
                deleteBtn.setOnClickListener {
                    removeCard(itemType)
                }
            }
        }

        inner class StatusViewHolder(val binding: ItemDashboardStatusBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.STATUS, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                val profile = ProfileManager.getProfile(DataStore.currentProfile)
                val name = lastProfileName ?: profile?.displayName() ?: getString(R.string.group_status_empty)
                val type = profile?.displayType() ?: ""
                binding.profileName.text = if (type.isNotEmpty()) "$name ($type)" else name

                if (profile != null && profile.status > 0 && profile.ping > 0) {
                    binding.currentPing.isVisible = true
                    binding.currentPing.text = getString(R.string.available, profile.ping)
                } else {
                    binding.currentPing.isGone = true
                }

                when (lastState) {
                    BaseService.State.Connected -> {
                        binding.statusText.setText(R.string.connected)
                        binding.statusIcon.setImageResource(R.drawable.ic_service_active)
                    }
                    else -> {
                        binding.statusText.setText(R.string.disconnected)
                        binding.statusIcon.setImageResource(R.drawable.ic_service_idle)
                    }
                }
            }
        }

        inner class SpeedViewHolder(val binding: ItemDashboardSpeedBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.SPEED, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                val stats = lastTrafficStats
                val context = itemView.context
                binding.speedChart.setSpeedData(speedHistory)

                if (stats != null) {
                    val rxSpeed = FormatFileSizeCompat.formatFileSize(context, stats.rxRateProxy, DataStore.useIECUnit)
                    val txSpeed = FormatFileSizeCompat.formatFileSize(context, stats.txRateProxy, DataStore.useIECUnit)
                    binding.downloadSpeed.text = context.getString(R.string.speed, rxSpeed)
                    binding.uploadSpeed.text = context.getString(R.string.speed, txSpeed)
                } else {
                    binding.downloadSpeed.setText(R.string.speed_zero)
                    binding.uploadSpeed.setText(R.string.speed_zero)
                }

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        val sheet = SpeedStatsBottomSheet()
                        activeSpeedStatsSheet = sheet
                        sheet.updateStats(lastAppStatsList)
                        sheet.show(parentFragmentManager, "SpeedStatsSheet")
                    }
                } else {
                    binding.root.setOnClickListener(null)
                }
            }
        }

        inner class TrafficViewHolder(val binding: ItemDashboardTrafficBinding) : RecyclerView.ViewHolder(binding.root) {
            @SuppressLint("SetTextI18n")
            fun bind() {
                setupEditControls(ItemType.TRAFFIC, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                val context = itemView.context
                val stats = lastTrafficStats

                val txSpeedStr = if (stats != null) FormatFileSizeCompat.formatFileSize(context, stats.txRateProxy, DataStore.useIECUnit) else "0 B"
                val rxSpeedStr = if (stats != null) FormatFileSizeCompat.formatFileSize(context, stats.rxRateProxy, DataStore.useIECUnit) else "0 B"

                binding.trafficUploadText.text = context.getString(R.string.speed, txSpeedStr)
                binding.trafficDownloadText.text = context.getString(R.string.speed, rxSpeedStr)

                val profile = ProfileManager.getProfile(DataStore.currentProfile)
                val group = if (profile != null) SagerDatabase.groupDao.getById(profile.groupId) else null
                val sub = group?.subscription

                if (sub != null) {
                    val used = sub.bytesUsed ?: 0L
                    val remaining = sub.bytesRemaining ?: 0L
                    val total = used + remaining
                    if (total > 0) {
                        val usedStr = FormatFileSizeCompat.formatFileSize(context, used, DataStore.useIECUnit)
                        val totalStr = FormatFileSizeCompat.formatFileSize(context, total, DataStore.useIECUnit)
                        binding.trafficUsage.text = "已用: $usedStr / $totalStr"
                        val percent = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        binding.trafficProgress.progress = (percent * 100).toInt()
                        binding.trafficRingView.setProgressPercent(percent)
                        return
                    }
                }

                if (group != null) {
                    val profiles = SagerDatabase.proxyDao.getByGroup(group.id)
                    val groupTx = profiles.sumOf { it.tx }
                    val groupRx = profiles.sumOf { it.rx }
                    val totalTraffic = groupTx + groupRx
                    val totalStr = FormatFileSizeCompat.formatFileSize(context, totalTraffic, DataStore.useIECUnit)
                    binding.trafficUsage.text = "总计使用: $totalStr"
                    binding.trafficProgress.progress = 100
                    binding.trafficRingView.setProgressPercent(1.0f)
                } else {
                    binding.trafficUsage.setText(R.string.unavailable)
                    binding.trafficProgress.progress = 0
                    binding.trafficRingView.setProgressPercent(0f)
                }
            }
        }

        inner class GeoIpViewHolder(val binding: ItemDashboardGeoipBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.GEOIP, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                if (isGeoIpHidden) {
                    binding.geoipIp.text = "* * * * * * * *"
                    binding.geoipLocation.text = "* * * * *"
                    binding.geoipIsp.text = "* * * * * * * *"
                    binding.geoipIsp.isVisible = true
                } else {
                    binding.geoipIp.text = geoIpInfo.ip.ifEmpty { getString(R.string.unavailable) }
                    binding.geoipLocation.text = geoIpInfo.location.ifEmpty { getString(R.string.unavailable) }
                    val ispInfo = listOf(geoIpInfo.asn, geoIpInfo.isp).filter { it.isNotEmpty() }.joinToString(" - ")
                    binding.geoipIsp.text = ispInfo
                    binding.geoipIsp.isVisible = ispInfo.isNotEmpty()
                }

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        if (isGeoIpHidden) {
                            isGeoIpHidden = false
                            adapter.updateItem(ItemType.GEOIP)
                        } else {
                            fetchGeoIP()
                        }
                    }
                } else {
                    binding.root.setOnClickListener(null)
                }
            }
        }

        inner class LatencyViewHolder(val binding: ItemDashboardLatencyBinding) : RecyclerView.ViewHolder(binding.root) {

            private var selectedIndex = 0
            private val iconPickerAdapter = IconPickerAdapter()

            init {
                binding.iconPickerRecycler.layoutManager = LinearLayoutManager(itemView.context, RecyclerView.HORIZONTAL, false)
                binding.iconPickerRecycler.adapter = iconPickerAdapter

                val snapHelper = PagerSnapHelper()
                snapHelper.attachToRecyclerView(binding.iconPickerRecycler)

                binding.iconPickerRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                            val pos = lm.findFirstCompletelyVisibleItemPosition().takeIf { it != RecyclerView.NO_POSITION }
                                ?: lm.findFirstVisibleItemPosition()
                            if (pos != RecyclerView.NO_POSITION && pos != selectedIndex) {
                                selectIndex(pos, scroll = false)
                            }
                        }
                    }
                })

                val openSheetAction = View.OnClickListener {
                    if (isEditMode) return@OnClickListener
                    val sheet = ServiceStatusBottomSheet(
                        latencyMap = latencyMap,
                        onTargetSelected = { target ->
                            val idx = SERVICE_TARGETS.indexOf(target)
                            if (idx != -1) selectIndex(idx, scroll = true)
                        },
                        onTestAllRequested = { testLatency(null) }
                    )
                    sheet.show((itemView.context as FragmentActivity).supportFragmentManager, "ServiceStatusSheet")
                }

                binding.cardServiceStatus.setOnClickListener(openSheetAction)
                binding.btnOpenDetail.setOnClickListener(openSheetAction)
                binding.latencyCardTitle.setOnClickListener(openSheetAction)

                binding.btnTestAll.setOnClickListener {
                    testLatency(null)
                }

                binding.summaryRow.setOnClickListener {
                    if (isEditMode) return@setOnClickListener
                    val target = SERVICE_TARGETS.getOrNull(selectedIndex)
                    if (target != null) testLatency(target)
                }
            }

            fun bind() {
                setupEditControls(ItemType.LATENCY, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)
                iconPickerAdapter.notifyItemRangeChanged(0, SERVICE_TARGETS.size)
                updateSummary()
            }

            private fun selectIndex(index: Int, scroll: Boolean) {
                if (index !in SERVICE_TARGETS.indices) return
                val prev = selectedIndex
                selectedIndex = index
                iconPickerAdapter.notifyItemChanged(prev)
                iconPickerAdapter.notifyItemChanged(index)
                if (scroll) {
                    binding.iconPickerRecycler.smoothScrollToPosition(index)
                }
                updateSummary()
            }

            private fun updateSummary() {
                val target = SERVICE_TARGETS.getOrNull(selectedIndex) ?: return
                val context = itemView.context

                binding.selectedServiceIcon.setImageResource(target.iconResId)
                binding.selectedServiceName.setText(target.nameResId)

                val latency = latencyMap[target.id] ?: -1

                when {
                    latency == -2 -> {
                        binding.selectedServiceStatusPill.text = context.getString(R.string.connecting)
                        binding.selectedServiceStatusPill.setTextColor(ContextCompat.getColor(context, R.color.material_grey_500))
                        binding.selectedServiceLatency.text = "..."
                        binding.selectedServiceLatency.setTextColor(ContextCompat.getColor(context, R.color.material_grey_500))
                    }
                    latency > 0 -> {
                        binding.selectedServiceStatusPill.text = context.getString(R.string.service_available)
                        binding.selectedServiceStatusPill.setTextColor(ContextCompat.getColor(context, R.color.cupertino_green))
                        binding.selectedServiceLatency.text = context.getString(R.string.available, latency)
                        binding.selectedServiceLatency.setTextColor(ContextCompat.getColor(context, R.color.cupertino_green))
                    }
                    else -> {
                        binding.selectedServiceStatusPill.text = context.getString(R.string.unavailable)
                        binding.selectedServiceStatusPill.setTextColor(ContextCompat.getColor(context, R.color.material_red_500))
                        binding.selectedServiceLatency.text = context.getString(R.string.unavailable)
                        binding.selectedServiceLatency.setTextColor(ContextCompat.getColor(context, R.color.material_red_500))
                    }
                }

                val locationText = geoIpInfo.location.ifEmpty { geoIpInfo.ip }
                val subInfoText = if (locationText.isNotEmpty() && locationText != context.getString(R.string.unavailable)) {
                    "$locationText · ${context.getString(R.string.service_status_sheet_title)}"
                } else {
                    context.getString(R.string.dashboard_latency_desc)
                }
                binding.selectedServiceSubInfo.text = subInfoText
            }

            inner class IconPickerAdapter : RecyclerView.Adapter<IconPickerAdapter.ViewHolder>() {

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                    val inflater = LayoutInflater.from(parent.context)
                    val itemBinding = ItemServiceIconPickerBinding.inflate(inflater, parent, false)
                    return ViewHolder(itemBinding)
                }

                override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                    holder.bind(position)
                }

                override fun getItemCount(): Int = SERVICE_TARGETS.size

                inner class ViewHolder(val itemBinding: ItemServiceIconPickerBinding) : RecyclerView.ViewHolder(itemBinding.root) {
                    fun bind(position: Int) {
                        val target = SERVICE_TARGETS[position]
                        itemBinding.pickerIcon.setImageResource(target.iconResId)

                        val isSelected = position == selectedIndex
                        itemBinding.activeIndicator.isVisible = isSelected
                        itemBinding.pickerIcon.alpha = if (isSelected) 1.0f else 0.5f

                        itemBinding.root.setOnClickListener {
                            selectIndex(position, scroll = true)
                        }
                    }
                }
            }
        }

        inner class QuickToolsViewHolder(val binding: ItemDashboardQuickToolsBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.QUICK_TOOLS, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                binding.btnImportSub.setOnClickListener {
                    if (isEditMode) return@setOnClickListener
                    handleImportSubscription()
                }
                binding.btnRefreshDns.setOnClickListener {
                    if (isEditMode) return@setOnClickListener
                    handleRefreshDns()
                }
                binding.btnShareConfig.setOnClickListener {
                    if (isEditMode) return@setOnClickListener
                    val profile = ProfileManager.getProfile(DataStore.currentProfile)
                    profile?.toLink()?.let { link ->
                        QRCodeDialog(link).showAllowingStateLoss(parentFragmentManager)
                    } ?: run {
                        (requireActivity() as MainActivity).snackbar(getString(R.string.profile_empty)).show()
                    }
                }
            }
        }

        inner class ProfilesViewHolder(val binding: ItemDashboardProfilesBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.PROFILES, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                val profile = ProfileManager.getProfile(DataStore.currentProfile)
                if (profile != null) {
                    binding.profileTitle.text = profile.displayName()
                    val groupName = SagerDatabase.groupDao.getById(profile.groupId)?.displayName() ?: ""
                    val type = profile.displayType()
                    binding.profileGroupName.text = if (type.isNotEmpty()) "$groupName · $type" else groupName
                } else {
                    binding.profileTitle.setText(R.string.group_status_empty)
                    binding.profileGroupName.text = ""
                }

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        val sheet = ProfileNodeBottomSheet {
                            adapter.updateItem(ItemType.PROFILES)
                            adapter.updateItem(ItemType.STATUS)
                            lifecycleScope.launch {
                                delay(1.seconds)
                                fetchGeoIP()
                            }
                            testLatency()
                        }
                        sheet.show(parentFragmentManager, "ProfileNodeSheet")
                    }
                } else {
                    binding.root.setOnClickListener(null)
                }
            }
        }

        inner class ConnectionsViewHolder(val binding: ItemDashboardConnectionsBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.CONNECTIONS, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                val context = itemView.context
                val stats = lastTrafficStats
                if (stats != null) {
                    val totalSpeed = FormatFileSizeCompat.formatFileSize(context, stats.rxRateProxy + stats.txRateProxy, DataStore.useIECUnit)
                    binding.connectionCount.text = context.getString(R.string.speed, totalSpeed)
                    binding.connectionStatus.setText(R.string.connected)
                } else {
                    binding.connectionCount.setText(R.string.speed_zero)
                    binding.connectionStatus.setText(R.string.disconnected)
                }

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        val sheet = ConnectionsBottomSheet()
                        activeConnectionsSheet = sheet
                        sheet.updateStats(lastAppStatsList)
                        sheet.show(parentFragmentManager, "ConnectionsSheet")
                    }
                } else {
                    binding.root.setOnClickListener(null)
                }
            }
        }

        inner class RunTimeViewHolder(val binding: ItemDashboardRuntimeBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.RUN_TIME, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                if (lastState == BaseService.State.Connected && serviceConnectedTime > 0L) {
                    val seconds = ((SystemClock.elapsedRealtime() - serviceConnectedTime) / 1000L).coerceAtLeast(0L)
                    val hrs = seconds / 3600
                    val mins = (seconds % 3600) / 60
                    val secs = seconds % 60
                    binding.runtimeText.text = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
                    binding.runtimeStatus.setText(R.string.connected)
                } else {
                    binding.runtimeText.text = "--:--:--"
                    binding.runtimeStatus.setText(R.string.disconnected)
                }
            }
        }

        inner class DnsQueriesViewHolder(val binding: ItemDashboardDnsBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.DNS_QUERIES, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                binding.dnsTitle.setText(R.string.dashboard_card_dns)
                binding.dnsStatus.text = if (SagerNet.started) "点击重载并刷新 DNS 缓存" else "服务未启动"

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        handleRefreshDns()
                    }
                } else {
                    binding.root.setOnClickListener(null)
                }
            }
        }

        inner class MemoryInfoViewHolder(val binding: ItemDashboardMemoryBinding) : RecyclerView.ViewHolder(binding.root) {
            @SuppressLint("SetTextI18n")
            fun bind() {
                setupEditControls(ItemType.MEMORY_INFO, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                val runtime = Runtime.getRuntime()
                val usedMemMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val maxMemMB = runtime.maxMemory() / (1024 * 1024)

                val context = itemView.context
                binding.memoryText.text = context.getString(R.string.dashboard_traffic_total, "$usedMemMB MB").replace("/", "").trim()
                binding.memoryStatus.text = "JVM Heap $usedMemMB / $maxMemMB MB"

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        val sheet = MemoryStatsBottomSheet {
                            adapter.updateItem(ItemType.MEMORY_INFO)
                        }
                        sheet.show(parentFragmentManager, "MemoryStatsSheet")
                    }
                } else {
                    binding.root.setOnClickListener(null)
                }
            }
        }
    }
}