package nl.timvanbergen.cijfernu

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray

/**
 * Herinneringen voor je eigen taken (de avond ervoor of die ochtend). De pagina geeft steeds de hele lijst door;
 * die wordt bewaard, zodat hij na een herstart van de telefoon opnieuw ingepland kan worden.
 * Niet op de seconde precies (dan is er een extra toestemming nodig), maar wel binnen een paar minuten.
 */
object Taken {
    private const val PREFS = "taken"
    private const val KANAAL = "taken"
    const val EXTRA_ID = "taak"

    fun vervang(ctx: Context, json: String) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oud = runCatching { JSONArray(p.getString("lijst", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until oud.length()) annuleer(ctx, oud.getJSONObject(i).optString("id"))
        p.edit().putString("lijst", json).apply()
        plan(ctx)
    }

    /** Alles uit de bewaarde lijst (opnieuw) inplannen. */
    fun plan(ctx: Context) {
        val lijst = runCatching { JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lijst", "[]")) }.getOrDefault(JSONArray())
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val nu = System.currentTimeMillis()
        for (i in 0 until lijst.length()) {
            val t = lijst.getJSONObject(i)
            val at = t.optLong("at")
            if (at <= nu) continue
            val intent = Intent(ctx, TaakReceiver::class.java)
                .putExtra(EXTRA_ID, t.optString("id"))
                .putExtra("titel", t.optString("titel"))
                .putExtra("tekst", t.optString("tekst"))
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, t.optString("id"), intent))
        }
    }

    private fun annuleer(ctx: Context, id: String) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id, Intent(ctx, TaakReceiver::class.java)))
    }

    private fun pending(ctx: Context, id: String, intent: Intent) =
        PendingIntent.getBroadcast(ctx, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun kanaal(ctx: Context) {
        val m = ctx.getSystemService(NotificationManager::class.java)
        if (m.getNotificationChannel(KANAAL) == null) {
            m.createNotificationChannel(NotificationChannel(KANAAL, "Herinneringen voor taken", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    fun toon(ctx: Context, id: String, titel: String, tekst: String) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        kanaal(ctx)
        val open = Intent(ctx, MainActivity::class.java).putExtra(Meldingen.EXTRA_TAB, "huiswerk").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tik = PendingIntent.getActivity(ctx, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, KANAAL)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(ctx.getColor(R.color.accent))
            .setContentTitle(titel)
            .setContentText(tekst)
            .setStyle(Notification.BigTextStyle().bigText(tekst))
            .setContentIntent(tik)
            .setAutoCancel(true)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(id.hashCode(), n) }
    }
}

/** Een herinnering gaat af, of de telefoon is herstart / de app bijgewerkt (dan alles opnieuw inplannen). */
class TaakReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Taken.plan(context)
            else -> Taken.toon(
                context,
                intent.getStringExtra(Taken.EXTRA_ID) ?: return,
                intent.getStringExtra("titel").orEmpty(),
                intent.getStringExtra("tekst").orEmpty(),
            )
        }
    }
}
