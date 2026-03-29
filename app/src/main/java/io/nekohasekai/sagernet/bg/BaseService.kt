/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.bg

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.BootReceiver
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.AppStatsList
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.bg.proto.ProxyInstance
import io.nekohasekai.sagernet.bg.test.V2RayTestInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.Alerts
import io.nekohasekai.sagernet.fmt.TAG_SOCKS
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.AutoSwitchStrategy
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import libexclavecore.AppStats
import libexclavecore.Libexclavecore
import libexclavecore.TrafficListener
import java.net.UnknownHostException
import com.github.shadowsocks.plugin.PluginManager as ShadowsocksPluginPluginManager
import io.nekohasekai.sagernet.aidl.AppStats as AidlAppStats

class BaseService {

    enum class State(val canStop: Boolean = false) {
        /**
         * Idle state is only used by UI and will never be returned by BaseService.
         */
        Idle,
        Connecting(true),
        Connected(true),
        Stopping,
        Stopped,
    }

    interface ExpectedException
    class ExpectedExceptionWrapper(e: Exception) : Exception(e.localizedMessage, e),
        ExpectedException

    class Data internal constructor(private val service: Interface) {
        var state = State.Stopped
        var proxy: ProxyInstance? = null
        var notification: ServiceNotification? = null

        var timeoutMonitorJob: Job? = null

        val receiver = broadcastReceiver { _, intent ->
            when (intent.action) {
                Intent.ACTION_SHUTDOWN -> service.persistStats()
                Action.RELOAD -> service.forceLoad()
                else -> service.stopRunner(keepState = false)
            }
        }
        var closeReceiverRegistered = false

        val binder = Binder(this)
        var connectingJob: Job? = null

        fun changeState(s: State, msg: String? = null) {
            if (state == s && msg == null) return
            binder.stateChanged(s, msg)
            state = s
        }
    }

