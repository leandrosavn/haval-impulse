package br.com.redesurftank.havalshisuku.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils

class OverscanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = br.com.redesurftank.App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

        when (intent.action) {
            "br.com.redesurftank.havalshisuku.ACTION_UPDATE_OVERSCAN" -> {
                val value = intent.getIntExtra("value", 60)
                prefs.edit().putInt(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, value).apply()
                Log.d("OverscanReceiver", "Global overscan preference updated to $value")
                
                // Note: The BottomBarService monitoring loop will pick this up on the next app switch,
                // or we can force an apply if the service is running. 
                // For simplicity, we just update the preference here.
            }
        }
    }
}
