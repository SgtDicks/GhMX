package au.com.ghmx.android.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import au.com.ghmx.android.MainActivity
import au.com.ghmx.android.R
import au.com.ghmx.android.data.PlanStore
import org.json.JSONObject

object ReminderScheduler {
    const val CHANNEL_ID = "ghmx_reminders"

    fun schedule(context: Context, id: String, title: String, body: String, at: Long) {
        PlanStore(context).saveReminder(id, title, body, at)
        val pending = pendingIntent(context, id, title, body)
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 23) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else alarms.set(AlarmManager.RTC_WAKEUP, at, pending)
    }

    fun cancel(context: Context, id: String) {
        PlanStore(context).removeReminder(id)
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, id, "", ""))
    }

    private fun pendingIntent(context: Context, id: String, title: String, body: String) = PendingIntent.getBroadcast(
        context, id.hashCode(), Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", id); putExtra("title", title); putExtra("body", body)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id").orEmpty()
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(intent.getStringExtra("title") ?: "GhMX reminder")
            .setContentText(intent.getStringExtra("body") ?: "Your reminder is ready.")
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) }
        if (id.isNotEmpty()) {
            val plans = PlanStore(context)
            plans.removeReminder(id)
            if (id.startsWith("vendor:")) plans.vendors().firstOrNull { it.id == id.removePrefix("vendor:") }?.let {
                plans.saveVendor(it.copy(reminderAt = null))
            }
        }
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlanStore(context).reminders().forEach { row: JSONObject ->
            val at = row.optLong("at")
            if (at > System.currentTimeMillis()) ReminderScheduler.schedule(context,
                row.optString("id"), row.optString("title"), row.optString("body"), at)
        }
    }
}
