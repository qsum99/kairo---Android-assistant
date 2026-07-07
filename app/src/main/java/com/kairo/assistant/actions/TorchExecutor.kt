package com.kairo.assistant.actions

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand

class TorchExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val enable = command.target == "on"
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ActionResult(false, "Flashlight not supported on this device.")

        return try {
            val cameraId = cameraManager.cameraIdList.getOrNull(0)
                ?: return ActionResult(false, "Camera not found on this device.")

            cameraManager.setTorchMode(cameraId, enable)
            val message = if (enable) "Turning on flashlight" else "Turning off flashlight"
            ActionResult(true, message)
        } catch (e: Exception) {
            Log.e("TorchExecutor", "Error setting torch mode", e)
            ActionResult(false, "Failed to toggle flashlight: ${e.message}")
        }
    }
}
