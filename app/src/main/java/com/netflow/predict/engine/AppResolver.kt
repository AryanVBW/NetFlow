package com.netflow.predict.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves network traffic to the originating app (UID → package name).
 *
 * Uses multiple strategies:
 * 1. ConnectivityManager.getConnectionOwnerUid() (API 29+) — most reliable.
 * 2. /proc/net/tcp and /proc/net/udp parsing — works on all versions.
 * 3. PackageManager to resolve UID → package name.
 *
 * Caches UID → AppInfo mappings for performance.
 */
class AppResolver(private val context: Context) {

    companion object {
        private const val TAG = "AppResolver"
        private const val CACHE_EXPIRY_MS = 60_000L // re-resolve every 60s
    }

    data class AppInfo(
        val uid: Int,
        val packageName: String,
        val appName: String,
        val isSystem: Boolean
    )

    /** UID → AppInfo cache */
    private val cache = ConcurrentHashMap<Int, CachedApp>()
    private data class CachedApp(val info: AppInfo, val timestamp: Long)

    private val pm: PackageManager = context.packageManager

    /**
     * Resolve a UID to an AppInfo. This may do I/O (PackageManager query)
     * so it should not be called on the hot packet-processing path.
     * Use resolveAppSync() for the fast cached path.
     */
    fun resolveApp(uid: Int): AppInfo? {
        // Check cache first
        val cached = cache[uid]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRY_MS) {
            return cached.info
        }

        return try {
            val packages = pm.getPackagesForUid(uid)
            if (packages.isNullOrEmpty()) {
                // System UID or unknown
                val info = AppInfo(
                    uid = uid,
                    packageName = "android.uid.$uid",
                    appName = resolveSystemUidName(uid),
                    isSystem = true
                )
                cache[uid] = CachedApp(info, System.currentTimeMillis())
                info
            } else {
                val pkg = packages[0]
                val appInfo = try {
                    pm.getApplicationInfo(pkg, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }

                val name = appInfo?.let {
                    pm.getApplicationLabel(it).toString()
                } ?: pkg

                val isSystem = appInfo?.let {
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } ?: false

                val info = AppInfo(
                    uid = uid,
                    packageName = pkg,
                    appName = name,
                    isSystem = isSystem
                )
                cache[uid] = CachedApp(info, System.currentTimeMillis())
                info
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve UID $uid", e)
            null
        }
    }

    /**
     * Fast synchronous lookup from cache only. Returns null if not cached.
     * Safe to call from the packet processing hot path.
     */
    fun resolveAppSync(uid: Int): AppInfo? {
        return cache[uid]?.info
    }

    /**
     * Look up the UID that owns a specific network connection using /proc/net.
     * This works without root on most Android versions.
     *
     * @param protocol "tcp" or "udp"
     * @param localPort the local port of the connection
     * @return the UID, or -1 if not found
     */
    fun findUidForConnection(protocol: String, localPort: Int): Int {
        try {
            val path = "/proc/net/${protocol.lowercase()}"
            val reader = BufferedReader(FileReader(path))

            reader.use { br ->
                br.readLine() // skip header
                var line = br.readLine()
                while (line != null) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 8) {
                        // Column 1 is local_address in hex (IP:PORT)
                        val localAddr = parts[1]
                        val portHex = localAddr.substringAfter(':')
                        try {
                            val port = portHex.toInt(16)
                            if (port == localPort) {
                                return parts[7].toIntOrNull() ?: -1
                            }
                        } catch (_: NumberFormatException) {}
                    }
                    line = br.readLine()
                }
            }

            // Try IPv6 as well
            val path6 = "/proc/net/${protocol.lowercase()}6"
            val reader6 = BufferedReader(FileReader(path6))
            reader6.use { br ->
                br.readLine()
                var line = br.readLine()
                while (line != null) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 8) {
                        val localAddr = parts[1]
                        val portHex = localAddr.substringAfter(':')
                        try {
                            val port = portHex.toInt(16)
                            if (port == localPort) {
                                return parts[7].toIntOrNull() ?: -1
                            }
                        } catch (_: NumberFormatException) {}
                    }
                    line = br.readLine()
                }
            }
        } catch (e: Exception) {
            // /proc/net may not be readable on some devices
            Log.d(TAG, "Cannot read /proc/net: ${e.message}")
        }
        return -1
    }

    /**
     * Preload the cache with all installed packages.
     * Call this once at service start.
     */
    fun preloadCache() {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.PackageInfoFlags.of(0)
            } else {
                @Suppress("DEPRECATION")
                0
            }

            @Suppress("DEPRECATION")
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getInstalledPackages(0)
            }

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val uid = appInfo.uid
                val name = pm.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                val info = AppInfo(
                    uid = uid,
                    packageName = pkg.packageName,
                    appName = name,
                    isSystem = isSystem
                )
                cache[uid] = CachedApp(info, System.currentTimeMillis())
            }
            Log.d(TAG, "Preloaded ${cache.size} app entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to preload app cache", e)
        }
    }

    private fun resolveSystemUidName(uid: Int): String = when (uid) {
        0 -> "Root"
        1000 -> "Android System"
        1001 -> "Phone/Telephony"
        1013 -> "Media Server"
        1021 -> "GPS"
        1023 -> "DNS Resolver"
        1051 -> "NFC"
        9999 -> "Nobody"
        else -> "System ($uid)"
    }
}
