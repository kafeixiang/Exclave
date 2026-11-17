package io.nekohasekai.sagernet.ui

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutIpQualitySheetBinding
import io.nekohasekai.sagernet.ktx.applyGlassBlur
import io.nekohasekai.sagernet.utils.Theme

class IpQualityBottomSheet(
    private var geoIpInfo: DashboardFragment.GeoIpData,
    private var isIpMasked: Boolean,
    private val onHideIpChanged: (Boolean) -> Unit = {},
    private val onRefreshRequested: () -> Unit = {}
) : BottomSheetDialogFragment() {

    private var _binding: LayoutIpQualitySheetBinding? = null
    private val binding get() = _binding!!
    private var refreshAnimator: ObjectAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutIpQualitySheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyGlassBlur()

        bindData()

        binding.btnRefreshIp.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startRefreshAnimation()
            onRefreshRequested()
        }

        binding.btnCopyIp.setOnClickListener {
            copyTextToClipboard(geoIpInfo.ip, getString(R.string.copied_to_clipboard))
        }

        binding.rowIpAddress.setOnClickListener {
            copyTextToClipboard(geoIpInfo.ip, getString(R.string.copied_to_clipboard))
        }

        binding.rowOrganization.setOnClickListener {
            val org = if (geoIpInfo.organization.isNotEmpty()) geoIpInfo.organization else geoIpInfo.isp
            if (org.isNotEmpty()) {
                copyTextToClipboard(org, getString(R.string.copied_to_clipboard))
            }
        }

        binding.rowAsn.setOnClickListener {
            if (geoIpInfo.asn.isNotEmpty()) {
                val asnText = if (geoIpInfo.asn.startsWith("AS", ignoreCase = true)) geoIpInfo.asn else "AS${geoIpInfo.asn}"
                copyTextToClipboard(asnText, getString(R.string.copied_to_clipboard))
            }
        }

        binding.switchHideIp.isChecked = isIpMasked
        binding.rowHideIp.setOnClickListener {
            binding.switchHideIp.toggle()
        }
        binding.switchHideIp.setOnCheckedChangeListener { _, isChecked ->
            isIpMasked = isChecked
            updateIpDisplay()
            onHideIpChanged(isChecked)
        }
    }

    fun updateData(newInfo: DashboardFragment.GeoIpData) {
        geoIpInfo = newInfo
        if (_binding != null) {
            stopRefreshAnimation()
            bindData()
        }
    }

    private fun startRefreshAnimation() {
        if (refreshAnimator?.isRunning == true) return
        refreshAnimator = ObjectAnimator.ofFloat(binding.btnRefreshIp, View.ROTATION, 0f, 360f).apply {
            duration = 1000
            interpolator = LinearInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopRefreshAnimation() {
        refreshAnimator?.cancel()
        binding.btnRefreshIp.rotation = 0f
    }

    @SuppressLint("SetTextI18n")
    private fun bindData() {
        updateIpDisplay()

        // 1. Level / Quality
        val level = if (geoIpInfo.qualityLevel.isNotEmpty()) geoIpInfo.qualityLevel else getString(R.string.ip_quality_good)
        binding.tvQualityLevel.text = level
        val accentColor = Theme.getAccentColor(requireContext())
        when (level) {
            getString(R.string.ip_quality_good), "良好" -> {
                val greenColor = 0xFF34C759.toInt()
                binding.tvQualityLevel.setTextColor(greenColor)
                binding.tvQualityLevel.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(greenColor, 38))
            }
            getString(R.string.ip_quality_risky), "高风险" -> {
                val redColor = 0xFFFF3B30.toInt()
                binding.tvQualityLevel.setTextColor(redColor)
                binding.tvQualityLevel.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(redColor, 38))
            }
            else -> {
                binding.tvQualityLevel.setTextColor(accentColor)
                binding.tvQualityLevel.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accentColor, 38))
            }
        }

        // 2. Type (住宅 / 机房 / 移动网络 / 商业)
        val type = if (geoIpInfo.ipType.isNotEmpty()) geoIpInfo.ipType else getString(R.string.ip_type_residential)
        binding.tvIpType.text = type

        // 3. Flags
        val flags = mutableListOf<String>()
        if (geoIpInfo.isTor) flags.add(getString(R.string.ip_flag_tor))
        if (geoIpInfo.isAbuser) flags.add(getString(R.string.ip_flag_abuser))
        if (geoIpInfo.isVpn) flags.add(getString(R.string.ip_flag_vpn))
        if (geoIpInfo.isProxy) flags.add(getString(R.string.ip_flag_proxy))

        binding.layoutFlagsContainer.removeAllViews()
        if (flags.isEmpty()) {
            binding.layoutFlagsContainer.addView(binding.tvFlagsEmpty)
        } else {
            val ctx = requireContext()
            flags.forEach { flag ->
                val pill = TextView(ctx).apply {
                    text = flag
                    textSize = 11f
                    setTextColor(accentColor)
                    setBackgroundResource(R.drawable.bg_segmented_pill)
                    backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accentColor, 38))
                    setPadding(dpToPx(7f), dpToPx(3f), dpToPx(7f), dpToPx(3f))
                    val lp = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dpToPx(5f)
                    }
                    layoutParams = lp
                }
                binding.layoutFlagsContainer.addView(pill)
            }
        }

        // 4. Network Info
        val org = if (geoIpInfo.organization.isNotEmpty()) geoIpInfo.organization else geoIpInfo.isp
        binding.tvOrganization.text = if (org.isNotEmpty()) org else "—"

        val asn = geoIpInfo.asn
        binding.tvAsn.text = if (asn.isNotEmpty()) {
            if (asn.startsWith("AS", ignoreCase = true)) asn else "AS$asn"
        } else {
            "—"
        }

        binding.tvSource.text = if (geoIpInfo.source.isNotEmpty()) geoIpInfo.source else "ipquery.io"
    }

    private fun updateIpDisplay() {
        val ip = geoIpInfo.ip
        if (ip.isEmpty()) {
            binding.tvIpAddress.text = "—"
        } else if (isIpMasked) {
            binding.tvIpAddress.text = "•••.•••.•••.•••"
        } else {
            binding.tvIpAddress.text = ip
        }
    }

    private fun copyTextToClipboard(text: String, message: String) {
        if (text.isEmpty() || text == "—") return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("IP Info", text)
        clipboard.setPrimaryClip(clip)
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        (activity as? MainActivity)?.snackbar(message)?.show()
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    override fun onDestroyView() {
        refreshAnimator?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
