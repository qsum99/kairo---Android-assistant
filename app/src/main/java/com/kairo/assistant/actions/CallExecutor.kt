package com.kairo.assistant.actions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand

class CallExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val rawExtra = command.extra
        if (rawExtra.isNullOrBlank() || rawExtra == "not_found") {
            return ActionResult(false, "I couldn't find a contact named ${command.target ?: "that user"}.")
        }
        if (rawExtra.startsWith("disambiguate")) {
            return ActionResult(false, "Multiple matches found for ${command.target}. Please disambiguate first.")
        }

        var number = rawExtra
        var selectedAccountHandle: PhoneAccountHandle? = null

        // Parse explicitly selected SIM handle if passed from ViewModel
        if (rawExtra.contains("|selected_sim|")) {
            val parts = rawExtra.split("|selected_sim|")
            number = parts[0]
            val simDetails = parts.getOrNull(1)?.split(":")
            val componentStr = simDetails?.getOrNull(0)
            val handleId = simDetails?.getOrNull(1)
            if (!componentStr.isNullOrEmpty() && !handleId.isNullOrEmpty()) {
                val comp = ComponentName.unflattenFromString(componentStr)
                if (comp != null) {
                    selectedAccountHandle = PhoneAccountHandle(comp, handleId)
                }
            }
        }

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val phoneAccounts = try {
            telecomManager?.callCapablePhoneAccounts ?: emptyList()
        } catch (e: SecurityException) {
            Log.w("CallExecutor", "Permission READ_PHONE_STATE missing, defaulting call", e)
            emptyList()
        }

        // If no explicit SIM selected, check SharedPreferences or trigger custom SIM chooser
        if (selectedAccountHandle == null && phoneAccounts.size > 1) {
            val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
            val prefKey = "contact_sim_pref_${command.target?.trim()?.lowercase()}"
            val savedHandleStr = prefs.getString(prefKey, null) // Format "componentStr:handleId"
            
            if (savedHandleStr != null) {
                val parts = savedHandleStr.split(":")
                val comp = parts.getOrNull(0)
                val id = parts.getOrNull(1)
                if (!comp.isNullOrEmpty() && !id.isNullOrEmpty()) {
                    selectedAccountHandle = phoneAccounts.firstOrNull {
                        it.componentName.flattenToString() == comp && it.id == id
                    }
                }
            }

            // If still no SIM handle is found, return the list of SIM options to prompt the user
            if (selectedAccountHandle == null) {
                val simOptionsList = phoneAccounts.mapNotNull { handle ->
                    val account = try {
                        telecomManager?.getPhoneAccount(handle)
                    } catch (e: Exception) {
                        null
                    }
                    val label = account?.label?.toString() ?: "SIM"
                    val compName = handle.componentName.flattenToString()
                    val id = handle.id
                    "$label|$compName:$id"
                }
                val simOptionsString = simOptionsList.joinToString(";")
                return ActionResult(
                    success = false,
                    message = "select_sim|${command.target}|$number|$simOptionsString"
                )
            }
        }

        return try {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (selectedAccountHandle != null) {
                    putExtra("telecom.extra.PHONE_ACCOUNT_HANDLE", selectedAccountHandle)
                }
            }
            context.startActivity(callIntent)
            ActionResult(true, "Calling ${command.target ?: number}")
        } catch (e: SecurityException) {
            Log.w("CallExecutor", "ACTION_CALL permission denied, falling back to ACTION_DIAL", e)
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                ActionResult(true, "Opening dialer for $number")
            } catch (dialException: Exception) {
                Log.e("CallExecutor", "Failed to open dialer", dialException)
                ActionResult(false, "Failed to make the call: ${dialException.message}")
            }
        } catch (e: Exception) {
            Log.e("CallExecutor", "Error executing call", e)
            ActionResult(false, "Failed to make the call: ${e.message}")
        }
    }
}
