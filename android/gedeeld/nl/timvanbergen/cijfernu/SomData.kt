package nl.timvanbergen.cijfernu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.IsoFields

/**
 * Cijfers en lessen uit SOMtoday halen en lezen, net als app.js dat doet (maakLes/maakCijfer).
 * Gedeeld door de meldingen op de telefoon en de horloge-app.
 */
object SomData {
    val CIJFERTYPES = setOf("Toetskolom", "DeeltoetsKolom", "Werkstukcijferkolom", "Advieskolom", "SamengesteldeToetsKolom", "RapportToetskolom")

    private const val CIJFERPARAMS = "type=Toetskolom&type=DeeltoetsKolom&type=Werkstukcijferkolom&type=Advieskolom" +
        "&additional=vaknaam&additional=resultaatkolom&additional=naamalternatiefniveau&additional=vakuuid&additional=lichtinguuid" +
        "&sort=desc-geldendResultaatCijferInvoer"

    fun linkId(o: JSONObject): String = o.optJSONArray("links")?.optJSONObject(0)?.optString("id").orEmpty().ifEmpty { o.optString("id") }

    // ---------- cijfers ----------

    /** Huidige SOMtoday: voortgangs- en examendossier; oudere: resultaten/huidigVoorLeerling. */
    fun haalCijfers(ctx: Context, leerling: Long): List<JSONObject> = try {
        val voortgang = Som.alles(ctx, "geldendvoortgangsdossierresultaten/leerling/$leerling?$CIJFERPARAMS")
        val examen = runCatching { Som.alles(ctx, "geldendexamendossierresultaten/leerling/$leerling?$CIJFERPARAMS") }.getOrDefault(emptyList())
        val ids = voortgang.map { linkId(it) }.filter { it.isNotEmpty() }.toSet()
        voortgang + examen.filter { linkId(it).let { id -> id.isEmpty() || id !in ids } }
    } catch (e: Som.Fout) {
        if (e.status !in listOf(400, 403, 404, 405)) throw e
        Som.alles(ctx, "resultaten/huidigVoorLeerling/$leerling")
    }

    fun resultaat(c: JSONObject): String =
        listOf("formattedResultaat", "geldendResultaat", "resultaat", "label").map { c.optString(it) }.firstOrNull { it.isNotEmpty() && it != "null" }.orEmpty()

    fun vaknaam(c: JSONObject): String =
        c.optJSONObject("additionalObjects")?.optString("vaknaam").orEmpty().ifEmpty { c.optJSONObject("vak")?.optString("naam").orEmpty() }

    /** Alleen echte cijfers (geen gemiddelden), met een resultaat. */
    fun isCijfer(c: JSONObject) = (c.optString("type").isEmpty() || c.optString("type") in CIJFERTYPES) && resultaat(c).isNotEmpty()

    // ---------- rooster ----------

    class Les(val sleutel: String, val begin: Long, val eind: Long, val uur: Int?, val vak: String, val lokaal: String, val uitval: Boolean, val letOp: String?)

    /** De afspraken van de week waarin [dag] valt. */
    fun week(ctx: Context, leerling: Long, dag: LocalDate): List<JSONObject> {
        val jaar = dag.get(IsoFields.WEEK_BASED_YEAR)
        val week = dag.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return runCatching {
            Som.alles(ctx, "afspraakitems/$leerling/jaar/$jaar/week/$week", "application/vnd.topicus.platinum+json; charset=utf-8")
        }.getOrElse {
            val maandag = dag.with(DayOfWeek.MONDAY)
            Som.alles(ctx, "afspraken?begindatum=$maandag&einddatum=${maandag.plusDays(6)}&additional=vak")
        }
    }

    fun les(a: JSONObject): Les? {
        val begin = runCatching { OffsetDateTime.parse(a.optString("beginDatumTijd")).toInstant().toEpochMilli() }.getOrNull() ?: return null
        val eind = runCatching { OffsetDateTime.parse(a.optString("eindDatumTijd")).toInstant().toEpochMilli() }.getOrNull() ?: begin
        val ex = a.optJSONObject("additionalObjects")
        val vak = (a.optJSONObject("vak") ?: ex?.optJSONObject("vak"))?.optString("naam").orEmpty().ifEmpty { a.optString("titel", "Les") }
        val meldingen = a.optJSONArray("statusNotifications") ?: JSONArray()
        var uitval = a.optString("afspraakStatus") == "VERVALLEN" || Regex("vervalt|vervallen|uitval", RegexOption.IGNORE_CASE).containsMatchIn(a.optString("titel"))
        var letOp: String? = a.optString("wijzigingOmschrijving").ifEmpty { null }
        for (i in 0 until meldingen.length()) {
            val m = meldingen.optJSONObject(i) ?: continue
            if (m.optString("status") == "4007") uitval = true else if (letOp == null && m.optString("status") != "2002") letOp = m.optString("message").ifEmpty { null }
        }
        val sleutel = linkId(a).ifEmpty { "$begin-$vak" }
        return Les(sleutel, begin, eind, a.optInt("beginLesuur", 0).takeIf { it > 0 }, vak, a.optString("locatie"), uitval, letOp)
    }
}
