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
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kairo.assistant.MainActivity
import kotlin.math.abs

private const val TAG = "FloatingBuddyService"
private const val CHANNEL_ID = "kairo_floating_buddy"
private const val NOTIFICATION_ID = 1001

/**
 * State of the floating buddy bubble.
 */
enum class BuddyState {
    IDLE,       // Pulsing cyan — waiting for user
    LISTENING,  // Glowing green — actively recording
    PROCESSING, // Spinning — thinking
    SPEAKING    // Glowing blue — speaking response
}

/**
 * Foreground service that draws a floating AI buddy bubble over other apps.
 *
 * The bubble is draggable, tap-to-activate, and shows state-driven animations.
 * Inspired by chat-head style UIs (Facebook Messenger, HeyClicky).
 */
class FloatingBuddyService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: FrameLayout? = null
    private var isExpanded = false

    // State holders for Compose
    private val buddyState = mutableStateOf(BuddyState.IDLE)
    private val lastResponse = mutableStateOf("")
    private val lastQuery = mutableStateOf("")
    private val isExpandedState = mutableStateOf(false)

    companion object {
        private var instance: FloatingBuddyService? = null

        fun isRunning(): Boolean = instance != null

        fun updateState(state: BuddyState) {
            instance?.buddyState?.value = state
        }

        fun updateResponse(query: String, response: String) {
            instance?.lastQuery?.value = query
            instance?.lastResponse?.value = response
        }

        /**
         * Start the floating buddy service.
         */
        fun start(context: Context) {
            val intent = Intent(context, FloatingBuddyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the floating buddy service.
         */
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
        Log.i(TAG, "Floating buddy service started")
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingBubble()
        instance = null
        Log.i(TAG, "Floating buddy service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kairo AI Buddy",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Kairo AI Buddy floating on screen"
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
                .setContentTitle("Kairo AI Buddy")
                .setContentText("Tap the floating bubble to talk")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Kairo AI Buddy")
                .setContentText("Tap the floating bubble to talk")
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
                FloatingBubbleContent(
                    state = buddyState.value,
                    isExpanded = isExpandedState.value,
                    lastQuery = lastQuery.value,
                    lastResponse = lastResponse.value,
                    onTap = { handleBubbleTap() },
                    onClose = { stopSelf() },
                    onExpandToggle = { isExpandedState.value = !isExpandedState.value }
                )
            }
        }

        floatingView = FrameLayout(this).apply {
            addView(composeView)
        }

        // Make bubble draggable
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
                        // It was a tap, not a drag
                        handleBubbleTap()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun handleBubbleTap() {
        if (isExpandedState.value) {
            // Collapse if already expanded
            isExpandedState.value = false
            return
        }

        when (buddyState.value) {
            BuddyState.IDLE -> {
                // Open Kairo main activity for voice input
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("start_listening", true)
                }
                startActivity(intent)
            }
            BuddyState.LISTENING, BuddyState.PROCESSING -> {
                // Show expanded card with current state
                isExpandedState.value = true
            }
            BuddyState.SPEAKING -> {
                // Show expanded card with response
                isExpandedState.value = true
            }
        }
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

/**
 * Compose UI for the floating bubble and expandable conversation card.
 */
@Composable
private fun FloatingBubbleContent(
    state: BuddyState,
    isExpanded: Boolean,
    lastQuery: String,
    lastResponse: String,
    onTap: () -> Unit,
    onClose: () -> Unit,
    onExpandToggle: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End
    ) {
        // Main bubble
        BubbleOrb(state = state, onTap = onTap)

        // Expanded conversation card
        if (isExpanded && (lastQuery.isNotBlank() || lastResponse.isNotBlank())) {
            Spacer(modifier = Modifier.height(8.dp))
            ConversationCard(
                query = lastQuery,
                response = lastResponse,
                state = state,
                onClose = onClose
            )
        }
    }
}

/**
 * The floating orb/bubble with state-driven animations.
 */
@Composable
private fun BubbleOrb(
    state: BuddyState,
    onTap: () -> Unit
) {
    // Pulse animation for idle state
    val infiniteTransition = rememberInfiniteTransition(label = "bubble")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == BuddyState.IDLE) 1.08f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == BuddyState.IDLE) 2000 else 800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Glow color based on state
    val glowColor = when (state) {
        BuddyState.IDLE -> Color(0xFF00BCD4)       // Cyan
        BuddyState.LISTENING -> Color(0xFF4CAF50)   // Green
        BuddyState.PROCESSING -> Color(0xFFFF9800)  // Orange
        BuddyState.SPEAKING -> Color(0xFF2196F3)    // Blue
    }

    val bgGradient = Brush.radialGradient(
        colors = listOf(
            glowColor.copy(alpha = 0.9f),
            glowColor.copy(alpha = 0.6f),
            Color(0xFF1A1A2E)
        )
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(pulseScale)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = glowColor.copy(alpha = 0.4f),
                spotColor = glowColor.copy(alpha = 0.4f)
            )
            .clip(CircleShape)
            .background(bgGradient)
            .border(
                width = 2.dp,
                color = glowColor.copy(alpha = 0.7f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures { onTap() }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (state) {
                BuddyState.LISTENING -> Icons.Default.Mic
                else -> Icons.Default.Assistant
            },
            contentDescription = "Kairo AI Buddy",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * Expandable conversation card showing last query and response.
 */
@Composable
private fun ConversationCard(
    query: String,
    response: String,
    state: BuddyState,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(260.dp)
            .shadow(8.dp, shape = MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFF1A1A2E))
            .padding(12.dp)
    ) {
        Column {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (state) {
                        BuddyState.IDLE -> "Kairo"
                        BuddyState.LISTENING -> "Listening..."
                        BuddyState.PROCESSING -> "Thinking..."
                        BuddyState.SPEAKING -> "Speaking..."
                    },
                    color = Color(0xFF00BCD4),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // User query
            if (query.isNotBlank()) {
                Text(
                    text = query,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // AI response
            if (response.isNotBlank()) {
                Text(
                    text = response,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
