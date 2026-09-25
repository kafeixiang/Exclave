package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.Debug
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.databinding.LayoutMemoryStatsSheetBinding
import io.nekohasekai.sagernet.ktx.applyGlassBlur
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class MemoryStatsBottomSheet(
    private val onGcExecuted: () -> Unit = {}
) : BottomSheetDialogFragment() {

    private var _binding: LayoutMemoryStatsSheetBinding? = null
    private val binding get() = _binding!!
    private var isAutoUpdating = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutMemoryStatsSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyGlassBlur()

        binding.btnGcClean.setOnClickListener {
            val beforeRuntime = Runtime.getRuntime()
            val beforeAllocated = (beforeRuntime.totalMemory() - beforeRuntime.freeMemory()) / (1024 * 1024)

            System.gc()
            Runtime.getRuntime().gc()
            System.runFinalization()

            val afterRuntime = Runtime.getRuntime()
            val afterAllocated = (afterRuntime.totalMemory() - afterRuntime.freeMemory()) / (1024 * 1024)

            val freedMB = (beforeAllocated - afterAllocated).coerceAtLeast(0)

            (activity as? MainActivity)?.snackbar("垃圾回收已完成！已释放 $freedMB MB 堆内存")?.show()
            updateMemoryStats()
            onGcExecuted()
        }

        startAutoUpdateTimer()
    }

    private fun startAutoUpdateTimer() {
        lifecycleScope.launch(Dispatchers.Main) {
            while (isAutoUpdating && _binding != null) {
                updateMemoryStats()
                delay(1500.milliseconds)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateMemoryStats() {
        val context = context ?: return
        val runtime = Runtime.getRuntime()

        val usedJvmMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxJvmMB = runtime.maxMemory() / (1024 * 1024)

        val debugMemInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(debugMemInfo)
        val pssMB = debugMemInfo.totalPss / 1024f

        val nativeAllocMB = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val availRamMB = memInfo.availMem / (1024 * 1024)
        val totalRamMB = memInfo.totalMem / (1024 * 1024)

        val threadCount = Thread.activeCount()

        runOnMainDispatcher {
            if (_binding == null) return@runOnMainDispatcher
            binding.appTotalPssText.text = String.format(Locale.US, "%.1f MB", pssMB)
            binding.jvmHeapText.text = String.format(Locale.US, "%d MB / %d MB", usedJvmMB, maxJvmMB)
            binding.nativeHeapText.text = String.format(Locale.US, "%d MB", nativeAllocMB)
            binding.systemRamText.text = String.format(Locale.US, "%.1f GB / %.1f GB", availRamMB / 1024f, totalRamMB / 1024f)
            binding.threadCountText.text = "$threadCount 线程"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isAutoUpdating = false
        _binding = null
    }
}
