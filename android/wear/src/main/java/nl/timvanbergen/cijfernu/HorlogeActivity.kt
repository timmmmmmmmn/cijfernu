package nl.timvanbergen.cijfernu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.remote.interactions.RemoteActivityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/** CijferNu op het horloge: twee pagina's (vegen): het rooster van vandaag of de volgende schooldag, en de nieuwste cijfers. */
class HorlogeActivity : ComponentActivity() {
    private var hervat by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Gegevens.laad(this)
        setContent { Thema { App(hervat) } }
    }

    override fun onResume() {
        super.onResume()
        hervat++
    }
}

private val INDIGO = Color(0xFF8B7CFF)
private val GOED = Color(0xFF22C55E)
private val SLECHT = Color(0xFFEF4444)
private val VLAK = Color(0xFF1C1B26)
private val GRIJS = Color(0xFFA6A3B8)

// Zoals de streepjes op het icoon en in app.js (kleur): elk vak zijn eigen kleur.
private val KLEUREN = listOf(
    0xFF3B82F6, 0xFFF59E0B, 0xFF8B5CF6, 0xFF10B981, 0xFFEC4899, 0xFF06B6D4, 0xFFF97316,
    0xFF6366F1, 0xFF84CC16, 0xFFD946EF, 0xFF14B8A6, 0xFF0EA5E9, 0xFFEAB308, 0xFFA855F7,
).map { Color(it) }

private fun kleur(vak: String): Color {
    var h = 0
    for (c in vak) h = h * 31 + c.code
    return KLEUREN[(Math.abs(h.toLong()) % KLEUREN.size).toInt()]
}

@Composable
private fun Thema(inhoud: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = INDIGO,
            onPrimary = Color(0xFF12004A),
            primaryContainer = Color(0xFF1200A2),
            onPrimaryContainer = Color.White,
            surfaceContainer = VLAK,
            surfaceContainerLow = VLAK,
            surfaceContainerHigh = Color(0xFF27253A),
            onSurface = Color.White,
            onSurfaceVariant = GRIJS,
            error = SLECHT,
        ),
        content = inhoud,
    )
}

@Composable
private fun App(hervat: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bezig by remember { mutableStateOf(false) }
    var fout by remember { mutableStateOf<String?>(null) }

    fun ververs() {
        if (bezig) return
        bezig = true
        fout = null
        scope.launch {
            fout = withContext(Dispatchers.IO) {
                try {
                    Gegevens.ververs(ctx.applicationContext)
                    null
                } catch (e: Exception) {
                    e.message ?: "Er ging iets mis"
                }
            }
            bezig = false
        }
    }

    val klaar = Gegevens.ingelogd || Gegevens.demo
    // Bij openen en na koppelen: verversen (als het laatste keer al even geleden is).
    LaunchedEffect(hervat, klaar) {
        if (klaar && System.currentTimeMillis() - (Gegevens.stand?.t ?: 0) > 60_000) ververs()
    }

    AppScaffold {
        if (!klaar) Koppelen() else Paginas(bezig, fout, ::ververs)
    }
}

// ---------- koppelen ----------

