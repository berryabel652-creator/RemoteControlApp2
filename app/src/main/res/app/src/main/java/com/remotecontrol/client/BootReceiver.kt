package com.remotecontrol.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Démarrer le service au démarrage du téléphone
            val serviceIntent = Intent(context, RemoteService::class.java)
            context.startService(serviceIntent)
        }
    }
}
