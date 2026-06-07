package com.idleworlds.android

import android.content.Context
import android.content.SharedPreferences

class GameNotificationStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var pushPollingEnabled: Boolean
        get() = prefs.getBoolean(KEY_PUSH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PUSH_ENABLED, value).apply()

    var gameSection: String
        get() = prefs.getString(KEY_GAME_SECTION, DEFAULT_SECTION) ?: DEFAULT_SECTION
        set(value) = prefs.edit().putString(KEY_GAME_SECTION, value).apply()

    var worldBossSnapshot: String
        get() = prefs.getString(KEY_WORLD_BOSS_SNAPSHOT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WORLD_BOSS_SNAPSHOT, value).apply()

    fun getSeenNotificationIds(): Set<String> =
        prefs.getStringSet(KEY_SEEN_IDS, emptySet())?.toSet() ?: emptySet()

    fun markNotificationSeen(id: String) {
        val updated = getSeenNotificationIds().toMutableSet()
        updated.add(id)
        prefs.edit().putStringSet(KEY_SEEN_IDS, updated).apply()
    }

    fun clearSeenNotifications() {
        prefs.edit().remove(KEY_SEEN_IDS).apply()
    }

    companion object {
        const val DEFAULT_SECTION = "task"
        private const val PREFS_NAME = "idleworlds_notifications"
        private const val KEY_PUSH_ENABLED = "push_polling_enabled"
        private const val KEY_GAME_SECTION = "game_section"
        private const val KEY_WORLD_BOSS_SNAPSHOT = "world_boss_snapshot"
        private const val KEY_SEEN_IDS = "seen_notification_ids"
    }
}
