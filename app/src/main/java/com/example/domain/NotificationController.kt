package com.example.domain

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

class NotificationController(private val context: Context) {
    
    companion object {
        const val CHANNEL_COACH = "coach_channel"
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Health Coach"
            val descriptionText = "Actionable advice and reminders from your Health Coach"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_COACH, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    @android.annotation.SuppressLint("MissingPermission")
    fun sendCoachNotification(title: String, message: String, actionId: String, domain: String = "") {
        if (!hasPermission()) return
        
        // Deep link intent to the app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("actionId", actionId)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // The two answers that are not "do it now". Without these the only way to decline is a swipe,
        // which the coach cannot distinguish from never having seen the notification at all.
        fun answer(action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
                putExtra(NotificationActionReceiver.EXTRA_ACTION_ID, actionId)
                putExtra(NotificationActionReceiver.EXTRA_DOMAIN, domain)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_COACH)
            .setSmallIcon(com.example.R.drawable.ic_notification)
            // Tints the small icon and the app name in the shade with the app's own accent.
            .setColor(android.graphics.Color.parseColor("#4C8DFF"))
            .setColorized(false)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Long text wraps instead of being cut off — the reason is the useful half.
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(message))
            .addAction(0, "Later", answer(NotificationActionReceiver.ACTION_POSTPONE, actionId.hashCode()))
            .addAction(0, "Not today", answer(NotificationActionReceiver.ACTION_SKIP, actionId.hashCode() + 1))

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(actionId.hashCode(), builder.build())
            } catch (e: SecurityException) {
                // Handle missing permission if revoked after check
            }
        }
    }
}
