package nl.timvanbergen.cijfernu

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Wat het horloge laat zien: de lessen van deze en volgende week en de nieuwste cijfers.
 * Rechtstreeks uit SOMtoday (eigen inlog, via de telefoon gekoppeld) en bewaard, zodat de app meteen iets toont.
 */
object Gegevens {
    class Les(val begin: Long, val eind: Long, val uur: Int?, val vak: String, val lokaal: String, val uitval: Boolean, val letOp: String?)
    class Cijfer(val vak: String, val tekst: String, val waarde: Double?, val voldoende: Boolean?, val wat: String, val weging: Double, val datum: Long)
    class Stand(val lessen: List<Les>, val cijfers: List<Cijfer>, val t: Long)

    private const val PREFS = "horloge"

    var stand by mutableStateOf<Stand?>(null)
        private set
    var ingelogd by mutableStateOf(false)
        private set
    var demo by mutableStateOf(false)
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Bij het openen: wat er bewaard is. */
    fun laad(ctx: Context) {
        demo = prefs(ctx).getBoolean("demo", false)
        ingelogd = Som.ingelogd(ctx)
        stand = if (demo) demoStand() else prefs(ctx).getString("stand", null)?.let { runCatching { lees(JSONObject(it)) }.getOrNull() }
    }

    /** Opnieuw uit SOMtoday halen. Niet op de hoofddraad; gooit Som.Fout. */
    fun ververs(ctx: Context) {
        if (demo) {
            stand = demoStand()
            return
        }
        try {
            val id = Som.leerlingId(ctx)
            val vandaag = LocalDate.now()
            val van = vandaag.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val tot = van + 8 * 86_400_000L
            val lessen = listOf(0L, 7L).flatMap { SomData.week(ctx, id, vandaag.plusDays(it)) }
                .mapNotNull { SomData.les(it) }
                .filter { it.begin in van until tot }
                .distinctBy { it.sleutel }
                .sortedWith(compareBy({ it.begin }, { it.uitval }))
                .map { Les(it.begin, it.eind, it.uur, it.vak, it.lokaal, it.uitval, it.letOp) }
            val cijfers = SomData.haalCijfers(ctx, id).filter { SomData.isCijfer(it) }.map { cijfer(it) }.sortedByDescending { it.datum }.take(30)
            val s = Stand(lessen, cijfers, System.currentTimeMillis())
            prefs(ctx).edit().putString("stand", schrijf(s).toString()).apply()
            stand = s
        } catch (e: Som.Fout) {
            if (e.status == 401) ingelogd = Som.ingelogd(ctx)
            throw e
        }
    }

    private fun cijfer(c: JSONObject): Cijfer {
        val tekst = SomData.resultaat(c)
        val waarde = c.optDouble("cijfer", Double.NaN).takeIf { !it.isNaN() } ?: tekst.trim().replace(',', '.').toDoubleOrNull()
        val voldoende = if (c.has("isVoldoende") && !c.isNull("isVoldoende")) c.optBoolean("isVoldoende") else waarde?.let { it >= 5.5 }
        val datum = listOf("datumInvoer", "datumInvoerHerkansing1", "datumInvoerEerstePoging").firstNotNullOfOrNull { k ->
            runCatching { OffsetDateTime.parse(c.optString(k)).toInstant().toEpochMilli() }.getOrNull()
        } ?: 0L
        val ex = c.optJSONObject("additionalObjects")
        val wat = c.optString("omschrijving").ifEmpty { ex?.optJSONObject("resultaatkolom")?.optString("naam").orEmpty() }
        return Cijfer(SomData.vaknaam(c).ifEmpty { "Onbekend vak" }, tekst.replace('.', ','), waarde, voldoende, wat, c.optDouble("weging", 0.0), datum)
    }

    /** De telefoon heeft nieuwe tokens gestuurd (Ontvanger). */
    fun gekoppeld() {
        ingelogd = true
        demo = false
    }

    fun zetDemo(ctx: Context, aan: Boolean) {
        prefs(ctx).edit().putBoolean("demo", aan).apply()
        laad(ctx)
    }

    fun wis(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        stand = null
    }

    fun uitloggen(ctx: Context) {
        Som.uitloggen(ctx)
        wis(ctx)
        laad(ctx)
    }

    // ---------- bewaren ----------

