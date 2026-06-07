package com.idleworlds.android

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.webkit.CookieManager
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class NotificationPollingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private lateinit var store: GameNotificationStore

    override fun onCreate() {
        super.onCreate()
        store = GameNotificationStore(this)
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!store.pushPollingEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            NotificationHelper.buildPollingNotification(this),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (pollingJob?.isActive != true) {
            pollingJob = scope.launch { pollLoop() }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun pollLoop() {
        while (scope.isActive && store.pushPollingEnabled) {
            try {
                pollCoreNotifications()
                if (store.pushPollingEnabled) {
                    pollWorldBossNotifications()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notification poll failed", e)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun pollCoreNotifications() {
        val section = store.gameSection
        val response = fetchJson(
            "$GAME_HOST/api/player?section=${encode(section)}&scope=core"
        ) ?: return

        val notifications = response.optJSONArray("notifications") ?: return
        val seenIds = store.getSeenNotificationIds().toMutableSet()

        for (index in 0 until notifications.length()) {
            val item = notifications.optJSONObject(index) ?: continue
            if (!item.isNull("readAt")) continue

            val id = item.optString("id")
            if (id.isBlank() || id in seenIds) continue

            val title = item.optString("title").ifBlank { "IdleWorlds" }
            val body = item.optString("body").ifBlank { "You have a new notification." }

            NotificationHelper.showGameNotification(
                context = this,
                notificationId = id.hashCode(),
                title = title,
                body = body,
                url = "$GAME_HOST/notifications"
            )
            store.markNotificationSeen(id)
            seenIds.add(id)
        }
    }

    private fun pollWorldBossNotifications() {
        val section = store.gameSection
        val response = fetchJson(
            "$GAME_HOST/api/player?section=${encode(section)}&scope=worldboss"
        ) ?: return

        val worldBosses = response.optJSONArray("worldBosses") ?: JSONArray()
        val snapshot = worldBosses.toString()
        val previous = store.worldBossSnapshot

        if (previous.isNotEmpty() && snapshot != previous) {
            val bossName = findActiveWorldBossName(worldBosses)
            if (bossName != null) {
                NotificationHelper.showGameNotification(
                    context = this,
                    notificationId = WORLD_BOSS_NOTIFICATION_ID,
                    title = "World Boss Alert",
                    body = "$bossName is available on IdleWorlds.",
                    url = GAME_URL
                )
            }
        }

        store.worldBossSnapshot = snapshot
    }

    private fun findActiveWorldBossName(worldBosses: JSONArray): String? {
        for (index in 0 until worldBosses.length()) {
            val boss = worldBosses.optJSONObject(index) ?: continue
            val name = boss.optString("name").ifBlank { boss.optString("activityKey") }
            if (name.isNotBlank()) {
                return name
            }
        }
        return null
    }

    private fun fetchJson(url: String): JSONObject? {
        val cookie = CookieManager.getInstance().getCookie(GAME_HOST)
        if (cookie.isNullOrBlank()) {
            return null
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Cookie", cookie)
            setRequestProperty("Accept", "application/json")
        }

        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private const val TAG = "NotificationPolling"
        private const val GAME_HOST = "https://www.idleworlds.com"
        private const val GAME_URL = "$GAME_HOST/"
        private const val POLL_INTERVAL_MS = 60_000L
        private const val FOREGROUND_ID = 1001
        private const val WORLD_BOSS_NOTIFICATION_ID = 2001

        const val ACTION_START = "com.idleworlds.android.action.START_POLLING"
        const val ACTION_STOP = "com.idleworlds.android.action.STOP_POLLING"

        fun start(context: Context) {
            val intent = Intent(context, NotificationPollingService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, NotificationPollingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
