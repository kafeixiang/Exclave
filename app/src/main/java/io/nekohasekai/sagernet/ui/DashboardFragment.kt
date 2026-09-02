package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.databinding.*
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.widget.QRCodeDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import kotlin.system.measureTimeMillis

class DashboardFragment : Fragment(R.layout.layout_dashboard) {

    private var recyclerView: RecyclerView? = null
    private lateinit var adapter: DashboardAdapter
    
    // 缓存状态数据，用于 RecyclerView 刷新
    private var lastState: BaseService.State = BaseService.State.Stopped
    private var lastProfileName: String? = null
    private var lastTrafficStats: TrafficStats? = null
    private var geoIpInfo = GeoIpData()
    private var latencyMap = mutableMapOf<String, Int>()

    data class GeoIpData(var ip: String = "", var location: String = "", var isp: String = "", var asn: String = "")

    enum class ItemType(val id: String) {
        STATUS("status"),
        SPEED("speed"),
        TRAFFIC("traffic"),
        GEOIP("geoip"),
        LATENCY("latency"),
        QUICK_TOOLS("quick_tools")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.dashboard_recycler)
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.applyGlassBlur()

        recyclerView?.let { rv ->
            ViewCompat.setOnApplyWindowInsetsListener(rv) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val baseBottomPx = v.resources.getDimensionPixelSize(R.dimen.main_list_padding_bottom)
                v.updatePadding(bottom = baseBottomPx + bars.bottom)
                insets
            }
        }

        adapter = DashboardAdapter()
        recyclerView?.adapter = adapter

        setupTouchHelper()

        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            lastState = mainActivity.state
            if (lastState == BaseService.State.Connected) {
                fetchGeoIP()
                testLatency()
            }
        }
        
        ProfileManager.addListener(object : ProfileManager.Listener {
            override suspend fun onAdd(profile: io.nekohasekai.sagernet.database.ProxyEntity) {}
            override suspend fun onUpdated(profileId: Long, trafficStats: TrafficStats) {}
            override suspend fun onUpdated(profile: io.nekohasekai.sagernet.database.ProxyEntity) {
                runOnMainDispatcher {
                    if (isAdded) {
                        adapter.updateItem(ItemType.TRAFFIC)
                        adapter.updateItem(ItemType.STATUS)
                    }
                }
            }
            override suspend fun onRemoved(groupId: Long, profileId: Long) {}
        })
    }

    private fun setupTouchHelper() {
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
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

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.animate()
                        .scaleX(1.03f)
                        .scaleY(1.03f)
                        .translationZ(12f)
                        .setDuration(150)
                        .start()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(150)
                    .start()
                // 保存排序结果
                DataStore.dashboardOrder = adapter.items.joinToString(",") { it.id }
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
    }

    private fun fetchGeoIP() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.ip.sb/geoip")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                geoIpInfo.ip = json.optString("ip", "Unknown")
                val country = json.optString("country", "")
                val city = json.optString("city", "")
                geoIpInfo.location = if (city.isNotEmpty()) "$country, $city" else country
                geoIpInfo.isp = json.optString("isp", "")
                val asn = json.optInt("asn", 0)
                geoIpInfo.asn = if (asn != 0) "AS$asn" else ""
                
                withContext(Dispatchers.Main) {
                    if (isAdded) adapter.updateItem(ItemType.GEOIP)
                }
            } catch (_: Exception) {
                geoIpInfo.ip = getString(R.string.unavailable)
                geoIpInfo.location = getString(R.string.unavailable)
                geoIpInfo.isp = ""
                geoIpInfo.asn = ""
                withContext(Dispatchers.Main) {
                    if (isAdded) adapter.updateItem(ItemType.GEOIP)
                }
            }
        }
    }

    private fun testLatency(targetHost: String? = null) {
        val targets = if (targetHost != null) listOf(targetHost) else listOf("www.google.com", "www.youtube.com", "github.com")
        targets.forEach { host ->
            latencyMap[host] = -2 // 测试中
            adapter.updateItem(ItemType.LATENCY)
            
            lifecycleScope.launch(Dispatchers.IO) {
                val time = try {
                    measureTimeMillis {
                        val connection = URL("https://$host").openConnection() as HttpURLConnection
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        connection.connect()
                        connection.disconnect()
                    }
                } catch (_: Exception) {
                    -1L
                }
                
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        latencyMap[host] = time.toInt()
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
            adapter.updateItem(ItemType.STATUS)
            if (state == BaseService.State.Connected) {
                fetchGeoIP()
                testLatency()
            }
        }
    }

    fun trafficUpdated(stats: TrafficStats) {
        runOnMainDispatcher {
            if (!isAdded) return@runOnMainDispatcher
            lastTrafficStats = stats
            adapter.updateItem(ItemType.SPEED)
            adapter.updateItem(ItemType.TRAFFIC)
        }
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
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateItem(type: ItemType) {
            val index = items.indexOf(type)
            if (index != -1) notifyItemChanged(index)
        }

        inner class StatusViewHolder(val binding: ItemDashboardStatusBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
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
                val stats = lastTrafficStats
                val context = itemView.context
                if (stats != null) {
                    val rxSpeed = FormatFileSizeCompat.formatFileSize(context, stats.rxRateProxy, DataStore.useIECUnit)
                    val txSpeed = FormatFileSizeCompat.formatFileSize(context, stats.txRateProxy, DataStore.useIECUnit)
                    // 使用标准的格式化方式，确保不出现未替换的占位符
                    binding.downloadSpeed.text = context.getString(R.string.speed, rxSpeed)
                    binding.uploadSpeed.text = context.getString(R.string.speed, txSpeed)
                    binding.speedChart.addDataPoint(stats.rxRateProxy + stats.txRateProxy)
                } else {
                    binding.downloadSpeed.setText(R.string.speed_zero)
                    binding.uploadSpeed.setText(R.string.speed_zero)
                }
            }
        }

        inner class TrafficViewHolder(val binding: ItemDashboardTrafficBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                val profile = ProfileManager.getProfile(DataStore.currentProfile) ?: return
                val group = SagerDatabase.groupDao.getById(profile.groupId) ?: return
                val sub = group.subscription

                if (sub != null) {
                    val used = sub.bytesUsed ?: 0L
                    val remaining = sub.bytesRemaining ?: 0L
                    val total = used + remaining
                    if (total > 0) {
                        binding.trafficUsage.text = FormatFileSizeCompat.formatFileSize(itemView.context, used, DataStore.useIECUnit)
                        binding.trafficTotal.text = getString(R.string.dashboard_traffic_total, FormatFileSizeCompat.formatFileSize(itemView.context, total, DataStore.useIECUnit))
                        binding.trafficProgress.progress = ((used * 100) / total).toInt()
                        return
                    }
                }
                
                // 非订阅或无配额，显示分组总流量
                val context = itemView.context
                val profiles = SagerDatabase.proxyDao.getByGroup(group.id)
                val groupTx = profiles.sumOf { it.tx }
                val groupRx = profiles.sumOf { it.rx }
                val totalTraffic = groupTx + groupRx
                if (totalTraffic > 0) {
                    binding.trafficUsage.text = FormatFileSizeCompat.formatFileSize(context, totalTraffic, DataStore.useIECUnit)
                    binding.trafficTotal.text = group.displayName()
                    binding.trafficProgress.progress = 100
                } else {
                    binding.trafficUsage.setText(R.string.unavailable)
                    binding.trafficTotal.text = ""
                    binding.trafficProgress.progress = 0
                }
            }
        }

        inner class GeoIpViewHolder(val binding: ItemDashboardGeoipBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                binding.geoipIp.text = geoIpInfo.ip.ifEmpty { getString(R.string.unavailable) }
                binding.geoipLocation.text = geoIpInfo.location.ifEmpty { getString(R.string.unavailable) }
                val ispInfo = listOf(geoIpInfo.asn, geoIpInfo.isp).filter { it.isNotEmpty() }.joinToString(" - ")
                binding.geoipIsp.text = ispInfo
                binding.geoipIsp.isVisible = ispInfo.isNotEmpty()
                binding.root.setOnClickListener { fetchGeoIP() }
            }
        }

        inner class LatencyViewHolder(val binding: ItemDashboardLatencyBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                binding.latencyCardTitle.setOnClickListener { testLatency() }
                
                setupItem(binding.latencyGoogle, "www.google.com")
                setupItem(binding.latencyYoutube, "www.youtube.com")
                setupItem(binding.latencyGithub, "github.com")
            }
            
            private fun setupItem(textView: android.widget.TextView, host: String) {
                val latency = latencyMap[host] ?: -1
                when {
                    latency == -2 -> {
                        textView.setText(R.string.connecting)
                        textView.setTextColor(ContextCompat.getColor(itemView.context, R.color.material_grey_500))
                    }
                    latency > 0 -> {
                        textView.text = getString(R.string.available, latency)
                        textView.setTextColor(ContextCompat.getColor(itemView.context, R.color.cupertino_green))
                    }
                    else -> {
                        textView.setText(R.string.unavailable)
                        textView.setTextColor(ContextCompat.getColor(itemView.context, R.color.material_red_500))
                    }
                }
                textView.setOnClickListener { testLatency(host) }
            }
        }

        inner class QuickToolsViewHolder(val binding: ItemDashboardQuickToolsBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind() {
                binding.btnImportSub.setOnClickListener {
                    (requireActivity() as MainActivity).displayFragmentWithId(R.id.nav_group)
                }
                binding.btnRefreshDns.setOnClickListener {
                    (requireActivity() as MainActivity).displayFragmentWithId(R.id.nav_tools)
                }
                binding.btnShareConfig.setOnClickListener {
                    val profile = ProfileManager.getProfile(DataStore.currentProfile)
                    profile?.toLink()?.let { link ->
                        QRCodeDialog(link).showAllowingStateLoss(parentFragmentManager)
                    } ?: run {
                        (requireActivity() as MainActivity).snackbar(getString(R.string.profile_empty)).show()
                    }
                }
            }
        }
    }
}
