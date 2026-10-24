package io.nekohasekai.sagernet.bg.test

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.internal.BalancerBean
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.matsuri.MatsuriBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.plugin.MatsuriJSInterface
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ui.MainActivity
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

class UrlTestCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UrlTestManager.cancelTest()
    }
}

object UrlTestManager {

    const val ACTION_OPEN_URL_TEST = "io.nekohasekai.sagernet.ACTION_OPEN_URL_TEST"
    const val ACTION_CANCEL_URL_TEST = "io.nekohasekai.sagernet.ACTION_CANCEL_URL_TEST"
    const val NOTIFICATION_ID = 1001
    const val CHANNEL_ID = "service-test"

    interface Listener {
        fun onTestStarted(total: Int)
        fun onProfileAdded(profile: ProxyEntity)
        fun onProfileUpdated(profile: ProxyEntity, finished: Int, total: Int)
        fun onTestCompleted(successCount: Int, total: Int)
        fun onTestCancelled()
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun registerListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: Listener) {
        listeners.remove(listener)
    }

    @Volatile
    var isTesting: Boolean = false
        private set

    var currentGroupId: Long = -1L
        private set

    var totalCount: Int = 0
        private set

    var finishedCount: Int = 0
        private set

    var successCount: Int = 0
        private set

    val results = CopyOnWriteArrayList<ProxyEntity>()

    private var testJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationUpdateTime = 0L

    fun canUseBrowserForwarder(profile: ProxyEntity): Boolean {
        return when (val bean = profile.requireBean()) {
            is StandardV2RayBean -> {
                when (bean.type) {
                    "ws" -> bean.wsUseBrowserForwarder
                    "splithttp" -> bean.shUseBrowserForwarder
                    else -> false
                }
            }
            is ChainBean -> {
                SagerDatabase.proxyDao.getEntities(bean.proxies).any {
                    canUseBrowserForwarder(it)
                }
            }
            is BalancerBean -> {
                SagerDatabase.proxyDao.getEntities(bean.proxies).any {
                    canUseBrowserForwarder(it)
                }
            }
            is MatsuriBean -> !bean.canTCPing()
            else -> false
        }
    }

    fun startTest(context: Context, groupId: Long) {
        if (isTesting) return

        isTesting = true
        currentGroupId = groupId
        finishedCount = 0
        successCount = 0
        results.clear()
        lastNotificationUpdateTime = 0L

        try {
            wakeLock = SagerNet.power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "exclave:urltest").apply {
                acquire(10 * 60 * 1000L) // 10 minutes max timeout
            }
        } catch (_: Exception) {}

        testJob = scope.launch {
            val profilesUnfiltered = SagerDatabase.proxyDao.getByGroup(groupId).filter {
                !canUseBrowserForwarder(it)
            }
            totalCount = profilesUnfiltered.size
            val profilesQueue = ConcurrentLinkedQueue(profilesUnfiltered)

            withContext(Dispatchers.Main) {
                listeners.forEach { it.onTestStarted(totalCount) }
            }

            updateNotification(context, isFinished = false)

            val link = DataStore.connectionTestURL
            val timeout = 5000
            val workerJobs = mutableListOf<Job>()

            repeat(6) {
                workerJobs.add(launch {
                    while (isActive) {
                        val profile = profilesQueue.poll() ?: break
                        profile.status = 0
                        results.add(profile)
                        withContext(Dispatchers.Main) {
                            listeners.forEach { it.onProfileAdded(profile) }
                        }

                        try {
                            val instance = if (DataStore.tunImplementation == TunImplementation.SYSTEM &&
                                DataStore.serviceMode == Key.MODE_VPN &&
                                SagerNet.started &&
                                DataStore.startedProfile > 0) {
                                V2RayTestInstance(
                                    profile,
                                    link,
                                    timeout,
                                    protectPath = SagerNet.deviceStorage.noBackupFilesDir.toString() + "/protect_path"
                                )
                            } else {
                                V2RayTestInstance(profile, link, timeout)
                            }
                            val result = instance.use { it.doTest() }
                            profile.status = 1
                            profile.ping = result
                        } catch (e: PluginManager.PluginNotFoundException) {
                            profile.status = -1
                            profile.error = e.readableMessage
                        } catch (e: Exception) {
                            profile.status = 3
                            profile.error = e.readableMessage
                        }

                        synchronized(this@UrlTestManager) {
                            finishedCount++
                            if (profile.status == 1) {
                                successCount++
                            }
                        }

                        ProfileManager.updateProfile(profile)

                        withContext(Dispatchers.Main) {
                            listeners.forEach { it.onProfileUpdated(profile, finishedCount, totalCount) }
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastNotificationUpdateTime > 500L || finishedCount == totalCount) {
                            lastNotificationUpdateTime = now
                            updateNotification(context, isFinished = false)
                        }
                    }
                })
            }

            workerJobs.joinAll()

            finishTest(context)
        }
    }

    private suspend fun finishTest(context: Context) {
        isTesting = false
        releaseWakeLock()

        withContext(Dispatchers.Main) {
            listeners.forEach { it.onTestCompleted(successCount, totalCount) }
        }

        try {
            GroupManager.postReload(currentGroupId)
            MatsuriJSInterface.Default.destroyAllJsi()
        } catch (_: Exception) {}

        updateNotification(context, isFinished = true)

        scope.launch {
            delay(8000)
            if (!isTesting) {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            }
        }
    }

    fun cancelTest() {
        if (!isTesting) return
        isTesting = false
        testJob?.cancel()
        releaseWakeLock()

        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)

        scope.launch(Dispatchers.Main) {
            listeners.forEach { it.onTestCancelled() }
        }

        scope.launch {
            try {
                GroupManager.postReload(currentGroupId)
                MatsuriJSInterface.Default.destroyAllJsi()
            } catch (_: Exception) {}
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun updateNotification(context: Context, isFinished: Boolean) {
        try {
            val nm = NotificationManagerCompat.from(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_URL_TEST
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cancelIntent = Intent(context, UrlTestCancelReceiver::class.java).apply {
                action = ACTION_CANCEL_URL_TEST
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_service_active)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPendingIntent)

            if (isFinished) {
                builder.setContentTitle(context.getString(R.string.url_test_completed))
                    .setContentText(context.getString(R.string.url_test_summary, successCount, totalCount))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setProgress(0, 0, false)
            } else {
                builder.setContentTitle(context.getString(R.string.connection_test_url_test))
                    .setContentText(context.getString(R.string.url_test_progress, finishedCount, totalCount))
                    .setOngoing(true)
                    .setProgress(totalCount, finishedCount, false)
                    .addAction(R.drawable.ic_navigation_close, context.getString(android.R.string.cancel), cancelPendingIntent)
            }

            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}
    }
}
