package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.ItemProfileNodeRowBinding
import io.nekohasekai.sagernet.databinding.LayoutProfileNodeSheetBinding
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.ktx.applyItemEntryAnimation
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.utils.WallpaperManager

class ProfileNodeBottomSheet : BottomSheetDialogFragment {

    private var onSelectedChanged: (() -> Unit)? = null

    constructor() : super()

    constructor(onSelectedChanged: () -> Unit) : super() {
        this.onSelectedChanged = onSelectedChanged
    }

    private var _binding: LayoutProfileNodeSheetBinding? = null
    private val binding get() = _binding!!

    private var currentFilterGroupId: Long = -999L
    private var allGroups: List<ProxyGroup> = emptyList()

    private fun dp2px(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    private fun dp2px(dp: Float): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    override fun getTheme(): Int = R.style.ThemeOverlay_SagerNet_BottomSheetDialog_Transparent

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

        applyThemeStyles()

        binding.sheetRecycler.layoutManager = LinearLayoutManager(context)
        binding.sheetRecycler.itemAnimator = null

        binding.btnSheetUrlTest.setOnClickListener {
            val mainActivity = activity as? MainActivity ?: return@setOnClickListener
            if (mainActivity.state != BaseService.State.Connected) {
                mainActivity.snackbar(getString(R.string.not_connected)).show()
                return@setOnClickListener
            }
            binding.btnSheetUrlTest.isEnabled = false
            runOnDefaultDispatcher {
                try {
                    val elapsed = mainActivity.urlTest()
                    runOnMainDispatcher {
                        if (elapsed > 0) {
                            mainActivity.snackbar(getString(R.string.connection_test_available, elapsed)).show()
                        } else {
                            mainActivity.snackbar(getString(R.string.connection_test_error, getString(R.string.connection_test_timeout))).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnMainDispatcher {
                        mainActivity.snackbar(getString(R.string.connection_test_error, e.message ?: e.toString())).show()
                    }
                } finally {
                    runOnMainDispatcher {
                        if (_binding != null) {
                            binding.btnSheetUrlTest.isEnabled = true
                        }
                    }
                    loadData()
                }
            }
        }

        loadData()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Theme.isCupertino()) {
                setDimAmount(0.28f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    attributes = attributes.apply { blurBehindRadius = 80 }
                }
            }
        }

        (dialog as? BottomSheetDialog)?.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.let { bottomSheet ->
            bottomSheet.background = null
            bottomSheet.setBackgroundColor(Color.TRANSPARENT)
            bottomSheet.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            androidx.core.view.ViewCompat.setBackground(bottomSheet, null)
            androidx.core.view.ViewCompat.setBackgroundTintList(bottomSheet, ColorStateList.valueOf(Color.TRANSPARENT))

            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED

            val maxH = (resources.displayMetrics.heightPixels * 0.72f).toInt()
            behavior.maxHeight = maxH

            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        (view?.parent as? View)?.let { parent ->
            parent.background = null
            parent.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun applyThemeStyles() {
        val b = _binding ?: return
        val context = context ?: return
        val isCupertino = Theme.isCupertino()
        val isNight = Theme.usingNightMode()
        val isPureBlack = isNight && DataStore.amoledPureBlack
        val accent = Theme.getAccentColor(context)
        val topRadius = dp2px(28).toFloat()

        // 1. Root outline clipping with 28dp top corners
        b.root.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height + topRadius.toInt(), topRadius)
            }
        }
        b.root.clipToOutline = true

        // Keep background container height strictly synchronized with content container height
        b.sheetContentContainer.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val h = bottom - top
            if (h > 0 && (h != (oldBottom - oldTop) || b.sheetBackgroundContainer.height != h)) {
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

            // Translucent base window tint behind mesh (allows background blur to diffuse through)
            val windowBgColor = if (isNight) {
                if (isPureBlack && !Theme.isWallpaperTheme()) Color.argb(220, 8, 8, 10) else Color.argb(185, 20, 20, 26)
            } else {
                Color.argb(190, 246, 247, 251)
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
                b.sheetBgMesh.alpha = if (isNight) 0.65f else 0.80f
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

            // Ambient Theme Glow layer with accent color tint
            if (isPureBlack && !Theme.isWallpaperTheme()) {
                b.sheetAmbientGlow.isVisible = false
            } else {
                b.sheetAmbientGlow.isVisible = true
                b.sheetAmbientGlow.backgroundTintList = ColorStateList.valueOf(accent)
                b.sheetAmbientGlow.alpha = if (isNight) 0.32f else 0.42f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    b.sheetAmbientGlow.setRenderEffect(
                        RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.DECAL)
                    )
                }
            }

            // Translucent Frosted Glass Tint
            val baseTint = if (isNight) {
                if (isPureBlack && !Theme.isWallpaperTheme()) Color.argb(180, 10, 10, 12) else Color.argb(150, 18, 18, 24)
            } else {
                Color.argb(115, 245, 246, 250)
            }
            val accentHint = ColorUtils.setAlphaComponent(accent, if (isNight) 30 else 24)
            val finalFrostedColor = ColorUtils.compositeColors(accentHint, baseTint)
            b.sheetFrostedTint.isVisible = true
            b.sheetFrostedTint.setBackgroundColor(finalFrostedColor)

            // Specular border on top edge
            val rimStrokeColor = if (isNight) Color.argb(55, 255, 255, 255) else Color.argb(110, 255, 255, 255)
            b.root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    0f, 0f,
                    0f, 0f
                )
                setStroke(dp2px(1), rimStrokeColor)
                setColor(Color.TRANSPARENT)
            }
        } else {
            // Classic non-Cupertino theme fallback
            b.sheetBackgroundContainer.isVisible = false
            val classicBg = if (isNight) {
                if (isPureBlack) Color.BLACK else Color.parseColor("#1C1C1E")
            } else {
                Color.WHITE
            }
            b.root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    0f, 0f,
                    0f, 0f
                )
                setColor(classicBg)
            }
        }

        // Refined Frosted Glass "全部测速" button (Capsule border & accent tint)
        b.btnSheetUrlTest.strokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, if (isNight) 160 else 180))
        b.btnSheetUrlTest.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, if (isNight) 36 else 22))
        b.btnSheetUrlTest.setTextColor(accent)
    }

    private fun loadData() {
        val ctx = context ?: return
        runOnDefaultDispatcher {
            val groups = SagerDatabase.groupDao.allGroups()
            allGroups = groups

            val allProxies = SagerDatabase.proxyDao.getAll()
            val totalCount = allProxies.size
            val groupCounts = allProxies.groupingBy { it.groupId }.eachCount()

            if (currentFilterGroupId == -999L) {
                currentFilterGroupId = DataStore.selectedGroup.takeIf { it > 0L }
                    ?: (if (groups.size > 1) -1L else groups.firstOrNull()?.id ?: 0L)
            }

            val proxies = when {
                currentFilterGroupId == -1L -> allProxies
                currentFilterGroupId > 0L -> SagerDatabase.proxyDao.getByGroup(currentFilterGroupId)
                else -> emptyList()
            }

            val selectedProxyId = DataStore.selectedProxy

            runOnMainDispatcher {
                val b = _binding ?: return@runOnMainDispatcher
                if (!isAdded) return@runOnMainDispatcher

                // 1. Update Subtitle: e.g. "3 个可用节点 · 当前连接正常"
                val isZh = ctx.resources.configuration.locales[0].language.startsWith("zh")
                val isConnected = (activity as? MainActivity)?.state == BaseService.State.Connected
                val statusDesc = if (isZh) {
                    if (isConnected) "当前连接正常" else "未连接"
                } else {
                    if (isConnected) "Connected" else "Disconnected"
                }
                b.sheetSubtitle.text = if (isZh) {
                    "${proxies.size} 个可用节点 · $statusDesc"
                } else {
                    "${proxies.size} available nodes · $statusDesc"
                }

                // 2. Setup Segmented Group Tabs with counts and even distribution
                setupGroupTabs(groups, groupCounts, totalCount)

                // 3. Dynamic Height & Empty State
                val isEmpty = proxies.isEmpty()
                b.emptyText.isVisible = isEmpty
                b.sheetRecycler.isVisible = !isEmpty

                val targetHeight = when {
                    isEmpty -> dp2px(140)
                    proxies.size <= 4 -> ViewGroup.LayoutParams.WRAP_CONTENT
                    else -> dp2px(360)
                }
                if (b.recyclerContainer.layoutParams.height != targetHeight) {
                    b.recyclerContainer.layoutParams = b.recyclerContainer.layoutParams.apply {
                        height = targetHeight
                    }
                }

                // 4. Update Adapter
                b.sheetRecycler.adapter = ProxyAdapter(proxies, selectedProxyId)
            }
        }
    }

    private fun setupGroupTabs(
        groups: List<ProxyGroup>,
        groupCounts: Map<Long, Int>,
        totalCount: Int
    ) {
        val b = _binding ?: return
        val ctx = context ?: return
        if (groups.size <= 1) {
            b.scrollGroupTabs.isVisible = false
            return
        }

        b.scrollGroupTabs.isVisible = true
        b.layoutGroupTabs.removeAllViews()

        val isNight = Theme.usingNightMode()
        val totalTabs = 1 + groups.size
        val shouldDistributeEvenly = totalTabs <= 4

        // Helper to add a tab
        fun addTab(title: String, groupId: Long) {
            val isSelected = currentFilterGroupId == groupId
            val tab = TextView(ctx).apply {
                text = title
                gravity = Gravity.CENTER
                textSize = 12f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

                layoutParams = if (shouldDistributeEvenly) {
                    LinearLayout.LayoutParams(0, dp2px(32), 1f).apply {
                        setMargins(dp2px(1), 0, dp2px(1), 0)
                    }
                } else {
                    setPadding(dp2px(14), 0, dp2px(14), 0)
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp2px(32)).apply {
                        setMargins(dp2px(2), 0, dp2px(2), 0)
                    }
                }

                if (isSelected) {
                    setBackgroundResource(R.drawable.bg_segment_tab_selected)
                    setTextColor(if (isNight) Color.WHITE else Color.BLACK)
                } else {
                    setBackgroundResource(R.drawable.bg_segment_tab_unselected)
                    setTextColor(if (isNight) Color.parseColor("#999999") else Color.parseColor("#666666"))
                }

                setOnClickListener {
                    if (currentFilterGroupId != groupId) {
                        currentFilterGroupId = groupId
                        if (groupId > 0L) {
                            DataStore.selectedGroup = groupId
                        }
                        loadData()
                    }
                }
            }
            b.layoutGroupTabs.addView(tab)
        }

        // "全部" Tab with count
        val isZh = ctx.resources.configuration.locales[0].language.startsWith("zh")
        val allLabel = if (isZh) "全部 ($totalCount)" else "All ($totalCount)"
        addTab(allLabel, -1L)

        // Groups with counts
        groups.forEach { group ->
            val count = groupCounts[group.id] ?: 0
            val groupLabel = "${group.displayName()} ($count)"
            addTab(groupLabel, group.id)
        }
    }

    private inner class ProxyAdapter(
        private val list: List<ProxyEntity>,
        private val selectedProxyId: Long
    ) : RecyclerView.Adapter<ProxyAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(ItemProfileNodeRowBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.itemView.applyItemEntryAnimation(position)
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        inner class ViewHolder(val binding: ItemProfileNodeRowBinding) :
            RecyclerView.ViewHolder(binding.root) {

            @SuppressLint("SetTextI18n")
            fun bind(proxy: ProxyEntity) {
                val ctx = itemView.context
                val isNight = Theme.usingNightMode()
                val accent = Theme.getAccentColor(ctx)
                val isSelected = proxy.id == selectedProxyId

                // 1. Authentic Cupertino Frosted Liquid Glass Card Styling
                binding.cardNode.setCardBackgroundColor(Theme.getCardBackgroundColor(ctx, isSelected))
                binding.cardNode.strokeColor = Theme.getCardStrokeColor(ctx, isSelected)
                binding.cardNode.strokeWidth = dp2px(if (isSelected) 1.5f else 1f)
                binding.cardNode.radius = dp2px(16).toFloat()

                // 2. Node Icon Container: Paper plane proxy glyph
                binding.itemIcon.setImageResource(R.drawable.ic_paper_plane_up)
                if (isSelected) {
                    binding.iconContainer.backgroundTintList = ColorStateList.valueOf(
                        ColorUtils.setAlphaComponent(accent, if (isNight) 55 else 36)
                    )
                    binding.itemIcon.imageTintList = ColorStateList.valueOf(accent)
                } else {
                    binding.iconContainer.backgroundTintList = ColorStateList.valueOf(
                        if (isNight) Color.argb(40, 255, 255, 255) else Color.argb(18, 0, 0, 0)
                    )
                    binding.itemIcon.imageTintList = ColorStateList.valueOf(
                        if (isNight) Color.parseColor("#9A9AA6") else Color.parseColor("#6E6E73")
                    )
                }

                // 3. Node Title
                binding.itemTitle.text = proxy.displayName()

                // 4. Protocol Type Pill (Restrained Apple-style micro-capsule)
                binding.itemTypePill.text = proxy.displayType()
                val pillBg = if (isNight) Color.argb(180, 50, 50, 58) else Color.argb(160, 230, 232, 238)
                val pillText = if (isNight) Color.parseColor("#E0E0E6") else Color.parseColor("#3C3C43")
                binding.itemTypePill.backgroundTintList = ColorStateList.valueOf(pillBg)
                binding.itemTypePill.setTextColor(pillText)

                // 5. Subtitle (Reality / Vision / Transport / Port or Group Name)
                val address = try { proxy.displayAddress() } catch (_: Exception) { "" }
                val groupName = allGroups.find { it.id == proxy.groupId }?.displayName() ?: ""
                val bean = try { proxy.requireBean() } catch (_: Exception) { null }
                val isZh = ctx.resources.configuration.locales[0].language.startsWith("zh")

                val details = mutableListOf<String>()
                if (bean is StandardV2RayBean) {
                    if (!bean.security.isNullOrBlank() && bean.security != "none") {
                        details.add(bean.security.replaceFirstChar { it.uppercase() })
                    }
                    if (!bean.type.isNullOrBlank()) {
                        details.add(bean.type.uppercase())
                    }
                    if (bean is VLESSBean && !bean.flow.isNullOrBlank() && bean.flow.contains("vision", ignoreCase = true)) {
                        details.add("Vision")
                    }
                    if (bean.serverPort != null && bean.serverPort > 0) {
                        details.add(if (isZh) "端口 ${bean.serverPort}" else "Port ${bean.serverPort}")
                    }
                } else if (bean != null) {
                    if (bean.serverPort != null && bean.serverPort > 0) {
                        details.add(if (isZh) "端口 ${bean.serverPort}" else "Port ${bean.serverPort}")
                    }
                }

                val subText = when {
                    details.isNotEmpty() -> details.joinToString(" · ")
                    address.isNotBlank() && address != proxy.displayName() -> address
                    groupName.isNotBlank() -> groupName
                    else -> address
                }
                binding.itemSubtitle.text = subText
                binding.itemSubtitle.isVisible = subText.isNotBlank()

                // 6. Ping Latency Pill with circular signal dot
                val pingBg = if (isNight) Color.argb(180, 42, 42, 50) else Color.argb(160, 236, 238, 242)
                binding.itemPingContainer.backgroundTintList = ColorStateList.valueOf(pingBg)
                when {
                    proxy.ping > 0 -> {
                        binding.itemPingDot.isVisible = true
                        val dotColor = when {
                            proxy.ping <= 500 -> ContextCompat.getColor(ctx, R.color.cupertino_green)
                            proxy.ping <= 1000 -> Color.parseColor("#FF9500")
                            else -> ContextCompat.getColor(ctx, R.color.material_red_500)
                        }
                        binding.itemPingDot.backgroundTintList = ColorStateList.valueOf(dotColor)
                        binding.itemPingPill.text = "${proxy.ping} ms"
                        binding.itemPingPill.setTextColor(if (isNight) Color.parseColor("#E0E0E6") else Color.parseColor("#2C2C2E"))
                    }
                    proxy.status == 0 -> {
                        binding.itemPingDot.isVisible = false
                        binding.itemPingPill.text = ctx.getString(R.string.connection_test_testing)
                        binding.itemPingPill.setTextColor(ContextCompat.getColor(ctx, R.color.material_grey_500))
                    }
                    proxy.status < 0 -> {
                        binding.itemPingDot.isVisible = false
                        binding.itemPingPill.text = ctx.getString(R.string.unavailable)
                        binding.itemPingPill.setTextColor(ContextCompat.getColor(ctx, R.color.material_red_500))
                    }
                    else -> {
                        binding.itemPingDot.isVisible = false
                        binding.itemPingPill.text = "-- ms"
                        binding.itemPingPill.setTextColor(ContextCompat.getColor(ctx, R.color.material_grey_500))
                    }
                }

                // 7. Active Circular Checkmark Badge
                if (isSelected) {
                    binding.itemCheck.isVisible = true
                    binding.itemCheck.backgroundTintList = ColorStateList.valueOf(accent)
                    binding.itemCheck.imageTintList = ColorStateList.valueOf(Color.WHITE)
                } else {
                    binding.itemCheck.isVisible = false
                }

                // 8. Card Click
                binding.cardNode.setOnClickListener {
                    DataStore.selectedGroup = proxy.groupId
                    DataStore.selectedProxy = proxy.id
                    if (SagerNet.started) {
                        SagerNet.reloadService()
                    }
                    onSelectedChanged?.invoke()
                    dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
