package nl.timvanbergen.cijfernu

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/** Ontvangt de tokens die de telefoon stuurt na "koppelen" (zie Horloge.kt in de telefoon-app). */
class Ontvanger : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/cijfernu/tokens") return
        val tokens = runCatching { JSONObject(String(event.data)) }.getOrNull() ?: return
        Som.uitloggen(this)
        Gegevens.wis(this)
        Som.bewaar(this, tokens)
        Gegevens.gekoppeld()
    }
}