    class Binder(private var data: Data? = null) : ISagerNetService.Stub(),
        CoroutineScope,
        AutoCloseable,
        TrafficListener {
        private val callbacks = object : RemoteCallbackList<ISagerNetServiceCallback>() {
            override fun onCallbackDied(callback: ISagerNetServiceCallback?, cookie: Any?) {
                super.onCallbackDied(callback, cookie)
                stopListeningForBandwidth(callback ?: return)
                stopListeningForStats(callback)
            }
        }
        private val bandwidthListeners = mutableMapOf<IBinder, Long>()  // the binder is the real identifier
        private val statsListeners = mutableMapOf<IBinder, Long>()  // the binder is the real identifier
        override val coroutineContext = Dispatchers.Main.immediate + Job()
        private var looper: Job? = null
        private var statsLooper: Job? = null

        override fun getState(): Int = (data?.state ?: State.Idle).ordinal
        override fun getProfileName(): String = data?.proxy?.profile?.displayName() ?: "Idle"

        override fun registerCallback(cb: ISagerNetServiceCallback) {
            callbacks.register(cb)
        }

        private val broadcastLock = Mutex()
        suspend fun broadcast(work: (ISagerNetServiceCallback) -> Unit) {
            broadcastLock.withLock {
                val count = callbacks.beginBroadcast()
                try {
                    repeat(count) {
                        try {
                            work(callbacks.getBroadcastItem(it))
                        } catch (_: RemoteException) {
                        } catch (e: Exception) {
                        }
                    }
                } finally {
                    callbacks.finishBroadcast()
                }
            }
        }

        private val appStats = ArrayList<AppStats>()
        override fun updateStats(t: AppStats) {
            appStats.add(t)
        }

        private suspend fun loop() {
            var lastQueryTime = 0L
            val showDirectSpeed = DataStore.showDirectSpeed
            while (true) {
                val delayMs = bandwidthListeners.values.minOrNull()
                delay(delayMs ?: return)
                if (delayMs == 0L) return
                val queryTime = System.currentTimeMillis()
                val sinceLastQueryInSeconds = (queryTime - lastQueryTime).toDouble() / 1000L
                val proxy = data?.proxy ?: return
                lastQueryTime = queryTime
                val (statsOut, outs) = proxy.outboundStats()
                val stats = TrafficStats(
                    (proxy.uplinkProxy / sinceLastQueryInSeconds).toLong(),
                    (proxy.downlinkProxy / sinceLastQueryInSeconds).toLong(),
                    if (showDirectSpeed) (proxy.uplinkDirect() / sinceLastQueryInSeconds).toLong() else 0L,
                    if (showDirectSpeed) (proxy.downlinkDirect() / sinceLastQueryInSeconds).toLong() else 0L,
                    statsOut.uplinkTotal,
                    statsOut.downlinkTotal
                )
                if (data?.state == State.Connected && bandwidthListeners.isNotEmpty()) {
                    broadcast { item ->
                        if (bandwidthListeners.contains(item.asBinder())) {
                            item.trafficUpdated(proxy.profile.id, stats, true)
                            outs.forEach { (profileId, stats) ->
                                item.trafficUpdated(
                                    profileId, TrafficStats(
                                        txRateDirect = stats.uplinkTotal,
                                        rxTotal = stats.downlinkTotal
                                    ), false
                                )
                            }
                        }
                    }
                }

            }

        }

        private suspend fun loopStats() {
            var lastQueryTime = 0L
            var tun = (data?.proxy?.service as? VpnService)?.tun ?: return
            if (!tun.trafficStatsEnabled) return

            PackageCache.awaitLoadSync()
            while (true) {
                val delayMs = statsListeners.values.minOrNull()
                if (delayMs == 0L) return
                val queryTime = System.currentTimeMillis()
                val sinceLastQueryInSeconds = ((queryTime - lastQueryTime).toDouble() / 1000).toLong()
                lastQueryTime = queryTime

                appStats.clear()
                tun = (data?.proxy?.service as? VpnService)?.tun ?: return
                tun.readAppTraffics(this)

                val statsList = AppStatsList(appStats.map {
                    val uid = it.uid
                    AidlAppStats(
                        uid,
                        it.tcpConn,
                        it.udpConn,
                        it.tcpConnTotal,
                        it.udpConnTotal,
                        it.uplink / sinceLastQueryInSeconds,
                        it.downlink / sinceLastQueryInSeconds,
                        it.uplinkTotal,
                        it.downlinkTotal,
                        it.deactivateAt
                    )
                })
                if (data?.state == State.Connected && statsListeners.isNotEmpty()) {
                    broadcast { item ->
                        if (statsListeners.contains(item.asBinder())) {
                            item.statsUpdated(statsList)
                        }
                    }
                }
                delay(delayMs ?: return)
            }

        }

        override fun startListeningForBandwidth(
            cb: ISagerNetServiceCallback,
            timeout: Long,
        ) {
            launch {
                if (bandwidthListeners.isEmpty() and (bandwidthListeners.put(
                        cb.asBinder(), timeout
                    ) == null)
                ) {
                    check(looper == null)
                    looper = launch {
                        loop()
                        looper = null
                    }
                }
                if (data?.state != State.Connected) return@launch
                val data = data
                data?.proxy ?: return@launch
                val sum = TrafficStats()
                cb.trafficUpdated(0, sum, true)
            }
        }

        override fun stopListeningForBandwidth(cb: ISagerNetServiceCallback) {
            launch {
                if (bandwidthListeners.remove(cb.asBinder()) != null && bandwidthListeners.isEmpty() && looper != null) {
                    looper!!.cancel()
                    looper = null
                }
            }
        }

        override fun unregisterCallback(cb: ISagerNetServiceCallback) {
            stopListeningForBandwidth(cb)   // saves an RPC, and safer
            stopListeningForStats(cb)
            callbacks.register(cb)
        }

        override fun protect(fd: Int) {
            (data?.proxy?.service as VpnService?)?.protect(fd)
        }

        override fun urlTest(): Int {
            val v2rayPoint = data?.proxy?.v2rayPoint ?: error("core not started")
            try {
                return Libexclavecore.urlTest(
                    v2rayPoint, TAG_SOCKS, DataStore.connectionTestURL, 5000
                )
            } catch (e: Exception) {
                Logs.w(e)
                error(e)
            }
        }

        override fun startListeningForStats(cb: ISagerNetServiceCallback, timeout: Long) {
            launch {
                if (statsListeners.isEmpty() and (statsListeners.put(
                        cb.asBinder(), timeout
                    ) == null)
                ) {
                    check(statsLooper == null)
                    statsLooper = launch {
                        loopStats()
                        statsLooper = null
                    }
                }
            }
        }

        fun checkLoop() {
            if (bandwidthListeners.isNotEmpty() && looper == null) {
                looper = launch {
                    loop()
                    looper = null
                }
            }
            if (statsListeners.isNotEmpty() && statsLooper == null) {
                statsLooper = launch {
                    loopStats()
                    statsListeners.clear()
                    statsLooper = null
                }
            }
        }

        override fun stopListeningForStats(cb: ISagerNetServiceCallback) {
            launch {
                if (statsListeners.remove(cb.asBinder()) != null && statsListeners.isEmpty() && statsLooper != null) {
                    statsLooper!!.cancel()
                    statsLooper = null
                }
            }
        }

        override fun resetTrafficStats() {
            runOnDefaultDispatcher {
                SagerDatabase.statsDao.deleteAll()
                (data?.proxy?.service as? VpnService)?.tun?.resetAppTraffics()
                val empty = AppStatsList(emptyList())
                broadcast { item ->
                    if (statsListeners.contains(item.asBinder())) {
                        item.statsUpdated(empty)
                    }
                }
            }
        }

        fun stateChanged(s: State, msg: String?) = launch {
            val profileName = profileName
            broadcast { it.stateChanged(s.ordinal, profileName, msg) }
        }

        fun profilePersisted(ids: List<Long>) = launch {
            if (bandwidthListeners.isNotEmpty() && ids.isNotEmpty()) broadcast { item ->
                if (bandwidthListeners.contains(item.asBinder())) ids.forEach(item::profilePersisted)
            }
        }

        fun missingPlugin(pluginName: String) = launch {
            val profileName = profileName
            broadcast { it.missingPlugin(profileName, pluginName) }
        }

        override fun getTrafficStatsEnabled(): Boolean {
            return (data?.proxy?.service as? VpnService)?.tun?.trafficStatsEnabled ?: false
        }

        override fun close() {
            callbacks.kill()
            cancel()
            data = null
        }
    }

