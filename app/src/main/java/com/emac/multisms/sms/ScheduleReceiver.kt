package com.emac.multisms.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Programme un envoi à une date/heure future via AlarmManager. */
object Scheduler {

    fun schedule(
        context: Context,
        triggerAtMillis: Long,
        listId: Long,
        body: String,
        delayMs: Long,
        subId: Int,
        personalize: Boolean
    ) {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            putExtra(SmsSenderService.EXTRA_LIST_ID, listId)
            putExtra(SmsSenderService.EXTRA_BODY, body)
            putExtra(SmsSenderService.EXTRA_DELAY_MS, delayMs)
            putExtra(SmsSenderService.EXTRA_SUB_ID, subId)
            putExtra(SmsSenderService.EXTRA_PERSONALIZE, personalize)
        }
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    /** Certains téléphones (Android 12+) exigent l'autorisation des « alarmes exactes ». */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java)
        return am.canScheduleExactAlarms()
    }

    const val REQUEST_CODE = 9911
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val listId = intent.getLongExtra(SmsSenderService.EXTRA_LIST_ID, -1L)
        val body = intent.getStringExtra(SmsSenderService.EXTRA_BODY) ?: ""
        val delayMs = intent.getLongExtra(SmsSenderService.EXTRA_DELAY_MS, 2000L)
        val subId = intent.getIntExtra(SmsSenderService.EXTRA_SUB_ID, -1)
        val personalize = intent.getBooleanExtra(SmsSenderService.EXTRA_PERSONALIZE, true)
        if (listId >= 0 && body.isNotBlank()) {
            SendProgress.reset()
            SmsSenderService.start(context, listId, body, delayMs, subId, personalize)
        }
    }
}