@Composable
private fun Koppelen() {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    val lijst = rememberScalingLazyListState()
    ScreenScaffold(scrollState = lijst) { padding ->
        ScalingLazyColumn(state = lijst, contentPadding = padding, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text("CijferNu", fontWeight = FontWeight.Bold) } }
            item {
                Text(
                    status ?: "Log op je telefoon in bij SOMtoday om je horloge te koppelen.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            item {
                Button(
                    onClick = {
                        status = "Even kijken op je telefoon…"
                        val f = RemoteActivityHelper(ctx, Executors.newSingleThreadExecutor()).startRemoteActivity(
                            Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData(Uri.parse("cijfernu://horloge")),
                        )
                        f.addListener({
                            status = if (runCatching { f.get() }.isSuccess) {
                                "Log in op je telefoon. Daarna verschijnt alles hier vanzelf."
                            } else {
                                "Dat lukte niet. Is je telefoon verbonden en staat de nieuwste CijferNu erop?"
                            }
                        }, Executors.newSingleThreadExecutor())
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Koppel met telefoon", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
            item {
                FilledTonalButton(onClick = { Gegevens.zetDemo(ctx, true) }, modifier = Modifier.fillMaxWidth()) { Text("Bekijk de demo", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

// ---------- rooster en cijfers ----------

@Composable
private fun Paginas(bezig: Boolean, fout: String?, ververs: () -> Unit) {
    val pager = rememberPagerState(pageCount = { 2 })
    HorizontalPagerScaffold(pagerState = pager) {
        HorizontalPager(state = pager) { pagina ->
            AnimatedPage(pageIndex = pagina, pagerState = pager) {
                if (pagina == 0) Rooster(bezig, fout, ververs) else Cijfers(bezig, fout, ververs)
            }
        }
    }
}

private val ZONE: ZoneId get() = ZoneId.systemDefault()
private val NL = Locale.forLanguageTag("nl")
private val TIJD = DateTimeFormatter.ofPattern("H:mm", NL)
private val DAG = DateTimeFormatter.ofPattern("EEEE d MMM", NL)
private fun datum(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(ZONE).toLocalDate()

private fun dagNaam(d: LocalDate): String {
    val vandaag = LocalDate.now()
    return when (d) {
        vandaag -> "Vandaag"
        vandaag.plusDays(1) -> "Morgen"
        else -> DAG.format(d).replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun Rooster(bezig: Boolean, fout: String?, ververs: () -> Unit) {
    val stand = Gegevens.stand
    val nu = System.currentTimeMillis()
    // Vandaag zolang er nog een les komt, anders de volgende dag met lessen.
    val lessen = stand?.lessen.orEmpty()
    val volgende = lessen.firstOrNull { it.eind > nu && !it.uitval } ?: lessen.firstOrNull { it.eind > nu }
    val dag = volgende?.let { datum(it.begin) }
    val vandeDag = if (dag == null) emptyList() else lessen.filter { datum(it.begin) == dag }

    Pagina(bezig, fout, ververs, titel = dag?.let { dagNaam(it) } ?: "Rooster") {
        if (stand != null && vandeDag.isEmpty()) {
            item { Leeg("Geen lessen deze week") }
        }
        items(vandeDag) { les -> LesRij(les, bezig = les.begin <= nu && nu < les.eind, volgende = les === volgende && les.begin > nu) }
    }
}

@Composable
private fun LesRij(les: Gegevens.Les, bezig: Boolean, volgende: Boolean) {
    val voorbij = les.eind < System.currentTimeMillis()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VLAK)
            .then(if (bezig || volgende) Modifier.border(1.5.dp, INDIGO, RoundedCornerShape(20.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(if (les.uitval) GRIJS.copy(alpha = .4f) else kleur(les.vak)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                les.vak,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (les.uitval) TextDecoration.LineThrough else null,
                color = if (les.uitval || voorbij) GRIJS else Color.White,
            )
            val bits = listOfNotNull(
                TIJD.format(Instant.ofEpochMilli(les.begin).atZone(ZONE)),
                les.uur?.let { "${it}e" },
                if (les.uitval) null else les.lokaal.ifEmpty { null },
            )
            Text(
                if (les.uitval) "Vervalt" + (les.letOp?.let { " · $it" } ?: "") else if (bezig) "Nu · " + bits.drop(1).joinToString(" · ") else bits.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (les.uitval) SLECHT else if (bezig) INDIGO else GRIJS,
            )
        }
    }
}

@Composable
private fun Cijfers(bezig: Boolean, fout: String?, ververs: () -> Unit) {
    val ctx = LocalContext.current
    val cijfers = Gegevens.stand?.cijfers.orEmpty()
    Pagina(bezig, fout, ververs, titel = "Cijfers") {
        if (Gegevens.stand != null && cijfers.isEmpty()) item { Leeg("Nog geen cijfers") }
        items(cijfers) { CijferRij(it) }
        item {
            FilledTonalButton(
                onClick = { if (Gegevens.demo) Gegevens.zetDemo(ctx, false) else Gegevens.uitloggen(ctx) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (Gegevens.demo) "Demo sluiten" else "Uitloggen", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun CijferRij(c: Gegevens.Cijfer) {
    val kleur = when (c.voldoende) {
        true -> GOED
        false -> SLECHT
        null -> GRIJS
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(VLAK).padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(kleur.copy(alpha = .18f)),
        ) {
            Text(c.tekst, color = kleur, fontWeight = FontWeight.Bold, fontSize = if (c.tekst.length > 3) 12.sp else 16.sp, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.vak, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val bits = listOfNotNull(c.wat.ifEmpty { null }, c.weging.takeIf { it > 0 }?.let { "${fmt(it)}x" })
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = GRIJS, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun fmt(x: Double) = if (x == Math.floor(x)) x.toLong().toString() else x.toString().replace('.', ',')

// ---------- bouwstenen ----------

@Composable
private fun Pagina(
    bezig: Boolean,
    fout: String?,
    ververs: () -> Unit,
    titel: String,
    inhoud: androidx.wear.compose.foundation.lazy.ScalingLazyListScope.() -> Unit,
) {
    val lijst = rememberScalingLazyListState()
    ScreenScaffold(
        scrollState = lijst,
        edgeButton = {
            EdgeButton(onClick = ververs, buttonSize = EdgeButtonSize.ExtraSmall, colors = ButtonDefaults.filledTonalButtonColors()) {
                if (bezig) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Ververs")
            }
        },
    ) { padding ->
        ScalingLazyColumn(
            state = lijst,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item { ListHeader { Text(titel, fontWeight = FontWeight.Bold) } }
            if (Gegevens.demo) item { Text("Demo", style = MaterialTheme.typography.labelSmall, color = INDIGO) }
            if (fout != null) item { Leeg(fout, kleur = SLECHT) }
            if (Gegevens.stand == null && bezig) item { CircularProgressIndicator(Modifier.size(28.dp)) }
            inhoud()
        }
    }
}

@Composable
private fun Leeg(tekst: String, kleur: Color = GRIJS) {
    Text(tekst, style = MaterialTheme.typography.bodySmall, color = kleur, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
}
