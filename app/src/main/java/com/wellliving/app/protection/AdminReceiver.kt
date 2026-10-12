package com.wellliving.app.protection

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.wellliving.app.R

/**
 * While this device admin is active, Android refuses to uninstall the app (module 1.6).
 * Deactivating it goes through a system screen that EnforcementService guards with the PIN.
 * Release by a friend's approval needs the server and comes with module 8.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)

    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)
    }
}
