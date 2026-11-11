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

package io.nekohasekai.sagernet.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.listenForPackageChanges
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object PackageCache {

    @Volatile
    lateinit var installedPackages: Map<String, PackageInfo>
    @Volatile
    lateinit var installedPluginPackages: Map<String, PackageInfo>
    @Volatile
    lateinit var installedApps: Map<String, ApplicationInfo>
    @Volatile
    lateinit var packageMap: Map<String, Int>
    @Volatile
    var uidMap = HashMap<Int, HashSet<String>>()
        private set
    val loaded = Mutex(true)
    var registered = AtomicBoolean(false)

    private val labelMap = ConcurrentHashMap<String, String>()
    private val iconCache = LruCache<String, Drawable>(64)

    fun register() {
        if (registered.getAndSet(true)) return
        reload()
        app.listenForPackageChanges(false) {
            reload()
            labelMap.clear()
            iconCache.evictAll()
        }
        loaded.unlock()
    }

    @SuppressLint("InlinedApi")
    fun reload() {
        val pm = app.packageManager
        installedPackages = if (DataStore.queryAllPackagesAlternativeMethod) {
            pm.getPackagesHoldingPermissions(
                arrayOf(Manifest.permission.INTERNET),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_UNINSTALLED_PACKAGES
                }
            )
        } else {
            pm.getInstalledPackages(
                PackageManager.GET_PERMISSIONS or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            PackageManager.MATCH_UNINSTALLED_PACKAGES
                        } else {
                            @Suppress("DEPRECATION")
                            PackageManager.GET_UNINSTALLED_PACKAGES
                        }
            ).filter {
                when (it.packageName) {
                    "android" -> true
                    else -> it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                }
            }
        }.associateBy { it.packageName }

        installedPluginPackages = pm.getInstalledPackages(
            PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA
        ).filter { pkg ->
            pkg.providers?.any { provider ->
                val auth = provider.authority ?: ""
                auth.startsWith("moe.matsuri.plugin.") ||
                        auth.startsWith("moe.matsuri.exe.") ||
                        auth.startsWith("io.nekohasekai.sagernet.plugin.")
            } == true
        }.associateBy { it.packageName }

        val installed = if (DataStore.queryAllPackagesAlternativeMethod) {
            installedPackages.map { it.value.applicationInfo }
        } else {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }
        installedApps = installed.associateBy { it.packageName }
        packageMap = installed.associate { it.packageName to it.uid }
        val newUidMap = HashMap<Int, HashSet<String>>()
        for (info in installed) {
            val uid = info.uid
            newUidMap.getOrPut(uid) { HashSet() }.add(info.packageName)
        }
        uidMap = newUidMap
    }

    operator fun get(uid: Int) = uidMap[uid]
    operator fun get(packageName: String) = packageMap[packageName]

    fun resolvePackageName(uid: Int): String? = when (uid) {
        1000 -> "android"
        else -> uidMap[uid]?.firstOrNull()
    }

    fun awaitLoadSync() {
        if (::packageMap.isInitialized) {
            return
        }
        if (!registered.get()) {
            register()
            return
        }
        runBlocking {
            loaded.withLock {
                // just await
            }
        }
    }

    fun loadLabel(packageName: String): String {
        labelMap[packageName]?.let { return it }
        val info = if (::installedApps.isInitialized) installedApps[packageName] else null
        val label = info?.loadLabel(app.packageManager)?.toString() ?: packageName
        labelMap[packageName] = label
        return label
    }

    fun getCachedIcon(packageName: String): Drawable? {
        val cached = iconCache.get(packageName) ?: return null
        return cached.constantState?.newDrawable(app.resources) ?: cached
    }

    fun loadIconCached(packageName: String): Drawable? {
        getCachedIcon(packageName)?.let { return it }
        val icon = try {
            val info = if (::installedApps.isInitialized) installedApps[packageName] else null
            info?.loadIcon(app.packageManager)
                ?: app.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
        if (icon != null) {
            iconCache.put(packageName, icon)
        }
        return icon?.constantState?.newDrawable(app.resources) ?: icon
    }

}