    interface Interface {
        val data: Data
        val tag: String
        fun createNotification(profileName: String): ServiceNotification

        fun onBind(intent: Intent): IBinder? =
            if (intent.action == Action.SERVICE) data.binder else null

        fun forceLoad() {
            if (DataStore.selectedProxy == 0L) {
                stopRunner(false, (this as Context).getString(R.string.profile_empty))
                return
            }
            val s = data.state
            when {
                s == State.Stopped -> startRunner()
                s.canStop -> stopRunner(true)
                else -> Logs.w("Illegal state $s when invoking use")
            }
        }

        fun startTimeoutMonitor() {
            if (!DataStore.enableAutoSwitchTimeout && !DataStore.enableAutoSwitchActive) return

            val currentProfile = SagerDatabase.proxyDao.getById(DataStore.selectedProxy) ?: return
            val groupProxies = SagerDatabase.proxyDao.getByGroup(currentProfile.groupId)
            if (groupProxies.size <= 1) return

            data.timeoutMonitorJob?.cancel()
            data.timeoutMonitorJob = data.binder.launch {
                var failureCount = 0
                var lastActiveTest = System.currentTimeMillis()
                
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val timeoutSeconds = DataStore.autoSwitchTimeoutDuration.toLong().coerceAtLeast(5)
                    val activeIntervalSeconds = DataStore.autoSwitchActiveInterval.toLong()
                    
                    // 1. 周期性主动探测 (Periodic Active Check - Like Mihomo url-test)
                    if (DataStore.enableAutoSwitchActive && (now - lastActiveTest) >= activeIntervalSeconds * 1000) {
                        Logs.d("Timeout monitor: Starting periodic active URL test")
                        try {
                            autoSwitchProxy(forceTestAll = true)
                        } catch (e: Exception) {
                            Logs.d("Active test error: ${e.readableMessage}")
                        }
                        lastActiveTest = System.currentTimeMillis()
                        failureCount = 0 
                    }

                    // 2. 被动超时检查 (Passive Timeout Check)
                    if (DataStore.enableAutoSwitchTimeout) {
                        // 初始等待设定间隔，如果已失败一次则快速重试确认
                        delay(if (failureCount == 0) timeoutSeconds * 1000 else 2000)
                        if (!isActive) break

                        var result = -1
                        try {
                            if (data.proxy?.v2rayPoint != null) {
                                // 利用当前运行的进程探测，不启动新进程，极度省电
                                result = Libexclavecore.urlTest(
                                    data.proxy!!.v2rayPoint, TAG_SOCKS, DataStore.connectionTestURL, 5000
                                )
                            }
                        } catch (e: Exception) {
                            Logs.d("Health check error: ${e.readableMessage}")
                        }

                        if (result > 0) {
                            failureCount = 0 
                        } else {
                            failureCount++
                            Logs.d("Health check: failure count $failureCount")
                            if (failureCount >= 2) {
                                // 连续两次失败才触发切换，防止网络波动误切
                                try {
                                    autoSwitchProxy(forceTestAll = false)
                                } catch (e: Exception) {
                                    Logs.d("Switch error: ${e.readableMessage}")
                                }
                                break 
                            }
                        }
                    } else {
                        delay(2000) // 仅开启主动探测时，降低循环频率
                    }
                }
            }
        }

