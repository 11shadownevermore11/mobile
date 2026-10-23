package ru.kodnovorossii.app.data

import android.content.Context

/** Настройки хранятся на устройстве и работают без входа и без сети. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("kod_prefs", Context.MODE_PRIVATE)

    init {
        // Старые версии хранили адрес сервера, введённый вручную (IP компьютера, туннель). Больше не используется.
        if (sp.contains("server")) sp.edit().remove("server").apply()
    }

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    var userJson: String?
        get() = sp.getString("user", null)
        set(v) = sp.edit().putString("user", v).apply()

    /** system | light | dark */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()

    /** По умолчанию — туристический режим; профессиональные разделы скрыты. */
    var proMode: Boolean
        get() = sp.getBoolean("pro", false)
        set(v) = sp.edit().putBoolean("pro", v).apply()

    // ---- Гео-уведомления (включаются пользователем) ----
    var geoEnabled: Boolean
        get() = sp.getBoolean("geo_enabled", false)
        set(v) = sp.edit().putBoolean("geo_enabled", v).apply()

    /** Радиус 100–1000 м */
    var geoRadius: Int
        get() = sp.getInt("geo_radius", 300)
        set(v) = sp.edit().putInt("geo_radius", v.coerceIn(100, 1000)).apply()

    var geoByInterests: Boolean
        get() = sp.getBoolean("geo_interests", true)
        set(v) = sp.edit().putBoolean("geo_interests", v).apply()

    /** «Тихие часы»: с quietFrom до quietTo (часы 0–23) уведомлений нет. */
    var quietFrom: Int
        get() = sp.getInt("quiet_from", 22)
        set(v) = sp.edit().putInt("quiet_from", v).apply()

    var quietTo: Int
        get() = sp.getInt("quiet_to", 9)
        set(v) = sp.edit().putInt("quiet_to", v).apply()

    var notifiedDay: String
        get() = sp.getString("notified_day", "") ?: ""
        set(v) = sp.edit().putString("notified_day", v).apply()

    var notifiedCount: Int
        get() = sp.getInt("notified_count", 0)
        set(v) = sp.edit().putInt("notified_count", v).apply()

    var notifiedIds: Set<String>
        get() = sp.getStringSet("notified_ids", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("notified_ids", v).apply()

    var interests: Set<String>
        get() = sp.getStringSet("interests", setOf("tourism", "creative")) ?: emptySet()
        set(v) = sp.edit().putStringSet("interests", v).apply()

    var lastSync: Long
        get() = sp.getLong("last_sync", 0L)
        set(v) = sp.edit().putLong("last_sync", v).apply()

    companion object {
        const val MAX_NOTIFICATIONS_PER_DAY = 3
    }
}
