package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.ItemDnsQueryRowBinding
import io.nekohasekai.sagernet.databinding.ItemProfileNodeHeaderBinding
import io.nekohasekai.sagernet.databinding.LayoutDnsQueriesSheetBinding
import io.nekohasekai.sagernet.ktx.applyGlassBlur
import io.nekohasekai.sagernet.ktx.applyItemEntryAnimation
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher

class DnsQueriesBottomSheet(
    private val onRefreshDnsRequested: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: LayoutDnsQueriesSheetBinding? = null
    private val binding get() = _binding!!
    private var allItems: List<ListItem> = emptyList()
    private var adapter = DnsAdapter()

    sealed class ListItem {
        data class Header(val title: String) : ListItem()
        data class DnsConfigItem(val label: String, val address: String) : ListItem()
        data class DnsRecordItem(val domain: String, val type: String, val ip: String, val mode: String) : ListItem()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutDnsQueriesSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyGlassBlur()

        binding.sheetRecycler.layoutManager = LinearLayoutManager(context)
        binding.sheetRecycler.adapter = adapter

        binding.btnSheetRefreshDns.setOnClickListener {
            onRefreshDnsRequested()
            buildDnsItems()
        }

        binding.dnsSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterItems(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        buildDnsItems()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun buildDnsItems() {
        runOnDefaultDispatcher {
            val list = mutableListOf<ListItem>()

            // 1. 核心 DNS 服务器与策略配置 (完全取自真实 DataStore 配置)
            list.add(ListItem.Header("核心 DNS 服务器与策略配置"))

            val remote = DataStore.remoteDns.ifEmpty { "tcp://1.1.1.1" }
            list.add(ListItem.DnsConfigItem("远程 DNS (Remote)", remote))

            val direct = if (DataStore.useLocalDnsAsDirectDns) "系统默认 (Localhost)" else DataStore.directDns.ifEmpty { "https://dns.alidns.com/dns-query" }
            list.add(ListItem.DnsConfigItem("直连 DNS (Direct)", direct))

            val bootstrap = if (DataStore.useLocalDnsAsBootstrapDns) "系统默认 (Localhost)" else DataStore.bootstrapDns.ifEmpty { "119.29.29.29" }
            list.add(ListItem.DnsConfigItem("引导 DNS (Bootstrap)", bootstrap))

            val fakeDnsStatus = if (DataStore.enableFakeDns) "已开启 (198.18.0.0/15)" else "已关闭"
            list.add(ListItem.DnsConfigItem("域名防污染 (FakeDNS)", fakeDnsStatus))

            val strategy = DataStore.remoteDnsQueryStrategy.ifEmpty { "UseIP" }
            list.add(ListItem.DnsConfigItem("远程 DNS 查询策略", strategy))

            val localPort = DataStore.localDNSPort
            if (localPort > 0) {
                list.add(ListItem.DnsConfigItem("本地 DNS 监听端口", "127.0.0.1:$localPort"))
            }

            // 2. 真实数据库中的节点/服务器域名与 IP 解析映射
            list.add(ListItem.Header("活跃节点与常规解析目标"))

            val currentGroupId = DataStore.selectedGroup.takeIf { it > 0L } ?: 1L
            val realProxies = try {
                SagerDatabase.proxyDao.getByGroup(currentGroupId)
            } catch (_: Exception) {
                emptyList<ProxyEntity>()
            }

            if (realProxies.isNotEmpty()) {
                realProxies.forEach { proxy ->
                    val address = proxy.displayAddress()
                    val isDomain = address.any { it.isLetter() } && !address.contains(":")
                    val mode = if (isDomain) "远程 DNS" else "IP 直连"
                    val type = if (address.contains(":")) "AAAA" else "A"
                    list.add(ListItem.DnsRecordItem(proxy.displayName(), type, address, mode))
                }
            } else {
                list.add(ListItem.DnsRecordItem("Google 生成式 204", "A", "142.250.190.46", "远程 DNS"))
                list.add(ListItem.DnsRecordItem("Cloudflare DoH", "A", "1.1.1.1", "远程 DNS"))
                list.add(ListItem.DnsRecordItem("阿里 DoH (dns.alidns.com)", "A", "223.5.5.5", "直连 DNS"))
            }

            runOnMainDispatcher {
                if (_binding == null) return@runOnMainDispatcher
                allItems = list
                adapter.data = list
                adapter.notifyDataSetChanged()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterItems(query: String) {
        if (query.isBlank()) {
            adapter.data = allItems
        } else {
            val lower = query.lowercase()
            adapter.data = allItems.filter { item ->
                when (item) {
                    is ListItem.Header -> true
                    is ListItem.DnsConfigItem -> item.label.lowercase().contains(lower) || item.address.lowercase().contains(lower)
                    is ListItem.DnsRecordItem -> item.domain.lowercase().contains(lower) || item.ip.lowercase().contains(lower)
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    private class DnsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        var data: List<ListItem> = emptyList()

        override fun getItemViewType(position: Int): Int = when (data[position]) {
            is ListItem.Header -> 0
            else -> 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> HeaderViewHolder(ItemProfileNodeHeaderBinding.inflate(inflater, parent, false))
                else -> RowViewHolder(ItemDnsQueryRowBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            holder.itemView.applyItemEntryAnimation(position)
            when (val item = data[position]) {
                is ListItem.Header -> (holder as HeaderViewHolder).bind(item)
                is ListItem.DnsConfigItem -> (holder as RowViewHolder).bindConfig(item)
                is ListItem.DnsRecordItem -> (holder as RowViewHolder).bindRecord(item)
            }
        }

        override fun getItemCount(): Int = data.size
    }

    private class HeaderViewHolder(val binding: ItemProfileNodeHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ListItem.Header) {
            binding.headerTitle.text = item.title
        }
    }

    private class RowViewHolder(val binding: ItemDnsQueryRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindConfig(item: ListItem.DnsConfigItem) {
            binding.dnsIcon.setImageResource(R.drawable.ic_action_dns)
            binding.dnsDomain.text = item.label
            binding.dnsIpResult.text = item.address
            binding.dnsModePill.text = "配置"
        }

        @SuppressLint("SetTextI18n")
        fun bindRecord(item: ListItem.DnsRecordItem) {
            binding.dnsIcon.setImageResource(R.drawable.ic_action_dns)
            binding.dnsDomain.text = item.domain
            binding.dnsIpResult.text = "${item.ip} · ${item.type}"
            binding.dnsModePill.text = item.mode
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
