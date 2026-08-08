package com.emac.multisms.sms

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.emac.multisms.MainActivity
import com.emac.multisms.data.Repository
import com.emac.multisms.data.SendStatus
import com.emac.multisms.net.ApiClient
import com.emac.multisms.session.SessionManager
import com.emac.multisms.util.SmsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class SmsSenderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SessionManager.ensure(applicationContext)
        when (intent?.action) {
            ACTION_PAUSE -> { SendController.pauseRequested = true; return START_NOT_STICKY }
            ACTION_RESUME -> { SendController.pauseRequested = false; return START_NOT_STICKY }
            ACTION_STOP -> { SendController.stopRequested = true; return START_NOT_STICKY }
        }

        val listId = intent?.getLongExtra(EXTRA_LIST_ID, -1L) ?: -1L
        val body = intent?.getStringExtra(EXTRA_BODY) ?: ""
        val delayMs = intent?.getLongExtra(EXTRA_DELAY_MS, 2000L) ?: 2000L
        val subId = intent?.getIntExtra(EXTRA_SUB_ID, -1) ?: -1
        val personalize = intent?.getBooleanExtra(EXTRA_PERSONALIZE, true) ?: true

        if (listId < 0 || body.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        SendController.resetFlags()
        startForeground(NOTIF_ID, buildNotification("Préparation de l'envoi…", 0, 0))

        scope.launch {
            runCampaign(listId, body, delayMs, subId, personalize)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun runCampaign(listId: Long, body: String, delayMs: Long, subId: Int, personalize: Boolean) {
        val repo = Repository(applicationContext)
        val targets = repo.contactsForSending(listId)
        val total = targets.size

        SendProgress.set(
            SendState(
                phase = SendPhase.RUNNING,
                total = total,
                statusLabel = "Envoi en cours"
            )
        )

        if (total == 0) {
            SendProgress.update { it.copy(phase = SendPhase.FINISHED, statusLabel = "Aucun contact à envoyer") }
            return
        }

        val sms = getSmsManager(subId)
        var creditsExhausted = false

        for (contact in targets) {
            if (SendController.stopRequested) break

            // Vérifie les crédits (monnaie logicielle) avant chaque envoi.
            if (SessionManager.credits <= 0) {
                creditsExhausted = true
                break
            }

            // Gestion de la pause.
            while (SendController.pauseRequested && !SendController.stopRequested) {
                SendProgress.update { it.copy(phase = SendPhase.PAUSED, statusLabel = "En pause") }
                delay(400)
            }
            if (SendController.stopRequested) break
            SendProgress.update { it.copy(phase = SendPhase.RUNNING, statusLabel = "Envoi en cours") }

            val text = if (personalize) SmsText.personalize(body, contact.name) else body
            val parts = SmsText.parts(text).coerceAtLeast(1)

            // Chaque partie SMS = 1 crédit. On s'arrête si le solde ne suffit pas.
            if (SessionManager.credits < parts) {
                creditsExhausted = true
                break
            }

            val ok = withTimeoutOrNull(30_000) { sendOne(sms, contact.phone, text) } ?: false

            repo.setStatus(contact.id, if (ok) SendStatus.SENT else SendStatus.FAILED)
            repo.logSent(contact.name, contact.phone, text, ok)
            if (ok) SessionManager.consume(parts) // 1 crédit par partie SMS envoyée

            SendProgress.update {
                it.copy(
                    processed = it.processed + 1,
                    sent = if (ok) it.sent + 1 else it.sent,
                    failed = if (ok) it.failed else it.failed + 1,
                    currentName = contact.name,
                    creditsLeft = SessionManager.credits
                )
            }
            val s = SendProgress.state.value
            updateNotification("Envoi : ${s.processed}/${s.total} · ${s.creditsLeft} crédits", s.processed, s.total)

            if (!SendController.stopRequested) delay(delayMs)
        }

        // Signale l'usage au backend (best-effort) et resynchronise le solde.
        val sentTotal = SendProgress.state.value.sent
        if (sentTotal > 0) {
            ApiClient.reportUsage(sentTotal)?.let { SessionManager.setCredits(it) }
        }

        val stopped = SendController.stopRequested
        SendProgress.update {
            it.copy(
                phase = SendPhase.FINISHED,
                statusLabel = when {
                    creditsExhausted -> "Crédits épuisés"
                    stopped -> "Arrêté"
                    else -> "Terminé"
                },
                creditsLeft = SessionManager.credits
            )
        }
    }

    /** Envoie un SMS (multipart si besoin) et attend le résultat via PendingIntent. */
    private suspend fun sendOne(sms: SmsManager, phone: String, text: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val parts = sms.divideMessage(text)
            val action = "$ACTION_SMS_SENT.${System.nanoTime()}"
            var remaining = parts.size
            var allOk = true

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (resultCode != Activity.RESULT_OK) allOk = false
                    remaining--
                    if (remaining <= 0) {
                        runCatching { unregisterReceiver(this) }
                        if (cont.isActive) cont.resume(allOk)
                    }
                }
            }

            ContextCompat.registerReceiver(
                this, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED
            )
            cont.invokeOnCancellation { runCatching { unregisterReceiver(receiver) } }

            try {
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                for (idx in parts.indices) {
                    val pi = PendingIntent.getBroadcast(
                        this, idx,
                        Intent(action).setPackage(packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    sentIntents.add(pi)
                }
                if (parts.size == 1) {
                    sms.sendTextMessage(phone, null, text, sentIntents[0], null)
                } else {
                    sms.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
                }
            } catch (e: Exception) {
                runCatching { unregisterReceiver(receiver) }
                if (cont.isActive) cont.resume(false)
            }
        }

    @Suppress("DEPRECATION")
    private fun getSmsManager(subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = getSystemService(SmsManager::class.java)
            if (subId >= 0) base.createForSubscriptionId(subId) else base
        } else {
            if (subId >= 0) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
        }
    }

    // --- Notification ---

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Envoi de SMS", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(text: String, progress: Int, max: Int): android.app.Notification {
        ensureChannel()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Multi SMS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setOngoing(true)
            .setContentIntent(pi)
        if (max > 0) builder.setProgress(max, progress, false)
        return builder.build()
    }

    private fun updateNotification(text: String, progress: Int, max: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text, progress, max))
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "sms_sending"
        const val NOTIF_ID = 4711
        const val ACTION_SMS_SENT = "com.emac.multisms.SMS_SENT"

        const val ACTION_PAUSE = "com.emac.multisms.PAUSE"
        const val ACTION_RESUME = "com.emac.multisms.RESUME"
        const val ACTION_STOP = "com.emac.multisms.STOP"

        const val EXTRA_LIST_ID = "listId"
        const val EXTRA_BODY = "body"
        const val EXTRA_DELAY_MS = "delayMs"
        const val EXTRA_SUB_ID = "subId"
        const val EXTRA_PERSONALIZE = "personalize"

        fun start(context: Context, listId: Long, body: String, delayMs: Long, subId: Int, personalize: Boolean = true) {
            val intent = Intent(context, SmsSenderService::class.java).apply {
                putExtra(EXTRA_LIST_ID, listId)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_DELAY_MS, delayMs)
                putExtra(EXTRA_SUB_ID, subId)
                putExtra(EXTRA_PERSONALIZE, personalize)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendControl(context: Context, action: String) {
            val intent = Intent(context, SmsSenderService::class.java).apply { this.action = action }
            context.startService(intent)
        }
    }
}
