package com.example.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.data.ActionOutcome
import com.example.data.AppDatabase
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Answers the notification's own buttons.
 *
 * "Later" and "Not today" are real answers, and they were previously impossible to give without
 * opening the app — so the honest options were tap-it-and-do-it, or swipe it away, which the coach
 * cannot tell apart from never having seen it. A recorded POSTPONED is what makes
 * [NotificationDecisionEngine]'s cooldown and [HealthCoachEngine.isSuppressed] mean anything.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val actionId = intent.getStringExtra(EXTRA_ACTION_ID) ?: return
        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: return
        val event = when (intent.action) {
            ACTION_POSTPONE -> HealthCoachEngine.ActionState.POSTPONED
            ACTION_SKIP -> HealthCoachEngine.ActionState.SKIPPED
            else -> return
        }

        // Dismiss immediately — the tap has been answered, and leaving it in the shade reads as if
        // nothing happened. The id matches what NotificationController posted it under.
        NotificationManagerCompat.from(context).cancel(actionId.hashCode())

        // A BroadcastReceiver's process can be killed the moment onReceive returns, which would lose
        // the write. goAsync() holds it open until the insert is actually done.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getDatabase(context).actionOutcomeDao().insert(
                    ActionOutcome(
                        at = LocalDateTime.now().toString(),
                        actionId = actionId,
                        domain = domain,
                        event = event.name,
                    ),
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_POSTPONE = "com.example.action.POSTPONE"
        const val ACTION_SKIP = "com.example.action.SKIP"
        const val EXTRA_ACTION_ID = "actionId"
        const val EXTRA_DOMAIN = "domain"
    }
}
