package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.ItemSpeedStatRowBinding
import io.nekohasekai.sagernet.databinding.LayoutSpeedStatsSheetBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.utils.WallpaperManager

class SpeedStatsBottomSheet : BottomSheetDialogFragment() {

    enum class SortOption {
        ALL, SPEED, CONNS
    }

    private var _binding: LayoutSpeedStatsSheetBinding? = null
    private val binding get() = _binding!!
    private val lifecycleOwnerOrNull: () -> LifecycleOwner? = {
        if (_binding != null && isAdded) viewLifecycleOwner else null
    }
    private val adapter = SpeedStatsAdapter(lifecycleOwnerOrNull)
    private var rawStatsList: List<AppStats> = emptyList()
    private var currentSort = SortOption.ALL

    private val statsUpdater = AppStatsListUpdater(
        lifecycleOwnerProvider = lifecycleOwnerOrNull,
        currentDataProvider = { adapter.data },
        applyResult = { sortedList, diffResult ->
            val b = _binding ?: return@AppStatsListUpdater
            val isEmpty = sortedList.isEmpty()
            if (b.emptyText.isVisible != isEmpty) {
                b.emptyText.isVisible = isEmpty
            }
            if (b.sheetRecycler.isVisible == isEmpty) {
                b.sheetRecycler.isVisible = !isEmpty
            }
            val targetHeight = when {
                isEmpty -> dp2px(140)
                sortedList.size <= 4 -> ViewGroup.LayoutParams.WRAP_CONTENT
                else -> dp2px(360)
            }
            if (b.recyclerContainer.layoutParams.height != targetHeight) {
                b.recyclerContainer.layoutParams = b.recyclerContainer.layoutParams.apply {
                    height = targetHeight
                }
            }
            val context = context ?: return@AppStatsListUpdater
            val totalUp = sortedList.sumOf { it.uplinkTotal }
            val totalDown = sortedList.sumOf { it.downlinkTotal }
            val maxTraffic = sortedList.maxOfOrNull { it.uplinkTotal + it.downlinkTotal } ?: 0L

            b.badgeActiveCount.text = "${sortedList.size} 应用"
            b.textHudUp.text = FormatFileSizeCompat.formatFileSize(context, totalUp, DataStore.useIECUnit)
            b.textHudDown.text = FormatFileSizeCompat.formatFileSize(context, totalDown, DataStore.useIECUnit)

            adapter.updateData(sortedList, maxTraffic, diffResult)
        }
    )

    override fun getTheme(): Int = R.style.ThemeOverlay_SagerNet_BottomSheetDialog_Transparent

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutSpeedStatsSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyThemeStyles()

        binding.btnFilterAll.setOnClickListener { setSort(SortOption.ALL) }
        binding.btnFilterSpeed.setOnClickListener { setSort(SortOption.SPEED) }
        binding.btnFilterConns.setOnClickListener { setSort(SortOption.CONNS) }
        updateFilterTabStyles()

        binding.sheetRecycler.layoutManager = LinearLayoutManager(context)
        // Disabling item animator prevents card overlapping during real-time 1s diff updates
        binding.sheetRecycler.itemAnimator = null
        binding.sheetRecycler.adapter = adapter

