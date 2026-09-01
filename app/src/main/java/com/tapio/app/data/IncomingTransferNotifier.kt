package com.tapio.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tapio.app.MainActivity
import com.tapio.app.R
import com.tapio.core.nfc.SessionTokenCodec
import com.tapio.core.nfc.domain.SessionToken
import java.util.Base64

/**
 * Posts the "someone wants to share with you" notification the moment a tap is
 * detected while Tapio is in the background or the phone is locked. It is
 * high-importance with a full-screen intent, so it surfaces like an incoming call;
 * tapping **Accepter** opens Tapio straight into the transfer.
 *
 * When Tapio is already in the foreground the in-app accept screen is shown
 * instead and this is not used.
 */
class IncomingTransferNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    fun notifyIncoming(token: SessionToken) {
        ensureChannel()

        val tokenB64 = Base64.getEncoder().encodeToString(SessionTokenCodec.encode(token))

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.tapio_logo_monochrome)
            .setContentTitle(appContext.getString(R.string.notif_incoming_title, token.deviceName))
            .setContentText(token.payloadSummary)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(openIntent(tokenB64, accept = false))
            .setFullScreenIntent(openIntent(tokenB64, accept = false), true)
            .addAction(0, appContext.getString(R.string.notif_refuse), dismissIntent())
            .addAction(0, appContext.getString(R.string.notif_accept), openIntent(tokenB64, accept = true))
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun dismiss() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun openIntent(tokenB64: String, accept: Boolean): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = ACTION_INCOMING_TRANSFER
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_HANDSHAKE_TOKEN, tokenB64)
            putExtra(EXTRA_AUTO_ACCEPT, accept)
        }
        return PendingIntent.getActivity(
            appContext,
            if (accept) 1 else 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        2,
        Intent(appContext, TransferDeclineReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appContext.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_INCOMING_TRANSFER = "com.tapio.app.action.INCOMING_TRANSFER"
        const val EXTRA_HANDSHAKE_TOKEN = "com.tapio.app.extra.HANDSHAKE_TOKEN"
        const val EXTRA_AUTO_ACCEPT = "com.tapio.app.extra.AUTO_ACCEPT"

        private const val CHANNEL_ID = "incoming_transfer"
        private const val NOTIFICATION_ID = 4201

        /** Decodes the token an [openIntent] carried, or `null`. */
        fun decodeToken(base64: String?): SessionToken? = base64
            ?.let { runCatching { SessionTokenCodec.decode(Base64.getDecoder().decode(it)) }.getOrNull() }
    }
}
