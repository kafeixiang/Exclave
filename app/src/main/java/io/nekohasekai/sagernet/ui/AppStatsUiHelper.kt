package io.nekohasekai.sagernet.ui

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Serializes asynchronous DiffUtil computations for AppStats lists so overlapping updates
 * never dispatch a stale DiffResult against mutated adapter data.
 */
internal class AppStatsListUpdater(
    private val lifecycleOwnerProvider: () -> LifecycleOwner?,
    private val currentDataProvider: () -> List<AppStats>,
    private val applyResult: (sortedList: List<AppStats>, diffResult: DiffUtil.DiffResult?) -> Unit,
) {
    private var updateJob: Job? = null

    fun submit(
        computeList: suspend () -> List<AppStats>,
        diffCallbackFactory: (oldList: List<AppStats>, newList: List<AppStats>) -> DiffUtil.Callback,
    ) {
        val owner = lifecycleOwnerProvider() ?: return
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) return
        updateJob?.cancel()
        val oldList = currentDataProvider()
        updateJob = owner.lifecycleScope.launch(Dispatchers.Main.immediate) {
            val (sortedList, diffResult) = withContext(Dispatchers.Default) {
                PackageCache.awaitLoadSync()
                val newList = computeList()
                val diff = if (oldList.isNotEmpty() && newList.isNotEmpty()) {
                    DiffUtil.calculateDiff(diffCallbackFactory(oldList, newList))
                } else {
                    null
                }
                newList to diff
            }
            if (!isActive) return@launch
            val safeDiff = if (currentDataProvider() === oldList) diffResult else null
            applyResult(sortedList, safeDiff)
        }
    }

    fun cancel() {
        updateJob?.cancel()
        updateJob = null
    }
}

/**
 * Binds an app icon from cache synchronously or offloads icon loading to Dispatchers.IO,
 * guarded by the target UID tag to prevent recycled ViewHolder icon flicker.
 */
internal fun ImageView.bindAppIcon(
    lifecycleOwnerProvider: () -> LifecycleOwner?,
    uid: Int,
    packageName: String?,
    @DrawableRes fallbackRes: Int,
    @ColorRes fallbackTintRes: Int? = null,
) {
    tag = uid
    if (packageName == null) {
        applyFallbackIcon(fallbackRes, fallbackTintRes)
        return
    }
    val cached = PackageCache.getCachedIcon(packageName)
    if (cached != null) {
        imageTintList = null
        setImageDrawable(cached)
        return
    }
    applyFallbackIcon(fallbackRes, fallbackTintRes)
    val owner = lifecycleOwnerProvider() ?: return
    owner.lifecycleScope.launch(Dispatchers.IO) {
        val loaded = PackageCache.loadIconCached(packageName)
        withContext(Dispatchers.Main) {
            if (tag == uid && loaded != null) {
                imageTintList = null
                setImageDrawable(loaded)
            }
        }
    }
}

private fun ImageView.applyFallbackIcon(
    @DrawableRes fallbackRes: Int,
    @ColorRes fallbackTintRes: Int?,
) {
    if (fallbackTintRes != null) {
        imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, fallbackTintRes))
    } else {
        imageTintList = null
    }
    setImageResource(fallbackRes)
}
