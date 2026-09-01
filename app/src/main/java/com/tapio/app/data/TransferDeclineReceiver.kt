package com.tapio.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the "Refuser" action on the incoming-transfer notification. */
class TransferDeclineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        IncomingTransferNotifier(context).dismiss()
    }
}
