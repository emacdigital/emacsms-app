package com.emac.multisms.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Après un redémarrage, la progression est déjà sauvegardée en base
 * (les contacts « SENT » ne sont pas renvoyés). L'utilisateur relance
 * simplement l'envoi : la reprise se fait automatiquement là où elle
 * s'était arrêtée. Ce receiver est présent pour d'éventuelles reprises
 * automatiques futures.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op pour l'instant.
    }
}
