package io.nekohasekai.sagernet.ui

import io.nekohasekai.sagernet.aidl.AppStats
import kotlin.time.Duration.Companion.seconds

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
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
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.card.MaterialCardView
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
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.widget.QRCodeDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import libexclavecore.Libexclavecore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Collections
import java.util.Locale
import kotlin.system.measureTimeMillis

private const val MENU_ID_EDIT = 1001
private const val MENU_ID_ADD = 1002
private const val MENU_ID_DONE = 1003

// 局部增量刷新 Payload 常量
private const val PAYLOAD_STATUS = 1
private const val PAYLOAD_SPEED = 2
private const val PAYLOAD_TRAFFIC = 3
private const val PAYLOAD_GEOIP = 4
private const val PAYLOAD_LATENCY = 5
private const val PAYLOAD_CONNECTIONS = 6
private const val PAYLOAD_RUNTIME = 7
private const val PAYLOAD_MEMORY = 8
private const val PAYLOAD_PROFILES = 9

data class ServiceTarget(
    val id: String,
    val nameResId: Int,
    val defaultName: String,
    val iconResId: Int,
    val testUrl: String
)

class DashboardFragment : Fragment(R.layout.layout_dashboard) {

    companion object {
        private var hasStartedOnce = false

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

    private var lastState: BaseService.State = BaseService.State.Stopped
    private var lastProfileName: String? = null
    private var lastTrafficStats: TrafficStats? = null
    private var geoIpInfo = GeoIpData()
    private var latencyMap = mutableMapOf<String, Int>()
    private var serviceConnectedTime = 0L
    private var isGeoIpHidden: Boolean
        get() = DataStore.hideOutboundIp
        set(value) {
            DataStore.hideOutboundIp = value
        }
    private val speedHistory = mutableListOf<Long>()
    private var lastAppStatsList: List<AppStats> = emptyList()
    private var activeConnectionsSheet: ConnectionsBottomSheet? = null
    private var activeSpeedStatsSheet: SpeedStatsBottomSheet? = null

    private var editModeFloatingBar: View? = null
    private var btnAddCard: View? = null
    private var btnPresetTemplates: View? = null
    private var btnDoneEdit: View? = null

    var isDashboardVisible = true
        private set
    private var hasPendingUpdate = false
    private var dbSnapshot = DashboardDbSnapshot()
    private var dbSnapshotJob: Job? = null
    private var uptimeJob: Job? = null
    private var touchHelper: ItemTouchHelper? = null

    private val connect = registerForActivityResult(VpnRequestActivity.StartService()) {
        if (it) (activity as? MainActivity)?.snackbar(R.string.vpn_permission_denied)?.show()
    }

    fun toggleVpn() {
        val mainActivity = activity as? MainActivity
        val currentState = mainActivity?.state ?: lastState
        if (currentState.canStop) {
            SagerNet.stopService()
        } else {
            if (DataStore.selectedProxy <= 0L && dbSnapshot.profileName == null) {
                mainActivity?.snackbar(getString(R.string.group_status_empty))?.show()
                return
            }
            connect.launch(null)
        }
    }

    private data class DashboardDbSnapshot(
        val profileId: Long = 0L,
        val profileName: String? = null,
        val profileType: String = "",
        val profileStatus: Int = 0,
        val profilePing: Int = 0,
        val groupName: String = "",
        val hasGroup: Boolean = false,
        val groupTx: Long = 0L,
        val groupRx: Long = 0L,
        val subUsed: Long = 0L,
        val subTotal: Long = 0L,
    )

    private val profileListener = object : ProfileManager.Listener {
        override suspend fun onAdd(profile: ProxyEntity) {
            runOnMainDispatcher { if (isAdded) refreshDbSnapshot() }
        }
        override suspend fun onUpdated(profileId: Long, trafficStats: TrafficStats) {}
        override suspend fun onUpdated(profile: ProxyEntity) {
            runOnMainDispatcher { if (isAdded) refreshDbSnapshot() }
        }
        override suspend fun onRemoved(groupId: Long, profileId: Long) {
            runOnMainDispatcher { if (isAdded) refreshDbSnapshot() }
        }
    }

    data class GeoIpData(
        var ip: String = "",
        var location: String = "",
        var isp: String = "",
        var asn: String = "",
        var countryCode: String = "",
        var ipType: String = "",
        var qualityLevel: String = "",
        var riskScore: Int = 0,
        var isVpn: Boolean = false,
        var isProxy: Boolean = false,
        var isTor: Boolean = false,
        var isDatacenter: Boolean = false,
        var isMobile: Boolean = false,
        var isAbuser: Boolean = false,
        var organization: String = "",
        var source: String = "ipquery.io"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.dashboard_recycler)

        editModeFloatingBar = view.findViewById(R.id.edit_mode_floating_bar)
        (editModeFloatingBar as? MaterialCardView)?.let { bar ->
            val ctx = bar.context
            bar.setCardBackgroundColor(Theme.getCardBackgroundColor(ctx, false))
            bar.strokeColor = Theme.getCardStrokeColor(ctx, false)
            bar.strokeWidth = ctx.resources.getDimensionPixelSize(R.dimen.card_stroke_width)
        }
        btnAddCard = view.findViewById(R.id.btn_add_card)
        btnPresetTemplates = view.findViewById(R.id.btn_preset_templates)
        btnDoneEdit = view.findViewById(R.id.btn_done_edit)

        btnAddCard?.setOnClickListener { openAddCardSheet() }
        btnPresetTemplates?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showPresetTemplatesDialog()
        }
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
        if (!Theme.isCupertino()) {
            view.setBackgroundColor(Theme.getClassicListBackgroundColor())
            recyclerView?.setBackgroundColor(Theme.getClassicListBackgroundColor())
        }
        recyclerView?.applyGlassBlur()
        recyclerView?.adapter = adapter
        (recyclerView?.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val mainActivity = activity as? MainActivity ?: return
                if (dy > 6) {
                    mainActivity.binding.cupertinoDock.hidePanel()
                } else if (dy < -6) {
                    mainActivity.binding.cupertinoDock.showPanel()
                }
            }
        })

        setupTouchHelper()

        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            lastState = mainActivity.state
            if (lastState == BaseService.State.Connected) {
                if (serviceConnectedTime == 0L) serviceConnectedTime = SystemClock.elapsedRealtime()
                fetchGeoIP(force = true)
                testLatency()
            } else if (geoIpInfo.ip.isEmpty()) {
                fetchGeoIP(force = false)
            }
        }

        ProfileManager.addListener(profileListener)
        refreshDbSnapshot()

        startUptimeTimer()
    }

    override fun onResume() {
        super.onResume()
        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            val currentState = mainActivity.state
            if (lastState != currentState) {
                stateChanged(currentState, lastProfileName)
            }
        }
        refreshDbSnapshot()
        val parentPager = (parentFragment as? ConfigurationFragment)?.groupPager
        val isVisibleInPager = parentPager?.currentItem == 0
        onVisibilityInPagerChanged(isVisibleInPager)
    }

    override fun onPause() {
        super.onPause()
        onVisibilityInPagerChanged(false)
    }

    override fun onDestroyView() {
        ProfileManager.removeListener(profileListener)
        uptimeJob?.cancel()
        uptimeJob = null
        dbSnapshotJob?.cancel()
        dbSnapshotJob = null
        geoIpJob?.cancel()
        geoIpJob = null
        touchHelper?.attachToRecyclerView(null)
        touchHelper = null
        activeConnectionsSheet = null
        activeSpeedStatsSheet = null
        recyclerView?.adapter = null
        super.onDestroyView()
        recyclerView = null
        editModeFloatingBar = null
        btnAddCard = null
        btnPresetTemplates = null
        btnDoneEdit = null
    }

    private fun refreshDbSnapshot() {
        if (!isAdded || view == null) return
        dbSnapshotJob?.cancel()
        dbSnapshotJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main.immediate) {
            val snapshot = withContext(Dispatchers.IO) {
                val profileId = DataStore.currentProfile
                val profile = ProfileManager.getProfile(profileId)
                val group = if (profile != null) {
                    try { SagerDatabase.groupDao.getById(profile.groupId) } catch (_: Exception) { null }
                } else null
                val sub = group?.subscription
                val profiles = if (group != null) {
                    try { SagerDatabase.proxyDao.getByGroup(group.id) } catch (_: Exception) { emptyList() }
                } else emptyList()
                val used = sub?.bytesUsed ?: 0L
                val remaining = sub?.bytesRemaining ?: 0L
                DashboardDbSnapshot(
                    profileId = profileId,
                    profileName = profile?.displayName(),
                    profileType = profile?.displayType() ?: "",
                    profileStatus = profile?.status ?: 0,
                    profilePing = profile?.ping ?: 0,
                    groupName = group?.displayName() ?: "",
                    hasGroup = group != null,
                    groupTx = profiles.sumOf { it.tx },
                    groupRx = profiles.sumOf { it.rx },
                    subUsed = used,
                    subTotal = used + remaining,
                )
            }
            if (!isActive || !isAdded) return@launch
            dbSnapshot = snapshot
            if (::adapter.isInitialized) {
                if (isDashboardVisible && isResumed) {
                    adapter.updateItemWithPayload(ItemType.STATUS, PAYLOAD_STATUS)
                    adapter.updateItemWithPayload(ItemType.TRAFFIC, PAYLOAD_TRAFFIC)
                    adapter.updateItemWithPayload(ItemType.PROFILES, PAYLOAD_PROFILES)
                } else {
                    hasPendingUpdate = true
                }
            }
        }
    }

    fun needsAppStatsUpdates(): Boolean {
        return isAdded &&
            isResumed &&
            isDashboardVisible &&
            DataStore.appTrafficStatistics &&
            ::adapter.isInitialized &&
            ItemType.CONNECTIONS in adapter.items
    }

    fun onVisibilityInPagerChanged(visible: Boolean) {
        if (isDashboardVisible == visible) {
            (activity as? MainActivity)?.updateTrafficTimeout()
            return
        }
        isDashboardVisible = visible
        (activity as? MainActivity)?.updateTrafficTimeout()
        if (visible) {
            val mainActivity = activity as? MainActivity
            if (mainActivity != null) {
                val currentState = mainActivity.state
                if (lastState != currentState) {
                    stateChanged(currentState, lastProfileName)
                }
            }
            refreshDbSnapshot()
            if (hasPendingUpdate && ::adapter.isInitialized) {
                hasPendingUpdate = false
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            } else if (::adapter.isInitialized) {
                val statusIndex = adapter.items.indexOf(ItemType.STATUS)
                if (statusIndex != -1) {
                    val holder = recyclerView?.findViewHolderForAdapterPosition(statusIndex) as? DashboardAdapter.StatusViewHolder
                    if (lastState == BaseService.State.Connected) {
                        holder?.startBreathing()
                    }
                }
            }
        } else {
            val statusIndex = if (::adapter.isInitialized) adapter.items.indexOf(ItemType.STATUS) else -1
            if (statusIndex != -1) {
                val holder = recyclerView?.findViewHolderForAdapterPosition(statusIndex) as? DashboardAdapter.StatusViewHolder
                holder?.stopBreathing()
            }
        }
    }

    fun showPresetTemplatesDialog() {
        val context = context ?: return
        val templateNames = arrayOf(
            getString(R.string.dashboard_template_default),
            getString(R.string.dashboard_template_minimal),
            getString(R.string.dashboard_template_pro)
        )

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dashboard_templates)
            .setItems(templateNames) { _, which ->
                when (which) {
                    0 -> { // Default Balanced
                        val order = listOf(
                            ItemType.STATUS, ItemType.SPEED, ItemType.TRAFFIC, ItemType.GEOIP,
                            ItemType.LATENCY, ItemType.QUICK_TOOLS, ItemType.PROFILES,
                            ItemType.CONNECTIONS, ItemType.RUN_TIME, ItemType.DNS_QUERIES, ItemType.MEMORY_INFO
                        )
                        val sizes = mapOf(
                            "status" to 2, "speed" to 2, "traffic" to 1, "geoip" to 1,
                            "latency" to 2, "quick_tools" to 2, "profiles" to 1,
                            "connections" to 1, "run_time" to 1, "dns_queries" to 1, "memory_info" to 1
                        )
                        applyPresetTemplate(order, sizes)
                    }
                    1 -> { // Minimalist
                        val order = listOf(
                            ItemType.STATUS, ItemType.SPEED, ItemType.TRAFFIC,
                            ItemType.PROFILES, ItemType.QUICK_TOOLS
                        )
                        val sizes = mapOf(
                            "status" to 2, "speed" to 2, "traffic" to 2,
                            "profiles" to 2, "quick_tools" to 2
                        )
                        applyPresetTemplate(order, sizes)
                    }
                    2 -> { // Pro Monitor
                        val order = listOf(
                            ItemType.SPEED, ItemType.CONNECTIONS, ItemType.TRAFFIC,
                            ItemType.GEOIP, ItemType.RUN_TIME, ItemType.MEMORY_INFO,
                            ItemType.DNS_QUERIES, ItemType.LATENCY, ItemType.STATUS
                        )
                        val sizes = mapOf(
                            "speed" to 2, "connections" to 1, "traffic" to 1,
                            "geoip" to 1, "run_time" to 1, "memory_info" to 1,
                            "dns_queries" to 1, "latency" to 2, "status" to 2
                        )
                        applyPresetTemplate(order, sizes)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .apply { applyGlassBlur() }
            .show()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun applyPresetTemplate(orderList: List<ItemType>, spanMap: Map<String, Int>) {
        saveCardSpans(spanMap)
        adapter.items.clear()
        adapter.items.addAll(orderList)
        saveDashboardOrder()
        recyclerView?.layoutManager?.requestLayout()
        adapter.notifyDataSetChanged()
        (activity as? MainActivity)?.snackbar(getString(R.string.dashboard_template_applied))?.show()
    }

    fun countryCodeToEmoji(countryCode: String?): String {
        if (countryCode.isNullOrEmpty() || countryCode.length != 2) return ""
        val code = countryCode.uppercase(Locale.ROOT)
        val firstChar = code[0]
        val secondChar = code[1]
        if (firstChar !in 'A'..'Z' || secondChar !in 'A'..'Z') return ""
        val firstCodePoint = 0x1F1E6 + (firstChar - 'A')
        val secondCodePoint = 0x1F1E6 + (secondChar - 'A')
        return String(Character.toChars(firstCodePoint)) + String(Character.toChars(secondCodePoint))
    }

    private fun getCountryFlagDrawable(countryCode: String?): Int? {
        if (countryCode.isNullOrEmpty()) return null
        return when (countryCode.uppercase(Locale.ROOT)) {
            "CN" -> R.drawable.ic_flag_cn
            "HK" -> R.drawable.ic_flag_hk
            "TW" -> R.drawable.ic_flag_tw
            "US" -> R.drawable.ic_flag_us
            "JP" -> R.drawable.ic_flag_jp
            "SG" -> R.drawable.ic_flag_sg
            "KR" -> R.drawable.ic_flag_kr
            "GB", "UK" -> R.drawable.ic_flag_gb
            "DE" -> R.drawable.ic_flag_de
            "FR" -> R.drawable.ic_flag_fr
            "RU" -> R.drawable.ic_flag_ru
            "CA" -> R.drawable.ic_flag_ca
            "AU" -> R.drawable.ic_flag_au
            else -> null
        }
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
        if (parentFragmentManager.findFragmentByTag("AddDashboardCardSheet") != null) return
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
            (activity as? MainActivity)?.updateTrafficTimeout()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun removeCard(type: ItemType) {
        val index = adapter.items.indexOf(type)
        if (index != -1) {
            adapter.items.removeAt(index)
            saveDashboardOrder()
            adapter.notifyDataSetChanged()
            (activity as? MainActivity)?.updateTrafficTimeout()
        }
    }

    private fun saveDashboardOrder() {
        DataStore.dashboardOrder = adapter.items.joinToString(",") { it.id }
    }

    private fun setupTouchHelper() {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
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

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?,
                actionState: Int
            ) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.let { item ->
                        item.scaleX = 1.02f
                        item.scaleY = 1.02f
                        item.alpha = 0.92f
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.scaleX = 1.0f
                viewHolder.itemView.scaleY = 1.0f
                viewHolder.itemView.alpha = 1.0f
                saveDashboardOrder()
            }
        })
        helper.attachToRecyclerView(recyclerView)
        touchHelper = helper
    }

    private fun startUptimeTimer() {
        uptimeJob?.cancel()
        uptimeJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            while (isAdded) {
                if (isDashboardVisible && isResumed) {
                    if (lastState == BaseService.State.Connected) {
                        adapter.updateItemWithPayload(ItemType.STATUS, PAYLOAD_STATUS)
                        adapter.updateItemWithPayload(ItemType.RUN_TIME, PAYLOAD_RUNTIME)
                    }
                    adapter.updateItemWithPayload(ItemType.MEMORY_INFO, PAYLOAD_MEMORY)
                } else {
                    hasPendingUpdate = true
                }
                delay(1.seconds)
            }
        }
    }

    private var isFetchingGeoIP = false
    private var geoIpJob: Job? = null

    private suspend fun httpGetViaCore(urlStr: String, useProxyUds: Boolean, timeoutMs: Long = 5000L): String? {
        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                val client = Libexclavecore.newHttpClient()
                try {
                    if (useProxyUds) {
                        val udsPath = SagerNet.deviceStorage.noBackupFilesDir.toString() + "/ipc.sock"
                        client.useUDS(udsPath)
                    }
                    val req = client.newRequest().apply {
                        setURL(urlStr)
                        setUserAgent("Mozilla/5.0 (Android; Mobile) Exclave")
                    }
                    val resp = req.execute()
                    resp.contentString?.takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                } finally {
                    runCatching { client.close() }
                }
            }
        }
    }

    private fun parseIpQueryJson(body: String, target: GeoIpData): Boolean {
        return try {
            val json = JSONObject(body)
            val ip = json.optString("ip", "")
            if (ip.isEmpty()) return false

            target.ip = ip
            val locObj = json.optJSONObject("location")
            val country = locObj?.optString("country", "") ?: ""
            val city = locObj?.optString("city", "") ?: ""
            if (country.isNotEmpty() || city.isNotEmpty()) {
                target.location = if (city.isNotEmpty() && country.isNotEmpty()) "$country, $city" else country.ifEmpty { city }
            }
            val cc = locObj?.optString("country_code", "") ?: ""
            if (cc.isNotEmpty()) target.countryCode = cc

            val ispObj = json.optJSONObject("isp")
            val org = ispObj?.optString("org", "") ?: ""
            val isp = ispObj?.optString("isp", "") ?: ""
            if (org.isNotEmpty()) target.organization = org
            if (org.isNotEmpty() || isp.isNotEmpty()) target.isp = org.ifEmpty { isp }
            val asn = ispObj?.optString("asn", "") ?: ""
            if (asn.isNotEmpty()) target.asn = asn

            val riskObj = json.optJSONObject("risk")
            if (riskObj != null) {
                target.isDatacenter = riskObj.optBoolean("is_datacenter", false)
                target.isMobile = riskObj.optBoolean("is_mobile", false)
                target.isVpn = riskObj.optBoolean("is_vpn", false)
                target.isProxy = riskObj.optBoolean("is_proxy", false)
                target.isTor = riskObj.optBoolean("is_tor", false)
                target.riskScore = riskObj.optInt("risk_score", 0)
                target.isAbuser = target.riskScore >= 50
            }

            val appCtx = io.nekohasekai.sagernet.ktx.app
            target.ipType = when {
                target.isDatacenter -> appCtx.getString(R.string.ip_type_hosting)
                target.isMobile -> appCtx.getString(R.string.ip_type_mobile)
                else -> appCtx.getString(R.string.ip_type_residential)
            }
            target.qualityLevel = when {
                target.isTor || target.isProxy || target.isVpn || target.riskScore >= 60 -> appCtx.getString(R.string.ip_quality_risky)
                target.riskScore >= 25 -> appCtx.getString(R.string.ip_quality_normal)
                else -> appCtx.getString(R.string.ip_quality_good)
            }
            target.source = "ipquery.io"
            true
        } catch (_: Exception) {
            false
        }
    }

    fun fetchGeoIP(force: Boolean = true) {
        if (isFetchingGeoIP && !force) return
        geoIpJob?.cancel()
        isFetchingGeoIP = true

        geoIpInfo.ip = ""
        geoIpInfo.location = ""
        geoIpInfo.isp = ""
        geoIpInfo.organization = ""
        geoIpInfo.asn = ""
        geoIpInfo.countryCode = ""
        geoIpInfo.ipType = ""
        geoIpInfo.qualityLevel = ""
        geoIpInfo.riskScore = 0
        geoIpInfo.isVpn = false
        geoIpInfo.isProxy = false
        geoIpInfo.isTor = false
        geoIpInfo.isDatacenter = false
        geoIpInfo.isMobile = false
        geoIpInfo.isAbuser = false
        geoIpInfo.source = ""

        if (::adapter.isInitialized) {
            adapter.updateItemWithPayload(ItemType.GEOIP, PAYLOAD_GEOIP)
            adapter.updateItemWithPayload(ItemType.STATUS, PAYLOAD_STATUS)
        }

        val useProxyUds = lastState == BaseService.State.Connected && SagerNet.started
        geoIpJob = lifecycleScope.launch(Dispatchers.IO) {
            val result = GeoIpData()
            var success = false
            var hasRichQuality = false
            val appCtx = io.nekohasekai.sagernet.ktx.app

            // Wave 1: ipquery.io (Single-shot IP + Geo + ASN + full risk/datacenter quality)
            val w1 = httpGetViaCore("https://api.ipquery.io/?format=json", useProxyUds, 5000L)
            if (w1 != null && parseIpQueryJson(w1, result)) {
                success = true
                hasRichQuality = true
            }

            // Wave 2: ipwho.is
            if (!success && isActive) {
                val w2 = httpGetViaCore("https://ipwho.is/", useProxyUds, 5000L)
                if (w2 != null) {
                    try {
                        val json = JSONObject(w2)
                        val ip = json.optString("ip", "")
                        if (ip.isNotEmpty()) {
                            result.ip = ip
                            val country = json.optString("country", "")
                            val city = json.optString("city", "")
                            result.location = if (city.isNotEmpty()) "$country, $city" else country
                            val conn = json.optJSONObject("connection")
                            val isp = conn?.optString("isp", "") ?: ""
                            val org = conn?.optString("org", "") ?: ""
                            result.organization = org
                            result.isp = org.ifEmpty { isp }
                            val asn = conn?.optInt("asn", 0) ?: 0
                            result.asn = if (asn != 0) "AS$asn" else ""
                            result.countryCode = json.optString("country_code", "")
                            val isHosting = org.contains("cloud", ignoreCase = true) ||
                                org.contains("server", ignoreCase = true) ||
                                org.contains("hosting", ignoreCase = true) ||
                                org.contains("datacenter", ignoreCase = true) ||
                                isp.contains("cloud", ignoreCase = true)
                            result.isDatacenter = isHosting
                            result.ipType = if (isHosting) appCtx.getString(R.string.ip_type_hosting) else appCtx.getString(R.string.ip_type_residential)
                            result.qualityLevel = appCtx.getString(R.string.ip_quality_good)
                            result.source = "ipwho.is"
                            success = true
                        }
                    } catch (_: Exception) {}
                }
            }

            // Wave 3: api.ip.sb
            if (!success && isActive) {
                val w3 = httpGetViaCore("https://api.ip.sb/geoip", useProxyUds, 5000L)
                if (w3 != null) {
                    try {
                        val json = JSONObject(w3)
                        val ip = json.optString("ip", "")
                        if (ip.isNotEmpty()) {
                            result.ip = ip
                            val country = json.optString("country", "")
                            val city = json.optString("city", "")
                            result.location = if (city.isNotEmpty()) "$country, $city" else country
                            val isp = json.optString("isp", "")
                            result.isp = isp
                            result.organization = json.optString("organization", isp)
                            val asn = json.optInt("asn", 0)
                            result.asn = if (asn != 0) "AS$asn" else ""
                            result.countryCode = json.optString("country_code", "")
                            result.ipType = appCtx.getString(R.string.ip_type_hosting)
                            result.qualityLevel = appCtx.getString(R.string.ip_quality_good)
                            result.source = "ip.sb"
                            success = true
                        }
                    } catch (_: Exception) {}
                }
            }

            // Wave 4: ipinfo.io
            if (!success && isActive) {
                val w4 = httpGetViaCore("https://ipinfo.io/json", useProxyUds, 5000L)
                if (w4 != null) {
                    try {
                        val json = JSONObject(w4)
                        val ip = json.optString("ip", "")
                        if (ip.isNotEmpty()) {
                            result.ip = ip
                            val country = json.optString("country", "")
                            val city = json.optString("city", "")
                            result.countryCode = country
                            result.location = if (city.isNotEmpty() && country.isNotEmpty()) "$country, $city" else country.ifEmpty { city }
                            val orgRaw = json.optString("org", "")
                            if (orgRaw.startsWith("AS", ignoreCase = true) && orgRaw.contains(" ")) {
                                result.asn = orgRaw.substringBefore(" ")
                                result.organization = orgRaw.substringAfter(" ")
                                result.isp = result.organization
                            } else {
                                result.organization = orgRaw
                                result.isp = orgRaw
                            }
                            result.ipType = appCtx.getString(R.string.ip_type_hosting)
                            result.qualityLevel = appCtx.getString(R.string.ip_quality_good)
                            result.source = "ipinfo.io"
                            success = true
                        }
                    } catch (_: Exception) {}
                }
            }

            // Wave 5: Cloudflare trace (ultra-reliable fallback)
            if (!success && isActive) {
                val w5 = httpGetViaCore("https://www.cloudflare.com/cdn-cgi/trace", useProxyUds, 5000L)
                if (w5 != null) {
                    val map = w5.lineSequence()
                        .mapNotNull { line ->
                            val idx = line.indexOf('=')
                            if (idx > 0) line.substring(0, idx) to line.substring(idx + 1).trim() else null
                        }.toMap()
                    val ip = map["ip"].orEmpty()
                    if (ip.isNotEmpty()) {
                        result.ip = ip
                        result.countryCode = map["loc"].orEmpty()
                        result.location = map["loc"].orEmpty()
                        result.ipType = appCtx.getString(R.string.ip_type_hosting)
                        result.qualityLevel = appCtx.getString(R.string.ip_quality_good)
                        result.source = "cloudflare"
                        success = true
                    }
                }
            }

            // Quality enrichment pass: if we got the exit IP from a basic source, enrich full quality via ipquery.io
            if (success && !hasRichQuality && result.ip.isNotEmpty() && isActive) {
                val enrichBody = httpGetViaCore("https://api.ipquery.io/${result.ip}?format=json", useProxyUds, 4000L)
                    ?: httpGetViaCore("https://api.ipquery.io/${result.ip}?format=json", false, 4000L)
                if (enrichBody != null) {
                    parseIpQueryJson(enrichBody, result)
                }
            }

            if (!isActive) return@launch

            if (success) {
                geoIpInfo = result
            } else {
                val unavailableText = appCtx.getString(R.string.unavailable)
                geoIpInfo = GeoIpData(
                    ip = unavailableText,
                    location = unavailableText,
                )
            }

            isFetchingGeoIP = false
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    if (isDashboardVisible && isResumed) {
                        adapter.updateItemWithPayload(ItemType.GEOIP, PAYLOAD_GEOIP)
                        adapter.updateItemWithPayload(ItemType.STATUS, PAYLOAD_STATUS)
                    } else {
                        hasPendingUpdate = true
                    }
                    (parentFragmentManager.findFragmentByTag("IpQualitySheet") as? IpQualityBottomSheet)?.updateData(geoIpInfo)
                }
            }
        }
    }

    private fun testLatency(singleTarget: ServiceTarget? = null) {
        val targetsToTest = singleTarget?.let { listOf(it) } ?: SERVICE_TARGETS
        targetsToTest.forEach { target ->
            latencyMap[target.id] = -2
        }
        if (isDashboardVisible && isResumed) {
            adapter.updateItemWithPayload(ItemType.LATENCY, PAYLOAD_LATENCY)
        } else {
            hasPendingUpdate = true
        }

        targetsToTest.forEach { target ->
            lifecycleScope.launch(Dispatchers.IO) {
                val time = try {
                    measureTimeMillis {
                        val connection = if (SagerNet.started && DataStore.requireSocks) {
                            val proxy = Proxy(
                                Proxy.Type.SOCKS,
                                InetSocketAddress("127.0.0.1", DataStore.socksPort)
                            )
                            URL(target.testUrl).openConnection(proxy) as HttpURLConnection
                        } else {
                            URL(target.testUrl).openConnection() as HttpURLConnection
                        }
                        connection.connectTimeout = 3500
                        connection.readTimeout = 3500
                        connection.instanceFollowRedirects = true
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                        connection.connect()
                        connection.disconnect()
                    }
                } catch (_: Exception) {
                    -3L
                }

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        latencyMap[target.id] = time.toInt()
                        if (isDashboardVisible && isResumed) {
                            adapter.updateItemWithPayload(ItemType.LATENCY, PAYLOAD_LATENCY)
                        } else {
                            hasPendingUpdate = true
                        }
                    }
                }
            }
        }
    }

    fun stateChanged(state: BaseService.State, profileName: String?) {
        runOnMainDispatcher {
            if (!isAdded) return@runOnMainDispatcher
            val prevState = lastState
            lastState = state
            lastProfileName = profileName
            if (state == BaseService.State.Connected) {
                if (serviceConnectedTime == 0L) serviceConnectedTime = SystemClock.elapsedRealtime()
                lifecycleScope.launch {
                    delay(800L)
                    if (isAdded && lastState == BaseService.State.Connected) {
                        fetchGeoIP(force = true)
                    }
                }
                testLatency()
            } else {
                serviceConnectedTime = 0L
                if (state == BaseService.State.Stopped && prevState == BaseService.State.Connected) {
                    fetchGeoIP(force = true)
                }
            }
            if (isDashboardVisible && isResumed) {
                adapter.updateItemWithPayload(ItemType.STATUS, PAYLOAD_STATUS)
                adapter.updateItemWithPayload(ItemType.RUN_TIME, PAYLOAD_RUNTIME)
                adapter.updateItemWithPayload(ItemType.TRAFFIC, PAYLOAD_TRAFFIC)
                adapter.updateItemWithPayload(ItemType.CONNECTIONS, PAYLOAD_CONNECTIONS)
            } else {
                hasPendingUpdate = true
            }
            refreshDbSnapshot()
        }
    }

    fun trafficUpdated(stats: TrafficStats) {
        runOnMainDispatcher {
            if (!isAdded) return@runOnMainDispatcher
            lastTrafficStats = stats
            val currentRate = stats.rxRateProxy + stats.txRateProxy
            speedHistory.add(currentRate)
            if (speedHistory.size > 30) speedHistory.removeAt(0)

            if (isDashboardVisible && isResumed) {
                adapter.updateItemWithPayload(ItemType.SPEED, PAYLOAD_SPEED)
                adapter.updateItemWithPayload(ItemType.TRAFFIC, PAYLOAD_TRAFFIC)
                adapter.updateItemWithPayload(ItemType.CONNECTIONS, PAYLOAD_CONNECTIONS)
            } else {
                hasPendingUpdate = true
            }
        }
    }

    fun appStatsUpdated(statsList: List<AppStats>) {
        runOnMainDispatcher {
            if (!isAdded) return@runOnMainDispatcher
            lastAppStatsList = statsList
            activeConnectionsSheet?.takeIf { it.isAdded }?.updateStats(statsList)
            activeSpeedStatsSheet?.takeIf { it.isAdded }?.updateStats(statsList)
            if (isDashboardVisible && isResumed && ::adapter.isInitialized) {
                adapter.updateItemWithPayload(ItemType.CONNECTIONS, PAYLOAD_CONNECTIONS)
            } else {
                hasPendingUpdate = true
            }
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
                        refreshDbSnapshot()
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
                                refreshDbSnapshot()
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
                                refreshDbSnapshot()
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

        private fun applyFrostedCardStyle(card: MaterialCardView) {
            val ctx = card.context
            card.setCardBackgroundColor(Theme.getCardBackgroundColor(ctx, false))
            card.strokeColor = Theme.getCardStrokeColor(ctx, false)
            card.strokeWidth = ctx.resources.getDimensionPixelSize(R.dimen.card_stroke_width)
            card.radius = dp2px(20).toFloat()
            card.cardElevation = 0f
            card.maxCardElevation = 0f
            card.preventCornerOverlap = false
            card.useCompatPadding = false
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val type = ItemType.entries[viewType]
            val inflater = LayoutInflater.from(parent.context)
            val holder = when (type) {
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
            (holder.itemView as? MaterialCardView)?.let { applyFrostedCardStyle(it) }
            return holder
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder.itemView as? MaterialCardView)?.let { applyFrostedCardStyle(it) }
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

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isNotEmpty()) {
                // 局部高效增量刷新，跳过重新设置卡片样式和重新绑定点击事件
                when (holder) {
                    is StatusViewHolder -> holder.updatePartial()
                    is SpeedViewHolder -> holder.updatePartial()
                    is TrafficViewHolder -> holder.updatePartial()
                    is GeoIpViewHolder -> holder.updatePartial()
                    is LatencyViewHolder -> holder.updatePartial()
                    is ProfilesViewHolder -> holder.updatePartial()
                    is ConnectionsViewHolder -> holder.updatePartial()
                    is RunTimeViewHolder -> holder.updatePartial()
                    is DnsQueriesViewHolder -> holder.updatePartial()
                    is MemoryInfoViewHolder -> holder.updatePartial()
                    else -> onBindViewHolder(holder, position)
                }
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            if (holder is StatusViewHolder) {
                holder.stopBreathing()
            }
            super.onViewRecycled(holder)
        }

        override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
            if (holder is StatusViewHolder) {
                holder.stopBreathing()
            }
            super.onViewDetachedFromWindow(holder)
        }

        override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
            super.onViewAttachedToWindow(holder)
            if (holder is StatusViewHolder) {
                holder.syncBreathingState()
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateItem(type: ItemType) {
            val index = items.indexOf(type)
            if (index != -1) notifyItemChanged(index)
        }

        fun updateItemWithPayload(type: ItemType, payload: Any) {
            val index = items.indexOf(type)
            if (index != -1) notifyItemChanged(index, payload)
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
            private var breathingAnimator: ObjectAnimator? = null
            private var badgeRotateAnimator: ObjectAnimator? = null

            fun startBreathing(view: View = binding.btnMorphToggle) {
                if (breathingAnimator != null) return
                val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.03f, 1.0f)
                val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.03f, 1.0f)
                breathingAnimator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
                    duration = 2000L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }

            fun stopBreathing() {
                breathingAnimator?.cancel()
                breathingAnimator = null
                binding.btnMorphToggle.alpha = 1.0f
                binding.btnMorphToggle.scaleX = 1.0f
                binding.btnMorphToggle.scaleY = 1.0f
            }

            fun syncBreathingState() {
                if (lastState == BaseService.State.Connected) {
                    startBreathing()
                } else {
                    stopBreathing()
                }
            }

            private fun startBadgeSpin() {
                if (badgeRotateAnimator != null) return
                badgeRotateAnimator = ObjectAnimator.ofFloat(binding.btnCountryBadge, View.ROTATION, 0f, 360f).apply {
                    duration = 1200L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            }

            private fun stopBadgeSpin() {
                badgeRotateAnimator?.cancel()
                badgeRotateAnimator = null
                binding.btnCountryBadge.rotation = 0f
            }

            fun bind() {
                setupEditControls(ItemType.STATUS, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                binding.btnMorphToggle.setOnClickListener {
                    binding.btnMorphToggle.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    toggleVpn()
                }

                binding.btnCountryBadge.applyCardPressAnimation()
                binding.btnCountryBadge.setOnClickListener {
                    binding.btnCountryBadge.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    fetchGeoIP()
                }

                val showQualitySheet = {
                    if (parentFragmentManager.findFragmentByTag("IpQualitySheet") == null) {
                        val sheet = IpQualityBottomSheet(
                            geoIpInfo = geoIpInfo,
                            isIpMasked = isGeoIpHidden,
                            onHideIpChanged = { masked ->
                                isGeoIpHidden = masked
                                updatePartial()
                            },
                            onRefreshRequested = {
                                fetchGeoIP()
                            }
                        )
                        sheet.show(parentFragmentManager, "IpQualitySheet")
                    }
                }

                binding.textOutboundIp.setOnClickListener {
                    binding.textOutboundIp.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    showQualitySheet()
                }

                binding.exitRow.setOnClickListener {
                    binding.exitRow.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    showQualitySheet()
                }

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        showQualitySheet()
                    }
                }

                binding.textOutboundIp.setOnLongClickListener {
                    val ip = geoIpInfo.ip
                    if (ip.isNotEmpty() && ip != getString(R.string.unavailable)) {
                        binding.textOutboundIp.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        SagerNet.trySetPrimaryClip(ip)
                        (activity as? MainActivity)?.snackbar(getString(R.string.copied_ip))?.show()
                        true
                    } else {
                        false
                    }
                }

                updatePartial()
            }

            fun updatePartial() {
                val context = itemView.context
                val accentColor = Theme.getAccentColor(context)
                val snapshot = dbSnapshot
                val profileName = lastProfileName ?: snapshot.profileName ?: getString(R.string.group_status_empty)

                // 1. Status Header & Chip
                when (lastState) {
                    BaseService.State.Connected -> {
                        binding.chipStatus.setBackgroundResource(R.drawable.bg_dashboard_chip_ok)
                        binding.chipStatusDot.isVisible = true
                        binding.chipStatusDot.setBackgroundResource(R.drawable.bg_chip_dot_green)
                        binding.chipStatusText.setText(R.string.status_running)
                        binding.chipStatusText.setTextColor(ContextCompat.getColor(context, R.color.material_green_500))
                        binding.statusHeaderGlyph.imageTintList = ColorStateList.valueOf(accentColor)
                    }
                    BaseService.State.Connecting -> {
                        binding.chipStatus.setBackgroundResource(R.drawable.bg_dashboard_chip_warn)
                        binding.chipStatusDot.isVisible = true
                        binding.chipStatusDot.setBackgroundResource(R.drawable.bg_chip_dot_warn)
                        binding.chipStatusText.setText(R.string.status_connecting)
                        binding.chipStatusText.setTextColor(ContextCompat.getColor(context, R.color.material_orange_500))
                        binding.statusHeaderGlyph.imageTintList = ColorStateList.valueOf(accentColor)
                    }
                    else -> {
                        val secondaryColor = context.getColorAttr(android.R.attr.textColorSecondary)
                        binding.chipStatus.setBackgroundResource(R.drawable.bg_dashboard_chip_idle)
                        binding.chipStatusDot.isVisible = false
                        binding.chipStatusText.setText(R.string.status_not_running)
                        binding.chipStatusText.setTextColor(secondaryColor)
                        binding.statusHeaderGlyph.imageTintList = ColorStateList.valueOf(secondaryColor)
                    }
                }

                // 2. Exit Row: Country Badge
                if (isFetchingGeoIP) {
                    startBadgeSpin()
                } else {
                    stopBadgeSpin()
                }

                val countryCode = geoIpInfo.countryCode
                val flagDrawable = getCountryFlagDrawable(countryCode)
                if (flagDrawable != null) {
                    binding.iconCountryBadge.setImageResource(flagDrawable)
                    binding.iconCountryBadge.isVisible = true
                    binding.iconCountryFallback.isVisible = false
                    binding.textCountryCode.isVisible = false
                } else {
                    binding.iconCountryBadge.isVisible = false
                    binding.iconCountryFallback.isVisible = true
                    binding.textCountryCode.isVisible = false
                }

                // 3. Exit Row: Outbound IP / Node Headline
                if (isGeoIpHidden) {
                    binding.textOutboundIp.text = "•••.•••.•••.•••"
                } else if (geoIpInfo.ip.isNotEmpty() && geoIpInfo.ip != getString(R.string.unavailable)) {
                    binding.textOutboundIp.text = geoIpInfo.ip
                } else if (isFetchingGeoIP) {
                    binding.textOutboundIp.setText(R.string.querying)
                } else if (lastState == BaseService.State.Connected) {
                    binding.textOutboundIp.setText(R.string.service_failed)
                } else {
                    binding.textOutboundIp.text = "—"
                }

                // 4. Exit Row: IP Chips Row (Type + Quality)
                val isFailed = lastState == BaseService.State.Connected &&
                        !isFetchingGeoIP &&
                        (geoIpInfo.ip.isEmpty() || geoIpInfo.ip == getString(R.string.unavailable))

                if (isFailed) {
                    binding.chipNodeType.isVisible = false
                    binding.chipLatency.setText(R.string.tap_icon_to_retry)
                    binding.chipLatency.setTextColor(context.getColorAttr(android.R.attr.textColorSecondary))
                    binding.chipLatency.isVisible = true
                } else {
                    val resolvedType = geoIpInfo.ipType.ifEmpty {
                        if (snapshot.profileType.isNotEmpty()) snapshot.profileType else if (lastState == BaseService.State.Connected) "机房" else "住宅"
                    }
                    binding.chipNodeType.text = resolvedType
                    binding.chipNodeType.isVisible = true

                    val level = geoIpInfo.qualityLevel.ifEmpty { "良好" }
                    binding.chipLatency.text = "质量 $level"
                    binding.chipLatency.isVisible = true
                    when (level) {
                        "高风险", getString(R.string.ip_quality_risky) -> {
                            binding.chipLatency.setTextColor(0xFFFF3B30.toInt())
                        }
                        "良好", getString(R.string.ip_quality_good) -> {
                            binding.chipLatency.setTextColor(0xFF34C759.toInt())
                        }
                        else -> {
                            binding.chipLatency.setTextColor(accentColor)
                        }
                    }
                }

                // 5. Bottom Cells: 运行时长 & 出口组织
                if (lastState == BaseService.State.Connected && serviceConnectedTime > 0L) {
                    val seconds = ((SystemClock.elapsedRealtime() - serviceConnectedTime) / 1000L).coerceAtLeast(0L)
                    val hrs = seconds / 3600
                    val mins = (seconds % 3600) / 60
                    val secs = seconds % 60
                    binding.textCellRuntime.text = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
                } else {
                    binding.textCellRuntime.text = "—"
                }

                val orgInfo = geoIpInfo.organization.ifEmpty { geoIpInfo.isp }
                if (orgInfo.isNotEmpty()) {
                    val fullOrg = if (geoIpInfo.asn.isNotEmpty()) {
                        val asnPrefix = if (geoIpInfo.asn.startsWith("AS", ignoreCase = true)) geoIpInfo.asn else "AS${geoIpInfo.asn}"
                        "$orgInfo ($asnPrefix)"
                    } else {
                        orgInfo
                    }
                    binding.textCellOrg.text = fullOrg
                } else if (lastState == BaseService.State.Connected) {
                    binding.textCellOrg.text = profileName
                } else {
                    binding.textCellOrg.text = "—"
                }

                // 6. Right Start/Stop Toggle Button (Initial Liquid Glass -> Theme Accent Circle / cookie9Sided)
                val isConnected = lastState == BaseService.State.Connected
                val isConnecting = lastState == BaseService.State.Connecting
                if (isConnected || isConnecting) {
                    hasStartedOnce = true
                }
                val useAccentFill = hasStartedOnce

                binding.btnMorphToggle.updateThemeColors()
                binding.btnMorphToggle.setUseAccentFill(useAccentFill)
                binding.btnMorphToggle.setConnecting(isConnecting)
                binding.btnMorphToggle.setRunning(isConnected)
                binding.btnToggleProgress.isVisible = isConnecting
                binding.btnToggleProgress.indeterminateTintList = ColorStateList.valueOf(
                    if (useAccentFill) Color.WHITE else accentColor
                )

                binding.bgCookieView.updateColors()
                binding.bgCookieView.setRunning(isConnected)

                if (isConnected) {
                    if (isDashboardVisible) {
                        startBreathing()
                    } else {
                        stopBreathing()
                    }
                } else {
                    stopBreathing()
                }
            }
        }

        inner class SpeedViewHolder(val binding: ItemDashboardSpeedBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.SPEED, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        if (parentFragmentManager.findFragmentByTag("SpeedStatsSheet") != null) return@setOnClickListener
                        val sheet = SpeedStatsBottomSheet()
                        activeSpeedStatsSheet = sheet
                        sheet.updateStats(lastAppStatsList)
                        sheet.show(parentFragmentManager, "SpeedStatsSheet")
                    }
                } else {
                    binding.root.setOnClickListener(null)
                }
                updatePartial()
            }

            fun updatePartial() {
                val stats = lastTrafficStats
                val context = itemView.context
                binding.speedChart.setSpeedData(speedHistory, animate = true)

                if (stats != null) {
                    val rxSpeed = FormatFileSizeCompat.formatFileSize(context, stats.rxRateProxy, DataStore.useIECUnit)
                    val txSpeed = FormatFileSizeCompat.formatFileSize(context, stats.txRateProxy, DataStore.useIECUnit)
                    binding.downloadSpeed.text = context.getString(R.string.speed, rxSpeed)
                    binding.uploadSpeed.text = context.getString(R.string.speed, txSpeed)
                } else {
                    binding.downloadSpeed.setText(R.string.speed_zero)
                    binding.uploadSpeed.setText(R.string.speed_zero)
                }
            }
        }

        inner class TrafficViewHolder(val binding: ItemDashboardTrafficBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.TRAFFIC, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)
                updatePartial()
            }

            @SuppressLint("SetTextI18n")
            fun updatePartial() {
                val context = itemView.context
                val stats = lastTrafficStats

                val txSpeedStr = if (stats != null) FormatFileSizeCompat.formatFileSize(context, stats.txRateProxy, DataStore.useIECUnit) else "0 B"
                val rxSpeedStr = if (stats != null) FormatFileSizeCompat.formatFileSize(context, stats.rxRateProxy, DataStore.useIECUnit) else "0 B"

                binding.trafficUploadText.text = context.getString(R.string.speed, txSpeedStr)
                binding.trafficDownloadText.text = context.getString(R.string.speed, rxSpeedStr)

                val snapshot = dbSnapshot
                val sessionTx = lastTrafficStats?.txTotal ?: 0L
                val sessionRx = lastTrafficStats?.rxTotal ?: 0L

                val uploadBytes = (snapshot.groupTx + sessionTx).coerceAtLeast(0L)
                val downloadBytes = (snapshot.groupRx + sessionRx).coerceAtLeast(0L)

                if (snapshot.hasGroup && snapshot.subTotal > 0L) {
                    val used = snapshot.subUsed
                    val total = snapshot.subTotal
                    val usedStr = FormatFileSizeCompat.formatFileSize(context, used, DataStore.useIECUnit)
                    val totalStr = FormatFileSizeCompat.formatFileSize(context, total, DataStore.useIECUnit)
                    binding.trafficUsage.text = "已用: $usedStr / $totalStr"
                    val percent = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    binding.trafficProgress.progress = (percent * 100).toInt()
                    binding.trafficRingView.setTrafficData(
                        uploadBytes = uploadBytes,
                        downloadBytes = downloadBytes,
                        totalUsedBytes = used,
                        totalQuotaBytes = total,
                        animate = false
                    )
                    return
                }

                if (snapshot.hasGroup) {
                    val totalTraffic = uploadBytes + downloadBytes
                    val totalStr = FormatFileSizeCompat.formatFileSize(context, totalTraffic, DataStore.useIECUnit)
                    binding.trafficUsage.text = "总计使用: $totalStr"
                    binding.trafficProgress.progress = 100
                    binding.trafficRingView.setTrafficData(
                        uploadBytes = uploadBytes,
                        downloadBytes = downloadBytes,
                        totalUsedBytes = totalTraffic,
                        totalQuotaBytes = null,
                        animate = false
                    )
                } else {
                    binding.trafficUsage.setText(R.string.unavailable)
                    binding.trafficProgress.progress = 0
                    binding.trafficRingView.setTrafficData(0L, 0L, 0L, null, animate = false)
                }
            }
        }

        inner class GeoIpViewHolder(val binding: ItemDashboardGeoipBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.GEOIP, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (isGeoIpHidden) {
                            isGeoIpHidden = false
                            updatePartial()
                        } else {
                            fetchGeoIP()
                        }
                    }
                    binding.root.setOnLongClickListener {
                        val ip = geoIpInfo.ip
                        if (ip.isNotEmpty() && ip != getString(R.string.unavailable) && !isGeoIpHidden) {
                            binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            SagerNet.trySetPrimaryClip(ip)
                            (activity as? MainActivity)?.snackbar(getString(R.string.copied_ip))?.show()
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    binding.root.setOnClickListener(null)
                    binding.root.setOnLongClickListener(null)
                }
                updatePartial()
            }

            fun updatePartial() {
                if (isGeoIpHidden) {
                    binding.geoipIp.text = "* * * * * * * *"
                    binding.geoipLocation.text = "* * * * *"
                    binding.geoipIsp.text = "* * * * * * * *"
                    binding.geoipIsp.isVisible = true
                } else if (isFetchingGeoIP && geoIpInfo.ip.isEmpty()) {
                    binding.geoipIp.text = "获取中..."
                    binding.geoipLocation.text = "定位中..."
                    binding.geoipIsp.isVisible = false
                } else {
                    binding.geoipIp.text = geoIpInfo.ip.ifEmpty { getString(R.string.unavailable) }
                    val flag = countryCodeToEmoji(geoIpInfo.countryCode)
                    val loc = geoIpInfo.location.ifEmpty { getString(R.string.unavailable) }
                    binding.geoipLocation.text = if (flag.isNotEmpty()) "$flag  $loc" else loc
                    val ispInfo = listOf(geoIpInfo.asn, geoIpInfo.isp).filter { it.isNotEmpty() }.joinToString(" - ")
                    binding.geoipIsp.text = ispInfo
                    binding.geoipIsp.isVisible = ispInfo.isNotEmpty()
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
                    val fm = (itemView.context as FragmentActivity).supportFragmentManager
                    if (fm.findFragmentByTag("ServiceStatusSheet") != null) return@OnClickListener
                    val sheet = ServiceStatusBottomSheet(
                        latencyMap = latencyMap,
                        onTargetSelected = { target ->
                            val idx = SERVICE_TARGETS.indexOf(target)
                            if (idx != -1) selectIndex(idx, scroll = true)
                        },
                        onTestAllRequested = { testLatency(null) }
                    )
                    sheet.show(fm, "ServiceStatusSheet")
                }

                binding.cardServiceStatus.setOnClickListener(openSheetAction)
                binding.btnOpenDetail.setOnClickListener(openSheetAction)
                binding.latencyCardTitle.setOnClickListener(openSheetAction)

                binding.btnTestAll.setOnClickListener {
                    binding.btnTestAll.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    testLatency(null)
                }

                binding.summaryRow.setOnClickListener {
                    if (isEditMode) return@setOnClickListener
                    binding.summaryRow.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val target = SERVICE_TARGETS.getOrNull(selectedIndex)
                    if (target != null) testLatency(target)
                }
            }

            fun bind() {
                setupEditControls(ItemType.LATENCY, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)
                updatePartial()
            }

            fun updatePartial() {
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
                        val color = when {
                            latency < 150 -> ContextCompat.getColor(context, R.color.cupertino_green)
                            latency < 350 -> ContextCompat.getColor(context, R.color.material_amber_500)
                            else -> ContextCompat.getColor(context, R.color.material_orange_500)
                        }
                        binding.selectedServiceStatusPill.text = context.getString(R.string.service_available)
                        binding.selectedServiceStatusPill.setTextColor(color)
                        binding.selectedServiceLatency.text = context.getString(R.string.available, latency)
                        binding.selectedServiceLatency.setTextColor(color)
                    }
                    latency == -1 -> {
                        binding.selectedServiceStatusPill.text = context.getString(R.string.untested)
                        binding.selectedServiceStatusPill.setTextColor(ContextCompat.getColor(context, R.color.material_grey_500))
                        binding.selectedServiceLatency.text = "--"
                        binding.selectedServiceLatency.setTextColor(ContextCompat.getColor(context, R.color.material_grey_500))
                    }
                    else -> {
                        val redColor = ContextCompat.getColor(context, R.color.material_red_500)
                        binding.selectedServiceStatusPill.text = context.getString(R.string.unavailable)
                        binding.selectedServiceStatusPill.setTextColor(redColor)
                        binding.selectedServiceLatency.text = context.getString(R.string.unavailable)
                        binding.selectedServiceLatency.setTextColor(redColor)
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
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val link = ProfileManager.getProfile(DataStore.currentProfile)?.toLink()
                        withContext(Dispatchers.Main) {
                            if (!isAdded) return@withContext
                            if (link != null) {
                                QRCodeDialog(link).showAllowingStateLoss(parentFragmentManager)
                            } else {
                                (activity as? MainActivity)?.snackbar(getString(R.string.profile_empty))?.show()
                            }
                        }
                    }
                }
            }
        }

        inner class ProfilesViewHolder(val binding: ItemDashboardProfilesBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.PROFILES, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        if (parentFragmentManager.findFragmentByTag("ProfileNodeSheet") != null) return@setOnClickListener
                        val sheet = ProfileNodeBottomSheet {
                            refreshDbSnapshot()
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
                updatePartial()
            }

            fun updatePartial() {
                val snapshot = dbSnapshot
                if (snapshot.profileName != null) {
                    binding.profileTitle.text = snapshot.profileName
                    val groupName = snapshot.groupName
                    val type = snapshot.profileType
                    binding.profileGroupName.text = if (type.isNotEmpty()) "$groupName · $type" else groupName
                } else {
                    binding.profileTitle.setText(R.string.group_status_empty)
                    binding.profileGroupName.text = ""
                }
            }
        }

        inner class ConnectionsViewHolder(val binding: ItemDashboardConnectionsBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.CONNECTIONS, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        if (parentFragmentManager.findFragmentByTag("ConnectionsSheet") != null) return@setOnClickListener
                        val sheet = ConnectionsBottomSheet()
                        activeConnectionsSheet = sheet
                        sheet.updateStats(lastAppStatsList)
                        sheet.show(parentFragmentManager, "ConnectionsSheet")
                    }
                } else {
                    binding.root.setOnClickListener(null)
                }
                updatePartial()
            }

            @SuppressLint("SetTextI18n")
            fun updatePartial() {
                if (lastState != BaseService.State.Connected) {
                    binding.connectionCount.text = "0"
                    binding.connectionStatus.setText(R.string.disconnected)
                    return
                }
                if (!DataStore.appTrafficStatistics) {
                    binding.connectionCount.text = "--"
                    binding.connectionStatus.setText(R.string.app_statistics_disabled)
                    return
                }
                val activeTcp = lastAppStatsList.sumOf { it.tcpConnections }
                val activeUdp = lastAppStatsList.sumOf { it.udpConnections }
                val totalConn = activeTcp + activeUdp
                binding.connectionCount.text = totalConn.toString()
                binding.connectionStatus.text = if (totalConn > 0) {
                    "TCP: $activeTcp · UDP: $activeUdp"
                } else {
                    itemView.context.getString(R.string.connected)
                }
            }
        }

        inner class RunTimeViewHolder(val binding: ItemDashboardRuntimeBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.RUN_TIME, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)
                updatePartial()
            }

            fun updatePartial() {
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

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        if (parentFragmentManager.findFragmentByTag("DnsQueriesSheet") != null) return@setOnClickListener
                        val sheet = DnsQueriesBottomSheet {
                            handleRefreshDns()
                        }
                        sheet.show(parentFragmentManager, "DnsQueriesSheet")
                    }
                } else {
                    binding.root.setOnClickListener(null)
                }
                updatePartial()
            }

            fun updatePartial() {
                binding.dnsStatus.text = if (SagerNet.started) "点击查看 DNS 详情与实时解析" else "服务未启动 (点击查看配置)"
            }
        }

        inner class MemoryInfoViewHolder(val binding: ItemDashboardMemoryBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                setupEditControls(ItemType.MEMORY_INFO, itemView, binding.cardEditControls, binding.btnCardResize, binding.btnCardDelete)

                if (!isEditMode) {
                    binding.root.setOnClickListener {
                        if (parentFragmentManager.findFragmentByTag("MemoryStatsSheet") != null) return@setOnClickListener
                        val sheet = MemoryStatsBottomSheet {
                            adapter.updateItemWithPayload(ItemType.MEMORY_INFO, PAYLOAD_MEMORY)
                        }
                        sheet.show(parentFragmentManager, "MemoryStatsSheet")
                    }
                } else {
                    binding.root.setOnClickListener(null)
                }
                updatePartial()
            }

            @SuppressLint("SetTextI18n")
            fun updatePartial() {
                val runtime = Runtime.getRuntime()
                val usedMemMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val maxMemMB = runtime.maxMemory() / (1024 * 1024)

                val context = itemView.context
                binding.memoryText.text = context.getString(R.string.dashboard_traffic_total, "$usedMemMB MB").replace("/", "").trim()
                binding.memoryStatus.text = "JVM Heap $usedMemMB / $maxMemMB MB"
            }
        }
    }
}