        suspend fun autoSwitchProxy(forceTestAll: Boolean = false) {
            val currentProfile = SagerDatabase.proxyDao.getById(DataStore.selectedProxy) ?: return
            val groupProxies = SagerDatabase.proxyDao.getByGroup(currentProfile.groupId)

            if (groupProxies.size <= 1) return

            var targetProxyId: Long = 0L

            if (DataStore.autoSwitchStrategy == AutoSwitchStrategy.URL_TEST) {
                // 智能测试集：全量探测或精简集（当前+最优5个+随机5个）
                val testList = if (forceTestAll || groupProxies.size <= 20) {
                    groupProxies
                } else {
                    val current = groupProxies.filter { it.id == currentProfile.id }
                    val others = groupProxies.filter { it.id != currentProfile.id }
                    val sortedOthers = others.sortedBy { if (it.ping > 0) it.ping else Int.MAX_VALUE }
                    val bestOthers = sortedOthers.take(5)
                    val randomOthers = (sortedOthers - bestOthers.toSet()).shuffled().take(5)
                    current + bestOthers + randomOthers
                }

                val semaphore = Semaphore(3) // 限制并发，防止发热
                val results = coroutineScope {
                    testList.map { proxy ->
                        async {
                            // 如果是当前节点，利用现有进程测试以省电
                            if (proxy.id == currentProfile.id && data.proxy?.v2rayPoint != null) {
                                try {
                                    val delay = Libexclavecore.urlTest(data.proxy!!.v2rayPoint, TAG_SOCKS, DataStore.connectionTestURL, 3000)
                                    proxy.ping = if (delay > 0) delay else -1
                                    proxy.status = if (delay > 0) 1 else 3
                                    SagerDatabase.proxyDao.updateProxy(proxy)
                                    if (delay > 0) proxy.id to delay.toLong() else null
                                } catch (e: Exception) { null }
                            } else {
                                semaphore.withPermit {
                                    val testInstance = V2RayTestInstance(proxy, DataStore.connectionTestURL, 3000)
                                    try {
                                        val delay = testInstance.doTest()
                                        proxy.ping = if (delay > 0) delay else -1
                                        proxy.status = if (delay > 0) 1 else 3
                                        SagerDatabase.proxyDao.updateProxy(proxy)
                                        if (delay > 0) proxy.id to delay.toLong() else null
                                    } catch (e: Exception) {
                                        proxy.ping = -1
                                        proxy.status = 3
                                        SagerDatabase.proxyDao.updateProxy(proxy)
                                        null
                                    } finally {
                                        testInstance.close()
                                    }
                                }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                
                // 通知 UI 更新延迟显示
                data.binder.profilePersisted(testList.map { it.id })

                val best = results.minByOrNull { it.second }
                if (best != null) {
                    val currentDelayResult = results.find { it.first == currentProfile.id }?.second ?: Long.MAX_VALUE
                    
                    // Mihomo 容差逻辑：除非新节点快 150ms 以上，否则不切换
                    val tolerance = 150L
                    if (best.first != currentProfile.id && (currentDelayResult == Long.MAX_VALUE || (currentDelayResult - best.second > tolerance))) {
                        targetProxyId = best.first
                        Logs.d("autoSwitchProxy: Better node found: ${targetProxyId}, Delay: ${best.second}ms (Previous: ${currentDelayResult}ms)")
                    } else {
                        targetProxyId = currentProfile.id
                    }
                } else {
                    targetProxyId = currentProfile.id
                }
            } else {
                // NEXT Strategy
                val currentIndex = groupProxies.indexOfFirst { it.id == currentProfile.id }
                val nextIndex = (currentIndex + 1) % groupProxies.size
                targetProxyId = groupProxies[nextIndex].id
            }

            if (targetProxyId != 0L && targetProxyId != currentProfile.id) {
                DataStore.selectedProxy = targetProxyId
                runOnMainDispatcher {
                    stopRunner(true)
                }
            }
        }

        suspend fun startProcesses() {
            data.proxy!!.launch()
        }

        fun startRunner() {
            this as Context
            startService(Intent(this, javaClass))
        }

        fun killProcesses() {
            data.proxy?.close()
            data.timeoutMonitorJob?.cancel()
            data.timeoutMonitorJob = null
            wakeLock?.apply {
                release()
                wakeLock = null
            }
        }

        fun stopRunner(restart: Boolean = false, msg: String? = null, keepState: Boolean = true) {
            data.notification?.destroy()
            data.notification = null
            if (data.state == State.Stopping) return
            this as Service

            data.changeState(State.Stopping)

            runOnMainDispatcher {
                data.connectingJob?.cancelAndJoin() // ensure stop connecting first
                killProcesses()
                // we use a coroutineScope here to allow clean-up in parallel
                coroutineScope {
                    val data = data
                    if (data.closeReceiverRegistered) {
                        unregisterReceiver(data.receiver)
                        data.closeReceiverRegistered = false
                    }
                    data.binder.profilePersisted(listOfNotNull(data.proxy).map { it.profile.id })
                    data.proxy = null
                }

                // change the state
                data.changeState(State.Stopped, msg)
                DataStore.startedProfile = 0L
                if (!keepState) DataStore.currentProfile = 0L
                // stop the service if nothing has bound to it
                if (restart) startRunner() else { //   BootReceiver.enabled = false
                    stopSelf()
                }
            }
        }

        fun persistStats() {
            data.proxy?.persistStats()
            (this as? VpnService)?.persistAppStats()
        }

        suspend fun preInit() {}

        var wakeLock: PowerManager.WakeLock?
        fun acquireWakeLock()

        suspend fun lateInit() {
            wakeLock?.apply {
                release()
                wakeLock = null
            }

            if (DataStore.acquireWakeLock) {
                acquireWakeLock()
            }
        }

        fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

            val data = data
            if (data.state != State.Stopped) return Service.START_NOT_STICKY
            val profile = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
            this as Context
            if (profile == null) { // gracefully shutdown: https://stackoverflow.com/q/47337857/2245107
                data.notification = createNotification("")
                stopRunner(false, getString(R.string.profile_empty))
                return Service.START_NOT_STICKY
            }
            val proxy = ProxyInstance(profile, this)
            data.proxy = proxy
            BootReceiver.enabled = DataStore.persistAcrossReboot
            if (!data.closeReceiverRegistered) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(data.receiver, IntentFilter().apply {
                        addAction(Action.RELOAD)
                        addAction(Intent.ACTION_SHUTDOWN)
                        addAction(Action.CLOSE)
                    }, "$packageName.SERVICE", null, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(data.receiver, IntentFilter().apply {
                        addAction(Action.RELOAD)
                        addAction(Intent.ACTION_SHUTDOWN)
                        addAction(Action.CLOSE)
                    }, "$packageName.SERVICE", null)
                }
                data.closeReceiverRegistered = true
            }

            val group = SagerDatabase.groupDao.getById(profile.groupId)
            data.notification = if (DataStore.showGroupName && group != null){
                createNotification("[" + group.displayName() + "] " + profile.displayName())
            } else {
                createNotification(profile.displayName())
            }

            data.changeState(State.Connecting)
            runOnMainDispatcher {
                try {
                    Executable.killAll()    // clean up old processes
                    preInit()
                    proxy.init()
                    proxy.processes = GuardedProcessPool {
                        Logs.w(it)
                        stopRunner(false, it.readableMessage)
                    }
                    DataStore.currentProfile = profile.id
                    DataStore.startedProfile = profile.id
                    startProcesses()
                    data.changeState(State.Connected)
                    data.binder.checkLoop()
                    startTimeoutMonitor()

                    for ((type, routeName) in proxy.config.alerts) {
                        data.binder.broadcast {
                            it.routeAlert(type, routeName)
                        }
                    }
                    lateInit()
                } catch (_: CancellationException) { // if the job was cancelled, it is canceller's responsibility to call stopRunner
                } catch (_: UnknownHostException) {
                    stopRunner(false, getString(R.string.invalid_server))
                } catch (e: PluginManager.PluginNotFoundException) {
                    Logs.d(e.readableMessage)
                    data.binder.missingPlugin(e.plugin)
                    stopRunner(false, null)
                } catch (e: ShadowsocksPluginPluginManager.PluginNotFoundException) {
                    Logs.d(e.readableMessage)
                    data.binder.missingPlugin("shadowsocks-" + e.plugin)
                    stopRunner(false, null)
                } catch (e: Alerts.RouteAlertException) {
                    data.binder.broadcast {
                        it.routeAlert(e.alert, e.routeName)
                    }
                    stopRunner(false, null)
                } catch (exc: Throwable) {
                    if (exc is ExpectedException) Logs.d(exc.readableMessage) else Logs.w(exc)
                    stopRunner(
                        false, exc.readableMessage
                    )
                } finally {
                    data.connectingJob = null
                }
            }
            return Service.START_NOT_STICKY
        }
    }

}
