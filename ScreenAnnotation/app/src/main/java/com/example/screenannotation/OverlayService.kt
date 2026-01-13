package com.example.screenannotation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        var isRunning = false
            private set

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_annotation_channel"
    }

    private lateinit var windowManager: WindowManager
    private var toolbarView: View? = null
    private var drawingView: DrawingView? = null
    private var showButton: ImageButton? = null

    private var toolbarParams: WindowManager.LayoutParams? = null
    private var drawingParams: WindowManager.LayoutParams? = null
    private var showButtonParams: WindowManager.LayoutParams? = null

    private var isToolbarHidden = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        setupDrawingView()
        setupToolbar()
        setupShowButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removeViews()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_edit)
            .addAction(R.drawable.ic_close, getString(R.string.stop), stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun setupDrawingView() {
        drawingView = DrawingView(this)

        drawingParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        // 初始时不可触摸，等用户选择工具后再启用
        drawingParams?.flags = drawingParams?.flags?.or(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ) ?: 0

        windowManager.addView(drawingView, drawingParams)
    }

    private fun setupToolbar() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        toolbarView = inflater.inflate(R.layout.overlay_toolbar, null)

        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 20
            y = 200
        }

        setupToolbarButtons()
        windowManager.addView(toolbarView, toolbarParams)
    }

    private fun setupShowButton() {
        showButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_show)
            setBackgroundResource(R.drawable.toolbar_background)
            setPadding(24, 24, 24, 24)
            visibility = View.GONE
        }

        showButtonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 20
            y = 200
        }

        // 支持拖动和点击
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        showButton?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = showButtonParams?.x ?: 0
                    initialY = showButtonParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                    }
                    if (isDragging) {
                        showButtonParams?.let { params ->
                            params.x = initialX + dx.toInt()
                            params.y = initialY - dy.toInt()
                            windowManager.updateViewLayout(showButton, params)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        showToolbar()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(showButton, showButtonParams)
    }

    private fun setupToolbarButtons() {
        val btnArrow = toolbarView?.findViewById<ImageButton>(R.id.btnArrow)
        val btnRect = toolbarView?.findViewById<ImageButton>(R.id.btnRect)
        val btnFreehand = toolbarView?.findViewById<ImageButton>(R.id.btnFreehand)
        val btnHide = toolbarView?.findViewById<ImageButton>(R.id.btnHide)
        val btnClear = toolbarView?.findViewById<ImageButton>(R.id.btnClear)
        val btnClose = toolbarView?.findViewById<ImageButton>(R.id.btnClose)
        val dragHandle = toolbarView?.findViewById<View>(R.id.dragHandle)

        // 拖动手柄触摸监听
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = toolbarParams?.x ?: 0
                    initialY = toolbarParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    toolbarParams?.let { params ->
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY - (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(toolbarView, params)
                    }
                    true
                }
                else -> false
            }
        }

        btnArrow?.setOnClickListener {
            setDrawingMode(DrawingView.Mode.ARROW)
            updateButtonStates(btnArrow, btnRect, btnFreehand)
            hideToolbar(disableDrawing = false)
        }

        btnRect?.setOnClickListener {
            setDrawingMode(DrawingView.Mode.RECTANGLE)
            updateButtonStates(btnRect, btnArrow, btnFreehand)
            hideToolbar(disableDrawing = false)
        }

        btnFreehand?.setOnClickListener {
            setDrawingMode(DrawingView.Mode.FREEHAND)
            updateButtonStates(btnFreehand, btnArrow, btnRect)
            hideToolbar(disableDrawing = false)
        }

        btnHide?.setOnClickListener {
            hideToolbar()
        }

        btnClear?.setOnClickListener {
            drawingView?.clearDrawings()
        }

        btnClose?.setOnClickListener {
            stopSelf()
        }
    }

    private fun hideToolbar() {
        hideToolbar(disableDrawing = true)
    }

    private fun hideToolbar(disableDrawing: Boolean) {
        isToolbarHidden = true
        toolbarView?.visibility = View.GONE

        if (disableDrawing) {
            // 禁用绘图层触摸，保留图形显示
            drawingParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            drawingView?.let {
                windowManager.updateViewLayout(it, drawingParams)
            }
        }

        // 显示恢复按钮
        showButtonParams?.x = toolbarParams?.x ?: 20
        showButtonParams?.y = toolbarParams?.y ?: 200
        showButton?.visibility = View.VISIBLE
        windowManager.updateViewLayout(showButton, showButtonParams)

        if (disableDrawing) {
            Toast.makeText(this, R.string.toolbar_hidden_hint, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showToolbar() {
        isToolbarHidden = false
        toolbarView?.visibility = View.VISIBLE
        showButton?.visibility = View.GONE

        // 恢复绘图层触摸（如果之前选择了工具）
        if (drawingView?.currentMode != DrawingView.Mode.NONE) {
            drawingParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            drawingView?.let {
                windowManager.updateViewLayout(it, drawingParams)
            }
        }
    }

    private fun updateButtonStates(active: ImageButton?, vararg inactive: ImageButton?) {
        active?.alpha = 1.0f
        inactive.forEach { it?.alpha = 0.5f }
    }

    private fun setDrawingMode(mode: DrawingView.Mode) {
        drawingView?.currentMode = mode

        // 启用绘图层的触摸
        drawingParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        drawingView?.let {
            windowManager.updateViewLayout(it, drawingParams)
        }
    }

    private fun removeViews() {
        toolbarView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        drawingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        showButton?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        toolbarView = null
        drawingView = null
        showButton = null
    }
}
