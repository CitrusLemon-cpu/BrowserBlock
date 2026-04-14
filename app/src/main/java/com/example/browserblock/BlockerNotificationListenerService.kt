package com.example.browserblock

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * BlockerNotificationListenerService — optional notification suppression.
 *
 * Declared in the manifest as:
 *   android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
 *
 * Responsibilities (to be implemented in a later step):
 *  - During an active block session, cancel incoming notifications from
 *    watched browser packages so the user cannot tap a notification to
 *    bypass the block screen.
 *  - Track which notifications were suppressed so they can be restored
 *    (or logged) when the session ends.
 *
 * Note: this extends the system [NotificationListenerService], NOT
 * [android.app.Service]. Registration is automatic once the user grants
 * Notification Access in system Settings.
 */
class BlockerNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // TODO: if a block session is active and sbn.packageName is in
        //       WatchedApps, cancel the notification via cancelNotification()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // TODO: track removals if needed for statistics
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // TODO: snapshot current active notifications for watched browsers
    }
}
