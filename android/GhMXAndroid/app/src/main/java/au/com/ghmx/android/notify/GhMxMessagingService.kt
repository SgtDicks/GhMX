package au.com.ghmx.android.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class GhMxMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "GhMX"
        val body = message.notification?.body ?: message.data["body"] ?: return
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(
            ReminderScheduler.CHANNEL_ID, "GhMX reminders and updates", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(this, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
            .setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(this).notify(message.messageId?.hashCode() ?: 1, notification) }
    }
}
