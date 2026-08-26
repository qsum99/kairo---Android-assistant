package com.kairo.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kairo.assistant.MainActivity
import com.kairo.assistant.agent.KairoAgent
import com.kairo.assistant.ui.overlay.AgentRadialMenu
import kotlin.math.abs

private const val TAG = "FloatingBuddyService"
private const val CHANNEL_ID = "kairo_floating_buddy"
private const val NOTIFICATION_ID = 1001

/**
 * State of the floating buddy bubble.
 */
enum class BuddyState {
    IDLE,       // Pulsing cyan - waiting for user
    LISTENING,  // Glowing green - actively recording
    PROCESSING, // Spinning - thinking
    SPEAKING    // Glowing blue - speaking response
}

/**
 * Foreground service that draws the multi-agent radial overlay over other apps.
 *
 * Features a central Kairo (wolf) coordinator bubble surrounded by 4 specialist
 * agent bubbles (Scout, Runner, Builder, Scribe) that expand radially on tap.
 */
class FloatingBuddyService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: FrameLayout? = null

    // Compose state holders
    private val buddyState = mutableStateOf(BuddyState.IDLE)
    private val activeAgent = mutableStateOf<KairoAgent?>(null)
    private val lastResponse = mutableStateOf("")
    private val lastQuery = mutableStateOf("")
    private val isExpandedState = mutableStateOf(false)

    companion object {
        private var instance: FloatingBuddyService? = null

        fun isRunning(): Boolean = instance != null

        fun updateState(state: BuddyState) {
            instance?.buddyState?.value = state
        }

        fun updateActiveAgent(agent: KairoAgent?) {
            instance?.activeAgent?.value = agent
        }

        fun updateResponse(query: String, response: String) {
            instance?.lastQuery?.value = query
            instance?.lastResponse?.value = response
        }

        fun start(context: Context) {
            val intent = Intent(context, FloatingBuddyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBuddyService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addFloatingBubble()
        Log.i(TAG, "Multi-agent floating overlay started")
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingBubble()
        instance = null
        Log.i(TAG, "Multi-agent floating overlay destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kairo AI Agents",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Kairo AI agents floating on screen"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Kairo AI Agents")
                .setContentText("Tap the floating wolf to summon your agents")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Kairo AI Agents")
                .setContentText("Tap the floating wolf to summon your agents")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun addFloatingBubble() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        val lifecycleOwner = BuddyLifecycleOwner()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                AgentRadialMenu(
                    isExpanded = isExpandedState.value,
                    buddyState = buddyState.value,
                    activeAgent = activeAgent.value,
                    lastQuery = lastQuery.value,
                    lastResponse = lastResponse.value,
                    onCoordinatorTap = { handleCoordinatorTap() },
                    onAgentTap = { agent -> handleAgentTap(agent) },
                    onClose = { stopSelf() }
                )
            }
        }

        floatingView = FrameLayout(this).apply {
            addView(composeView)
        }

        setupDragBehavior(floatingView!!, layoutParams)
        windowManager.addView(floatingView, layoutParams)
    }

    private fun setupDragBehavior(view: FrameLayout, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        handleCoordinatorTap()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun handleCoordinatorTap() {
        if (isExpandedState.value) {
            // If expanded, collapse the radial menu
            isExpandedState.value = false
            return
        }

        // Expand the radial menu to show agent options
        isExpandedState.value = true

        // Also open Kairo main activity for voice input
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("start_listening", true)
        }
        startActivity(intent)
    }

    private fun handleAgentTap(agent: KairoAgent) {
        Log.i(TAG, "Agent tapped: ${agent.displayName} ${agent.emoji}")
        activeAgent.value = agent

        // Open Kairo with a hint about which agent was selected
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("start_listening", true)
            putExtra("forced_agent", agent.name)
        }
        startActivity(intent)
    }

    private fun removeFloatingBubble() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing floating view", e)
            }
        }
        floatingView = null
    }
}

/**
 * LifecycleOwner implementation for ComposeView in a Service context.
 */
private class BuddyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}