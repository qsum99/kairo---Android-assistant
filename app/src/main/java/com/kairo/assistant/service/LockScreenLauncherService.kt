package com.kairo.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.kairo.assistant.MainActivity

/**
 * Foreground Service that displays a floating round launch button over the Android Lock Screen.
 * Tapping the floating round button instantly opens Kairo Voice Assistant over the lock screen.
 */
class LockScreenLauncherService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingButtonView: View? = null
    private var isViewAdded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (floatingButtonView == null) {
            setupFloatingButton()
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "kairo_lockscreen_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Kairo Lock Screen Launcher",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays floating launch button on lock screen"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Kairo Lock Screen Assistant")
            .setContentText("Tap floating round button on lock screen to activate Kairo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupFloatingButton() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("LockScreenLauncherService", "Overlay permission not granted, skipping floating button")
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val buttonSizePx = (58 * resources.displayMetrics.density).toInt()

            val layoutParams = WindowManager.LayoutParams(
                buttonSizePx,
                buttonSizePx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = (20 * resources.displayMetrics.density).toInt()
                y = (100 * resources.displayMetrics.density).toInt()
            }

            // Create a beautiful round cyan glowing button
            val container = ImageView(this).apply {
                val backgroundDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#141E30")) // Deep obsidian background
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#00E676")) // Neon Cyan border
                }
                background = backgroundDrawable
                setImageResource(android.R.drawable.ic_btn_speak_now)
                setColorFilter(Color.parseColor("#00D2FF")) // Electric cyan icon tint
                val paddingPx = (12 * resources.displayMetrics.density).toInt()
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                elevation = 16f
            }

            // Support touch drag & click
            container.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var startClickTime = 0L

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = layoutParams.x
                            initialY = layoutParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            startClickTime = System.currentTimeMillis()
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            layoutParams.x = initialX - (event.rawX - initialTouchX).toInt()
                            layoutParams.y = initialY - (event.rawY - initialTouchY).toInt()
                            try {
                                windowManager?.updateViewLayout(container, layoutParams)
                            } catch (e: Exception) {
                                Log.w("LockScreenLauncherService", "Error updating layout", e)
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val clickDuration = System.currentTimeMillis() - startClickTime
                            val moveDistance = Math.hypot(
                                (event.rawX - initialTouchX).toDouble(),
                                (event.rawY - initialTouchY).toDouble()
                            )
                            if (clickDuration < 300 && moveDistance < 10) {
                                // TAP! Launch Kairo over Lock Screen
                                launchKairoOnLockScreen()
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            floatingButtonView = container
            windowManager?.addView(container, layoutParams)
            isViewAdded = true
            Log.d("LockScreenLauncherService", "Floating lock screen button added successfully")

        } catch (e: Exception) {
            Log.e("LockScreenLauncherService", "Failed to add floating button", e)
        }
    }

    private fun launchKairoOnLockScreen() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("LockScreenLauncherService", "Error launching Kairo from floating button", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isViewAdded && floatingButtonView != null) {
            try {
                windowManager?.removeView(floatingButtonView)
            } catch (e: Exception) {
                Log.w("LockScreenLauncherService", "Error removing floating view", e)
            }
        }
        floatingButtonView = null
        isViewAdded = false
    }

    companion object {
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, LockScreenLauncherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LockScreenLauncherService::class.java)
            context.stopService(intent)
        }
    }
}
