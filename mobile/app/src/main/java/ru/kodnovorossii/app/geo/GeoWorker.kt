package ru.kodnovorossii.app.geo

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import ru.kodnovorossii.app.KodApp
import ru.kodnovorossii.app.MainActivity
import ru.kodnovorossii.app.R
import ru.kodnovorossii.app.data.BundleDto
import ru.kodnovorossii.app.data.Geo
import ru.kodnovorossii.app.data.KodDb
import ru.kodnovorossii.app.data.Logic
import ru.kodnovorossii.app.data.PlaceDto
import ru.kodnovorossii.app.data.Prefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Гео-уведомления: при приближении к месту из избранного или по интересам.
 * Правила ТЗ: включается пользователем, радиус 100–1000 м, не более 3 в сутки, «тихие часы».
 * Работает без Google Play Services и без сети (данные — из офлайн-пакета).
 */
class GeoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.geoEnabled) return Result.success()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val quiet = if (prefs.quietFrom > prefs.quietTo) hour >= prefs.quietFrom || hour < prefs.quietTo
                    else hour >= prefs.quietFrom && hour < prefs.quietTo
        if (quiet) return Result.success()

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.notifiedDay != today) { prefs.notifiedDay = today; prefs.notifiedCount = 0; prefs.notifiedIds = emptySet() }
        if (prefs.notifiedCount >= Prefs.MAX_NOTIFICATIONS_PER_DAY) return Result.success()

        val loc = Geo.current(applicationContext, 10000) ?: return Result.success()
        val db = KodDb.get(applicationContext)
        val gson = Gson()
        val bundle = db.cache().get("bundle")?.let { runCatching { gson.fromJson(it.json, BundleDto::class.java) }.getOrNull() }
        val favIds = db.favorites().active().map { it.placeId }.toSet()
        val all = bundle?.places.orEmpty()
        val candidates = all.filter { it.id in favIds } +
            (if (prefs.geoByInterests) all.filter { it.vertical?.slug in prefs.interests && it.id !in favIds } else emptyList())

        val already = prefs.notifiedIds
        val near = candidates
            .filter { it.id.toString() !in already && it.status == "open" }
            .map { it to Logic.haversine(loc.latitude, loc.longitude, it.lat, it.lng) }
            .filter { it.second <= prefs.geoRadius }
            .minByOrNull { it.second } ?: return Result.success()

        notify(near.first, near.second, near.first.id in favIds)
        prefs.notifiedCount = prefs.notifiedCount + 1
        prefs.notifiedIds = already + near.first.id.toString()
        return Result.success()
    }

    private fun notify(p: PlaceDto, dist: Double, fav: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(applicationContext, MainActivity::class.java).putExtra("place_id", p.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(applicationContext, p.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (fav) "Рядом место из избранного" else "Рядом интересное место"
        val n = NotificationCompat.Builder(applicationContext, KodApp.CHANNEL_GEO)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText("${p.name} — ${Logic.fmtDist(dist)} от вас. ${if (Logic.isOpenNow(p)) "Сейчас открыто" else ""}".trim())
            .setStyle(NotificationCompat.BigTextStyle().bigText("${p.name} — ${Logic.fmtDist(dist)} от вас.\n${p.short.orEmpty()}"))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(p.id, n)
    }
}
