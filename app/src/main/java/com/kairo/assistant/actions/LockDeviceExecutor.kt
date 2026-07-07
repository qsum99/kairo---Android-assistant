package com.kairo.assistant.actions

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand
import com.kairo.assistant.receiver.KairoDeviceAdminReceiver

class LockDeviceExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return ActionResult(false, "Device policy manager not supported.")

        val adminComponent = ComponentName(context, KairoDeviceAdminReceiver::class.java)

        return if (devicePolicyManager.isAdminActive(adminComponent)) {
            try {
                devicePolicyManager.lockNow()
                ActionResult(true, "Locking screen")
            } catch (e: Exception) {
                Log.e("LockDeviceExecutor", "Failed to lock screen", e)
                ActionResult(false, "Failed to lock screen: ${e.message}")
            }
        } else {
            try {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Kairo requires Device Administrator privileges to lock the device on voice commands.")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(false, "Device Administrator permission is required. Opening activation settings.")
            } catch (e: Exception) {
                Log.e("LockDeviceExecutor", "Failed to request device admin permission", e)
                ActionResult(false, "Please enable Device Administrator for Kairo in your device settings.")
            }
        }
    }
}
