package com.ai.assistance.operit.services.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.floating.FloatingChatWindow

interface FloatingWindowCallback {
    fun onClose()
    fun onSendMessage(message: String)
    fun onAttachmentRequest(request: String)
    fun onRemoveAttachment(filePath: String)
    fun getMessages(): List<ChatMessage>
    fun getAttachments(): List<AttachmentInfo>
    fun saveState()
}

class FloatingWindowManager(
        private val context: Context,
        private val state: FloatingWindowState,
        private val lifecycleOwner: LifecycleOwner,
        private val viewModelStoreOwner: ViewModelStoreOwner,
        private val savedStateRegistryOwner: SavedStateRegistryOwner,
        private val callback: FloatingWindowCallback
) {
    private val TAG = "FloatingWindowManager"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var isViewAdded = false

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isViewAdded) return

        try {
            composeView =
                    ComposeView(context).apply {
                        setViewTreeLifecycleOwner(lifecycleOwner)
                        setViewTreeViewModelStoreOwner(viewModelStoreOwner)
                        setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

                        setContent { MaterialTheme { FloatingChatUi() } }
                    }

            val params = createLayoutParams()
            windowManager.addView(composeView, params)
            isViewAdded = true
            Log.d(TAG, "Floating view added at (${params.x}, ${params.y})")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating view", e)
        }
    }

    fun destroy() {
        if (isViewAdded) {
            composeView?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing view", e)
                }
                composeView = null
                isViewAdded = false
            }
        }
    }

    @Composable
    private fun FloatingChatUi() {
        FloatingChatWindow(
                messages = callback.getMessages(),
                width = state.windowWidth.value,
                height = state.windowHeight.value,
                windowScale = state.windowScale.value,
                onScaleChange = { newScale ->
                    state.windowScale.value = newScale.coerceIn(0.5f, 1.0f)
                    updateWindowSizeInLayoutParams()
                    callback.saveState()
                },
                onClose = { callback.onClose() },
                onResize = { newWidth, newHeight ->
                    state.windowWidth.value = newWidth
                    state.windowHeight.value = newHeight
                    updateWindowSizeInLayoutParams()
                    callback.saveState()
                },
                isBallMode = state.isBallMode.value,
                ballSize = state.ballSize.value,
                onToggleBallMode = { onToggleBallMode() },
                onMove = { dx, dy, scale -> onMove(dx, dy, scale) },
                saveWindowState = { callback.saveState() },
                onSendMessage = { callback.onSendMessage(it) },
                onInputFocusRequest = { setFocusable(it) },
                attachments = callback.getAttachments(),
                onAttachmentRequest = { callback.onAttachmentRequest(it) },
                onRemoveAttachment = { callback.onRemoveAttachment(it) }
        )
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                )
        params.gravity = Gravity.TOP or Gravity.START

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        if (state.isBallMode.value) {
            val ballSizeInPx = (state.ballSize.value.value * density).toInt()
            val safeMargin = (16 * density).toInt()
            val minVisible = ballSizeInPx / 2
            state.x =
                    state.x.coerceIn(
                            -ballSizeInPx + minVisible + safeMargin,
                            screenWidth - minVisible - safeMargin
                    )
            state.y = state.y.coerceIn(safeMargin, screenHeight - minVisible - safeMargin)
        } else {
            val windowWidth =
                    (state.windowWidth.value.value * density * state.windowScale.value).toInt()
            val windowHeight =
                    (state.windowHeight.value.value * density * state.windowScale.value).toInt()
            val minVisibleWidth = (windowWidth * 2 / 3)
            val safeMargin = (20 * density).toInt()
            state.x =
                    state.x.coerceIn(
                            -(windowWidth - minVisibleWidth) + safeMargin,
                            screenWidth - minVisibleWidth - safeMargin
                    )
            state.y = state.y.coerceIn(safeMargin, screenHeight - (windowHeight / 2) - safeMargin)
        }

        params.x = state.x
        params.y = state.y
        return params
    }

    private fun updateWindowSizeInLayoutParams() {
        updateViewLayout { params ->
            val density = context.resources.displayMetrics.density
            val scale = state.windowScale.value
            val widthDp = state.windowWidth.value
            val heightDp = state.windowHeight.value
            params.width = (widthDp.value * density * scale).toInt()
            params.height = (heightDp.value * density * scale).toInt()
        }
    }

    private fun updateViewLayout(configure: (WindowManager.LayoutParams) -> Unit = {}) {
        composeView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            configure(params)
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun calculateCenteredPosition(
            fromX: Int,
            fromY: Int,
            fromWidth: Int,
            fromHeight: Int,
            toWidth: Int,
            toHeight: Int
    ): Pair<Int, Int> {
        val centerX = fromX + fromWidth / 2
        val centerY = fromY + fromHeight / 2
        val newX = centerX - toWidth / 2
        val newY = centerY - toHeight / 2
        return Pair(newX, newY)
    }

    private fun onToggleBallMode() {
        if (state.isTransitioning) return
        state.isTransitioning = true

        val view = composeView ?: return
        val currentParams = view.layoutParams as WindowManager.LayoutParams

        if (state.isBallMode.value) {
            state.lastBallPositionX = currentParams.x
            state.lastBallPositionY = currentParams.y
        } else {
            state.lastWindowPositionX = currentParams.x
            state.lastWindowPositionY = currentParams.y
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        val currentWidth: Int
        val currentHeight: Int

        if (state.isBallMode.value) {
            currentWidth = (state.ballSize.value.value * density).toInt()
            currentHeight = currentWidth
        } else {
            state.lastWindowScale = state.windowScale.value
            currentWidth =
                    (state.windowWidth.value.value * density * state.windowScale.value).toInt()
            currentHeight =
                    (state.windowHeight.value.value * density * state.windowScale.value).toInt()
        }

        state.isBallMode.value = !state.isBallMode.value
        callback.saveState()

        updateViewLayout { params ->
            if (state.isBallMode.value) {
                state.windowScale.value = 1.0f
                val ballSizeInPx = (state.ballSize.value.value * density).toInt()
                params.width = ballSizeInPx
                params.height = ballSizeInPx

                if (state.lastBallPositionX != 0 && state.lastBallPositionY != 0) {
                    params.x = state.lastBallPositionX
                    params.y = state.lastBallPositionY
                } else {
                    val (centeredX, centeredY) =
                            calculateCenteredPosition(
                                    params.x,
                                    params.y,
                                    currentWidth,
                                    currentHeight,
                                    ballSizeInPx,
                                    ballSizeInPx
                            )
                    params.x = centeredX
                    params.y = centeredY
                }

                val margin = (16 * density).toInt()
                params.x = params.x.coerceIn(margin, screenWidth - ballSizeInPx - margin)
                params.y = params.y.coerceIn(margin, screenHeight - ballSizeInPx - margin)

                state.x = params.x
                state.y = params.y
            } else {
                state.windowScale.value = state.lastWindowScale
                val newWidth = (state.windowWidth.value.value * density * state.windowScale.value).toInt()
                val newHeight = (state.windowHeight.value.value * density * state.windowScale.value).toInt()
                params.width = newWidth
                params.height = newHeight

                if (state.lastWindowPositionX != 0 && state.lastWindowPositionY != 0) {
                    params.x = state.lastWindowPositionX
                    params.y = state.lastWindowPositionY
                } else {
                    val (centeredX, centeredY) =
                            calculateCenteredPosition(
                                    params.x,
                                    params.y,
                                    currentWidth,
                                    currentHeight,
                                    newWidth,
                                    newHeight
                            )
                    params.x = centeredX
                    params.y = centeredY
                }

                val minVisibleWidth = newWidth / 3
                val minVisibleHeight = newHeight / 3
                params.x =
                        params.x.coerceIn(
                                -newWidth + minVisibleWidth,
                                screenWidth - minVisibleWidth
                        )
                params.y = params.y.coerceIn(0, screenHeight - minVisibleHeight)

                state.x = params.x
                state.y = params.y
            }
        }

        Handler(Looper.getMainLooper())
                .postDelayed({ state.isTransitioning = false }, state.transitionDebounceTime)
    }

    private fun onMove(dx: Float, dy: Float, currentScale: Float) {
        updateViewLayout { params ->
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density

            state.windowScale.value = currentScale

            val sensitivity = if (state.isBallMode.value) 0.9f else currentScale
            params.x += (dx * sensitivity).toInt()
            params.y += (dy * sensitivity).toInt()

            if (state.isBallMode.value) {
                val ballSize = (state.ballSize.value.value * density).toInt()
                val minVisible = ballSize / 2
                params.x = params.x.coerceIn(-ballSize + minVisible, screenWidth - minVisible)
                params.y = params.y.coerceIn(0, screenHeight - minVisible)
            } else {
                val windowWidth = (state.windowWidth.value.value * density * currentScale).toInt()
                val windowHeight = (state.windowHeight.value.value * density * currentScale).toInt()
                val minVisibleWidth = (windowWidth * 2 / 3)
                val minVisibleHeight = (windowHeight * 2 / 3)
                params.x =
                        params.x.coerceIn(
                                -(windowWidth - minVisibleWidth),
                                screenWidth - minVisibleWidth / 2
                        )
                params.y = params.y.coerceIn(0, screenHeight - minVisibleHeight)
            }
            state.x = params.x
            state.y = params.y
        }
    }

    private fun setFocusable(needsFocus: Boolean) {
        val view = composeView ?: return
        if (needsFocus) {
            updateViewLayout { params ->
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            }
            Handler(Looper.getMainLooper())
                    .postDelayed(
                            {
                                view.requestFocus()
                                val imm =
                                        context.getSystemService(Context.INPUT_METHOD_SERVICE) as?
                                                InputMethodManager
                                imm?.showSoftInput(
                                        view.findFocus(),
                                        InputMethodManager.SHOW_IMPLICIT
                                )
                            },
                            200
                    )
        } else {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
            Handler(Looper.getMainLooper())
                    .postDelayed(
                            {
                                updateViewLayout { params ->
                                    params.flags =
                                            params.flags or
                                                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                }
                            },
                            100
                    )
        }
    }
}
