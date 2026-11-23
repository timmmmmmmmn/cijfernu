package nl.timvanbergen.cijfernu

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

/** De telefoonkant van de horloge-app: na inloggen voor het horloge de tokens erheen sturen (Wear OS-datalaag, versleuteld). */
object Horloge {
    private const val KAN = "cijfernu_horloge"
    const val PAD_TOKENS = "/cijfernu/tokens"

    /** Niet op de hoofddraad. */
    fun stuurTokens(ctx: Context, tokens: JSONObject) {
        val horloges = try {
            Tasks.await(Wearable.getCapabilityClient(ctx).getCapability(KAN, CapabilityClient.FILTER_REACHABLE)).nodes
        } catch (e: Exception) {
            throw Som.Fout("Kan je horloge niet bereiken")
        }
        if (horloges.isEmpty()) throw Som.Fout("Geen horloge met CijferNu gevonden")
        val data = tokens.toString().toByteArray()
        for (h in horloges) {
            try {
                Tasks.await(Wearable.getMessageClient(ctx).sendMessage(h.id, PAD_TOKENS, data))
            } catch (e: Exception) {
                throw Som.Fout("Versturen naar ${h.displayName} mislukt")
            }
        }
    }
}