    private fun schrijf(s: Stand) = JSONObject().apply {
        put("t", s.t)
        put("lessen", JSONArray(s.lessen.map {
            JSONObject().put("b", it.begin).put("e", it.eind).put("u", it.uur ?: 0).put("v", it.vak).put("l", it.lokaal).put("x", it.uitval).put("o", it.letOp ?: "")
        }))
        put("cijfers", JSONArray(s.cijfers.map {
            JSONObject().put("v", it.vak).put("t", it.tekst).put("c", it.waarde ?: JSONObject.NULL).put("g", it.voldoende ?: JSONObject.NULL)
                .put("w", it.wat).put("we", it.weging).put("d", it.datum)
        }))
    }

    private fun lees(o: JSONObject): Stand {
        val l = o.getJSONArray("lessen")
        val c = o.getJSONArray("cijfers")
        return Stand(
            (0 until l.length()).map { l.getJSONObject(it) }.map {
                Les(it.getLong("b"), it.getLong("e"), it.optInt("u").takeIf { u -> u > 0 }, it.getString("v"), it.optString("l"), it.optBoolean("x"), it.optString("o").ifEmpty { null })
            },
            (0 until c.length()).map { c.getJSONObject(it) }.map {
                Cijfer(
                    it.getString("v"), it.getString("t"), if (it.isNull("c")) null else it.getDouble("c"), if (it.isNull("g")) null else it.getBoolean("g"),
                    it.optString("w"), it.optDouble("we", 0.0), it.optLong("d"),
                )
            },
            o.optLong("t"),
        )
    }

    // ---------- demo ----------

    /** Verzonnen gegevens om rond te kijken zonder in te loggen (zelfde vakken als demo.js). */
    private fun demoStand(): Stand {
        val vakken = listOf("Nederlands", "Engels", "Wiskunde B", "Natuurkunde", "Scheikunde", "Biologie", "Geschiedenis", "Lichamelijke opvoeding", "Frans", "Informatica")
        val tijden = listOf(8 to 30, 9 to 20, 10 to 10, 11 to 15, 12 to 5, 13 to 25, 14 to 15, 15 to 5)
        val zone = ZoneId.systemDefault()
        val lessen = ArrayList<Les>()
        for (plus in 0L..7L) {
            val dag = LocalDate.now().plusDays(plus)
            val d = dag.dayOfWeek.value - 1
            if (d > 4) continue
            val uren = if (d == 2) 5 else if (d == 4) 6 else 7
            for (u in 0 until uren) {
                if (d == 3 && u == 0) continue
                val b = dag.atTime(LocalTime.of(tijden[u].first, tijden[u].second)).atZone(zone).toInstant().toEpochMilli()
                val uitval = (d + u) % 9 == 4
                val lokaal = if ((d * u) % 7 == 5) "Aula" else "${1 + (d + u) % 3}${(10 + (u * 7 + d) % 20).toString().padStart(2, '0')}"
                lessen.add(Les(b, b + 45 * 60_000, u + 1, vakken[(d * 3 + u * 7) % vakken.size], lokaal, uitval, if (uitval) "Docent afwezig" else null))
            }
        }
        val nu = System.currentTimeMillis()
        val dag = 86_400_000L
        val cijfers = listOf(
            Cijfer("Wiskunde B", "7,8", 7.8, true, "Toets H3", 2.0, nu - dag),
            Cijfer("Engels", "6,4", 6.4, true, "SO woordjes", 1.0, nu - 2 * dag),
            Cijfer("Natuurkunde", "5,1", 5.1, false, "Proefwerk H4", 3.0, nu - 4 * dag),
            Cijfer("Nederlands", "8,2", 8.2, true, "Leesdossier", 1.0, nu - 6 * dag),
            Cijfer("Lichamelijke opvoeding", "V", null, true, "Turnen", 0.0, nu - 8 * dag),
            Cijfer("Scheikunde", "6,9", 6.9, true, "Toets H2", 2.0, nu - 9 * dag),
            Cijfer("Frans", "4,6", 4.6, false, "Mondeling", 1.0, nu - 12 * dag),
            Cijfer("Biologie", "7,3", 7.3, true, "Praktische opdracht", 2.0, nu - 15 * dag),
        )
        return Stand(lessen, cijfers, nu)
    }
}