        processAndDisplayStats(rawStatsList)
    }

    override fun onStart() {
        super.onStart()
        // Eliminate sharp right angles by clearing any backgrounds on the container frame and window
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Theme.isCupertino()) {
            dialog?.window?.setDimAmount(0.28f)
        }
        (dialog as? BottomSheetDialog)?.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.let { bottomSheet ->
            bottomSheet.background = null
            bottomSheet.setBackgroundColor(Color.TRANSPARENT)
            bottomSheet.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            androidx.core.view.ViewCompat.setBackground(bottomSheet, null)
            androidx.core.view.ViewCompat.setBackgroundTintList(bottomSheet, ColorStateList.valueOf(Color.TRANSPARENT))

            // Configure BottomSheetBehavior to prevent expanding to a full-screen void
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED

            // Constrain max height to 72% of screen height so sheet can NEVER pull to full screen
            val maxH = (resources.displayMetrics.heightPixels * 0.72f).toInt()
            behavior.maxHeight = maxH

            // Ensure design_bottom_sheet does not stretch to MATCH_PARENT
            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        (view?.parent as? View)?.let { parent ->
            parent.background = null
            parent.setBackgroundColor(Color.TRANSPARENT)
        }

        (activity as? MainActivity)?.updateTrafficTimeout()
    }

    override fun onStop() {
        super.onStop()
        (activity as? MainActivity)?.updateTrafficTimeout()
    }

    fun updateStats(statsList: List<AppStats>) {
        runOnMainDispatcher {
            rawStatsList = statsList
            if (_binding != null && isAdded) {
                processAndDisplayStats(statsList)
            }
        }
    }

    private fun setSort(option: SortOption) {
        if (currentSort == option) return
        currentSort = option
        updateFilterTabStyles()
        processAndDisplayStats(rawStatsList)
    }

    private fun applyThemeStyles() {
        val b = _binding ?: return
        val context = context ?: return
        val isCupertino = Theme.isCupertino()
        val isNight = Theme.usingNightMode()
        val isPureBlack = isNight && DataStore.amoledPureBlack
        val accent = Theme.getAccentColor(context)
        val topRadius = dp2px(28).toFloat()

        // 1. Root container outline clipping (guarantees top 28dp corners with zero leakage)
        b.root.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height + topRadius.toInt(), topRadius)
            }
        }
        b.root.clipToOutline = true

        // Keep background container height strictly synchronized with content container height
        b.sheetContentContainer.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val h = bottom - top
            if (h > 0 && b.sheetBackgroundContainer.layoutParams.height != h) {
                b.sheetBackgroundContainer.layoutParams = b.sheetBackgroundContainer.layoutParams.apply {
                    height = h
                }
            }
        }
        val currentContentH = b.sheetContentContainer.height
        if (currentContentH > 0 && b.sheetBackgroundContainer.layoutParams.height != currentContentH) {
            b.sheetBackgroundContainer.layoutParams = b.sheetBackgroundContainer.layoutParams.apply {
                height = currentContentH
            }
        }

        if (isCupertino) {
            // Cupertino Gaussian Blur & Ambient Accent Glow Background
            b.sheetBackgroundContainer.isVisible = true

            // Base window tint behind mesh
            val windowBgColor = if (isNight) {
                if (isPureBlack && !Theme.isWallpaperTheme()) Color.BLACK else Color.parseColor("#121216")
            } else {
                ContextCompat.getColor(context, R.color.cupertino_window_bg)
            }
            b.sheetBackgroundContainer.setBackgroundColor(windowBgColor)

            // Mesh / Wallpaper layer with Gaussian blur
            if (Theme.isWallpaperTheme()) {
                val wallpaper = WallpaperManager.loadWallpaperDrawable(context, DataStore.currentWallpaperPath)
                b.sheetBgMesh.isVisible = true
                b.sheetBgMesh.setImageDrawable(wallpaper ?: ContextCompat.getDrawable(context, R.drawable.bg_mesh_gradient))
                b.sheetBgMesh.alpha = if (isNight) 0.70f else 0.88f
            } else if (isPureBlack) {
                b.sheetBgMesh.isVisible = false
                b.sheetBgMesh.setImageDrawable(null)
            } else {
                b.sheetBgMesh.isVisible = true
                b.sheetBgMesh.setImageResource(R.drawable.bg_mesh_gradient)
                b.sheetBgMesh.alpha = if (isNight) 0.65f else 0.85f
            }

            val blurRadius = if (Theme.isWallpaperTheme()) {
                DataStore.wallpaperBlurRadius.toFloat().coerceIn(30f, 80f)
            } else {
                45f
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                b.sheetBgMesh.setRenderEffect(
                    if (b.sheetBgMesh.isVisible) RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP) else null
                )
            }

            // Ambient Theme Glow layer
            if (isPureBlack && !Theme.isWallpaperTheme()) {
                b.sheetAmbientGlow.isVisible = false
            } else {
                b.sheetAmbientGlow.isVisible = true
                b.sheetAmbientGlow.backgroundTintList = ColorStateList.valueOf(accent)
                b.sheetAmbientGlow.alpha = if (isNight) 0.22f else 0.28f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    b.sheetAmbientGlow.setRenderEffect(
                        RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.DECAL)
                    )
                }
            }

            // Translucent Frosted Glass Tint
            val baseTint = if (isNight) {
                if (isPureBlack && !Theme.isWallpaperTheme()) Color.argb(200, 10, 10, 12) else Color.argb(165, 18, 18, 24)
            } else {
                Color.argb(135, 242, 244, 248)
            }
            val accentHint = ColorUtils.setAlphaComponent(accent, if (isNight) 25 else 18)
            val finalFrostedColor = ColorUtils.compositeColors(accentHint, baseTint)
            b.sheetFrostedTint.isVisible = true
            b.sheetFrostedTint.setBackgroundColor(finalFrostedColor)

            // Specular border on top edge
            val rimStrokeColor = if (isNight) Color.argb(45, 255, 255, 255) else Color.argb(80, 255, 255, 255)
            b.root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    0f, 0f,
                    0f, 0f
                )
                setColor(Color.TRANSPARENT)
                setStroke(dp2px(1), rimStrokeColor)
            }
        } else {
            // Classic Material Surface Background
            b.sheetBackgroundContainer.isVisible = false
            b.sheetBgMesh.isVisible = false
            b.sheetAmbientGlow.isVisible = false
            b.sheetFrostedTint.isVisible = false
            b.root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    0f, 0f,
                    0f, 0f
                )
                setColor(Theme.getClassicListBackgroundColor())
            }
        }

        // 2. HUD Cards
        applyCardThemeStyle(b.cardHudUp)
        applyCardThemeStyle(b.cardHudDown)

        // 3. Segmented control background
        val segmentBgColor = if (isCupertino) {
            val base = if (isNight) Color.argb(140, 32, 32, 38) else Color.argb(140, 222, 224, 230)
            ColorUtils.compositeColors(ColorUtils.setAlphaComponent(accent, if (isNight) 20 else 14), base)
        } else {
            if (isNight) Color.rgb(36, 36, 40) else Color.rgb(230, 230, 235)
        }
        b.layoutFilterSegment.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(11).toFloat()
            setColor(segmentBgColor)
        }
    }

    private fun updateFilterTabStyles() {
        val b = _binding ?: return
        val context = context ?: return

        fun makeSelectedTabDrawable(): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(9).toFloat()
                setColor(Theme.getCardBackgroundColor(context, false))
                setStroke(dp2px(1), Theme.getCardStrokeColor(context, false))
            }
        }

        fun makeUnselectedTabDrawable(): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(9).toFloat()
                setColor(Color.TRANSPARENT)
            }
        }

        val textColorPrimary = context.getColorAttr(android.R.attr.textColorPrimary)
        val textColorSecondary = context.getColorAttr(android.R.attr.textColorSecondary)

        b.btnFilterAll.background = if (currentSort == SortOption.ALL) makeSelectedTabDrawable() else makeUnselectedTabDrawable()
        b.btnFilterAll.setTextColor(if (currentSort == SortOption.ALL) textColorPrimary else textColorSecondary)

        b.btnFilterSpeed.background = if (currentSort == SortOption.SPEED) makeSelectedTabDrawable() else makeUnselectedTabDrawable()
        b.btnFilterSpeed.setTextColor(if (currentSort == SortOption.SPEED) textColorPrimary else textColorSecondary)

        b.btnFilterConns.background = if (currentSort == SortOption.CONNS) makeSelectedTabDrawable() else makeUnselectedTabDrawable()
        b.btnFilterConns.setTextColor(if (currentSort == SortOption.CONNS) textColorPrimary else textColorSecondary)
    }

    private fun processAndDisplayStats(statsList: List<AppStats>) {
        val snapshot = statsList.toList()
        statsUpdater.submit(
            computeList = {
                val merged = LinkedHashMap<Int, AppStats>(snapshot.size)
                for (entry in snapshot) {
                    val existing = merged[entry.uid]
                    if (existing != null) {
                        existing.tcpConnections += entry.tcpConnections
                        existing.udpConnections += entry.udpConnections
                        existing.uplink += entry.uplink
                        existing.downlink += entry.downlink
                        existing.uplinkTotal += entry.uplinkTotal
                        existing.downlinkTotal += entry.downlinkTotal
                    } else {
                        merged[entry.uid] = entry.copy()
                    }
                }
                when (currentSort) {
                    SortOption.ALL -> {
                        merged.values.sortedWith(
                            compareByDescending<AppStats> { it.uplinkTotal + it.downlinkTotal }
                                .thenByDescending { it.tcpConnections + it.udpConnections }
                                .thenBy { it.uid }
                        )
                    }
                    SortOption.SPEED -> {
                        merged.values.sortedWith(
                            compareByDescending<AppStats> { it.uplinkTotal + it.downlinkTotal }
                                .thenBy { it.uid }
                        )
                    }
                    SortOption.CONNS -> {
                        merged.values.sortedWith(
                            compareByDescending<AppStats> { it.tcpConnections + it.udpConnections }
                                .thenBy { it.uid }
                        )
                    }
                }
            },
            diffCallbackFactory = { oldList, newList -> SpeedStatsDiffCallback(oldList, newList) }
        )
    }

    private class SpeedStatsDiffCallback(
        private val oldList: List<AppStats>,
        private val newList: List<AppStats>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].uid == newList[newItemPosition].uid
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldList[oldItemPosition]
            val new = newList[newItemPosition]
            return old.tcpConnections == new.tcpConnections &&
                    old.udpConnections == new.udpConnections &&
                    old.uplink == new.uplink &&
                    old.downlink == new.downlink &&
                    old.uplinkTotal == new.uplinkTotal &&
                    old.downlinkTotal == new.downlinkTotal
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any {
            return PAYLOAD_SPEED_CHANGE
        }
    }

    private class SpeedStatsAdapter(
        private val lifecycleOwnerProvider: () -> LifecycleOwner?
    ) : RecyclerView.Adapter<SpeedStatsAdapter.ViewHolder>() {

        var data: List<AppStats> = emptyList()
            private set
        var maxTraffic: Long = 0L
            private set

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return data.getOrNull(position)?.uid?.toLong() ?: RecyclerView.NO_ID
        }

        fun updateData(newList: List<AppStats>, newMaxTraffic: Long, diffResult: DiffUtil.DiffResult?) {
            data = newList
            maxTraffic = newMaxTraffic
            if (diffResult != null) {
                diffResult.dispatchUpdatesTo(this)
            } else {
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemSpeedStatRowBinding.inflate(inflater, parent, false)
            applyCardThemeStyle(binding.root as MaterialCardView)
            return ViewHolder(binding, lifecycleOwnerProvider)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isNotEmpty()) {
                holder.updateStatsOnly(data[position], maxTraffic)
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            // Cancel any residual animations and reset transforms to guarantee zero card overlap
            holder.itemView.animate().cancel()
            holder.itemView.translationY = 0f
            holder.itemView.alpha = 1f

            val stats = data[position]
            holder.bind(stats, maxTraffic)
        }

        override fun getItemCount(): Int = data.size

        class ViewHolder(
            val binding: ItemSpeedStatRowBinding,
            private val lifecycleOwnerProvider: () -> LifecycleOwner?,
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(stats: AppStats, maxTraffic: Long) {
                val packageName = PackageCache.resolvePackageName(stats.uid)
                val label = if (packageName != null) PackageCache.loadLabel(packageName) else "UID ${stats.uid}"

                binding.appName.text = label
                binding.appIcon.bindAppIcon(
                    lifecycleOwnerProvider = lifecycleOwnerProvider,
                    uid = stats.uid,
                    packageName = packageName,
                    fallbackRes = R.drawable.ic_baseline_compare_arrows_24,
                    fallbackTintRes = R.color.material_grey_500,
                )

                updateStatsOnly(stats, maxTraffic)
            }

            @SuppressLint("SetTextI18n")
            fun updateStatsOnly(stats: AppStats, maxTraffic: Long) {
                val context = itemView.context
                val upTotal = FormatFileSizeCompat.formatFileSize(context, stats.uplinkTotal, DataStore.useIECUnit)
                val downTotal = FormatFileSizeCompat.formatFileSize(context, stats.downlinkTotal, DataStore.useIECUnit)
                binding.uploadTotal.text = "↑ $upTotal"
                binding.downloadTotal.text = "↓ $downTotal"

                binding.badgeTcp.text = "TCP ${stats.tcpConnections}"
                binding.badgeUdp.text = "UDP ${stats.udpConnections}"

                val totalTraffic = stats.uplinkTotal + stats.downlinkTotal
                if (maxTraffic > 0 && totalTraffic > 0) {
                    val pct = ((totalTraffic * 100) / maxTraffic).toInt().coerceIn(1, 100)
                    binding.trafficProgress.progress = pct
                    binding.trafficProgress.isVisible = true
                } else {
                    binding.trafficProgress.isVisible = false
                }
            }
        }
    }

    override fun onDestroyView() {
        statsUpdater.cancel()
        _binding?.sheetRecycler?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PAYLOAD_SPEED_CHANGE = "payload_speed_change"

        fun applyCardThemeStyle(card: MaterialCardView) {
            val ctx = card.context
            card.setCardBackgroundColor(Theme.getCardBackgroundColor(ctx, false))
            card.strokeColor = Theme.getCardStrokeColor(ctx, false)
            card.strokeWidth = dp2px(1)
            card.radius = dp2px(16).toFloat()
            card.cardElevation = 0f
            card.maxCardElevation = 0f
            card.preventCornerOverlap = false
            card.useCompatPadding = false
        }
    }
}
