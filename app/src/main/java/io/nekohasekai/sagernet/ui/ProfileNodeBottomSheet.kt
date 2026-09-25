package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.ItemProfileNodeHeaderBinding
import io.nekohasekai.sagernet.databinding.ItemProfileNodeRowBinding
import io.nekohasekai.sagernet.databinding.LayoutProfileNodeSheetBinding
import io.nekohasekai.sagernet.ktx.applyGlassBlur
import io.nekohasekai.sagernet.ktx.applyItemEntryAnimation
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher

class ProfileNodeBottomSheet(
    private val onSelectedChanged: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: LayoutProfileNodeSheetBinding? = null
    private val binding get() = _binding!!

    sealed class ListItem {
        data class Header(val title: String) : ListItem()
        data class GroupItem(val group: ProxyGroup, val isSelected: Boolean) : ListItem()
        data class ProxyItem(val proxy: ProxyEntity, val isSelected: Boolean) : ListItem()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutProfileNodeSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyGlassBlur()

        binding.sheetRecycler.layoutManager = LinearLayoutManager(context)

        binding.btnSheetUrlTest.setOnClickListener {
            (activity as? MainActivity)?.urlTest()
            loadData()
        }

        loadData()
    }

    private fun loadData() {
        runOnDefaultDispatcher {
            val groups = SagerDatabase.groupDao.allGroups()
            val currentGroupId = DataStore.selectedGroup.takeIf { it > 0L } ?: groups.firstOrNull()?.id ?: 0L
            val proxies = if (currentGroupId > 0L) SagerDatabase.proxyDao.getByGroup(currentGroupId) else emptyList()

            val selectedProxyId = DataStore.selectedProxy

            val items = mutableListOf<ListItem>()

            // 1. 分类一：配置 (Profiles/Groups)
            items.add(ListItem.Header(getString(R.string.menu_group)))
            groups.forEach { group ->
                items.add(ListItem.GroupItem(group, group.id == currentGroupId))
            }

            // 2. 分类二：节点 (Proxies/Nodes)
            items.add(ListItem.Header(getString(R.string.menu_configuration)))
            proxies.forEach { proxy ->
                items.add(ListItem.ProxyItem(proxy, proxy.id == selectedProxyId))
            }

            runOnMainDispatcher {
                if (_binding != null) {
                    binding.sheetRecycler.adapter = SheetAdapter(items)
                }
            }
        }
    }

    private inner class SheetAdapter(private val list: List<ListItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = when (list[position]) {
            is ListItem.Header -> 0
            is ListItem.GroupItem -> 1
            is ListItem.ProxyItem -> 2
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> HeaderViewHolder(ItemProfileNodeHeaderBinding.inflate(inflater, parent, false))
                else -> RowViewHolder(ItemProfileNodeRowBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            holder.itemView.applyItemEntryAnimation(position)
            when (val item = list[position]) {
                is ListItem.Header -> (holder as HeaderViewHolder).bind(item)
                is ListItem.GroupItem -> (holder as RowViewHolder).bindGroup(item)
                is ListItem.ProxyItem -> (holder as RowViewHolder).bindProxy(item)
            }
        }

        override fun getItemCount(): Int = list.size
    }

    private class HeaderViewHolder(val binding: ItemProfileNodeHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ListItem.Header) {
            binding.headerTitle.text = item.title
        }
    }

    private inner class RowViewHolder(val binding: ItemProfileNodeRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindGroup(item: ListItem.GroupItem) {
            binding.itemIcon.setImageResource(R.drawable.ic_baseline_view_list_24)
            binding.itemTitle.text = item.group.displayName()
            binding.itemTypePill.isVisible = false
            binding.itemPingPill.isVisible = false
            binding.itemCheck.isVisible = item.isSelected

            binding.root.setOnClickListener {
                DataStore.selectedGroup = item.group.id
                loadData()
            }
        }

        fun bindProxy(item: ListItem.ProxyItem) {
            val context = itemView.context
            binding.itemIcon.setImageResource(R.drawable.ic_baseline_vpn_key_24)
            binding.itemTitle.text = item.proxy.displayName()

            binding.itemTypePill.isVisible = true
            binding.itemTypePill.text = item.proxy.displayType()

            binding.itemPingPill.isVisible = true
            if (item.proxy.ping > 0) {
                binding.itemPingPill.text = context.getString(R.string.available, item.proxy.ping)
                binding.itemPingPill.setTextColor(ContextCompat.getColor(context, R.color.cupertino_green))
            } else {
                binding.itemPingPill.text = context.getString(R.string.unavailable)
                binding.itemPingPill.setTextColor(ContextCompat.getColor(context, R.color.material_red_500))
            }

            binding.itemCheck.isVisible = item.isSelected

            binding.root.setOnClickListener {
                DataStore.selectedProxy = item.proxy.id
                if (SagerNet.started) {
                    SagerNet.reloadService()
                }
                onSelectedChanged()
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
