package dev.jacobv.undertow.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue

/**
 * Full-screen pause drawn from the accessibility service (TYPE_ACCESSIBILITY_OVERLAY,
 * so no extra "draw over apps" permission is needed). A slow breathing circle plus
 * two exits: walk away, or take an ever-shrinking snooze.
 */
class FrictionOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: View? = null
    private var breather: ValueAnimator? = null
    private var holdTimer: CountDownTimer? = null

    val isShowing: Boolean get() = root != null

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        appLabel: String,
        sessionMinutes: Long,
        strict: Boolean,
        onDone: () -> Unit,
        onSnooze: () -> Unit,
    ) {
        if (isShowing) return

        val dp = { v: Float ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics
            ).toInt()
        }

        val circle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#334FC3F7"))
                setStroke(dp(2f), Color.parseColor("#4FC3F7"))
            }
        }

        val title = TextView(context).apply {
            text = "You've been scrolling $appLabel for $sessionMinutes minutes"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(context).apply {
            text = "Take a breath. Is this still what you want to be doing?"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 15f
            gravity = Gravity.CENTER
        }

        fun pillButton(label: String, bg: Int, fg: Int) =
            Button(context).apply {
                text = label
                isAllCaps = false
                textSize = 16f
                setTextColor(fg)
                background = GradientDrawable().apply {
                    cornerRadius = dp(28f).toFloat()
                    setColor(bg)
                }
                setPadding(dp(32f), dp(14f), dp(32f), dp(14f))
            }

        val doneButton = pillButton(
            "I'm done — take me out",
            Color.parseColor("#4FC3F7"), Color.parseColor("#0B1F2A")
        ).apply { setOnClickListener { onDone() } }

        val snoozeLabel = if (strict) "Hold for a little longer" else "A little longer"
        val snoozeButton = pillButton(snoozeLabel, Color.parseColor("#22FFFFFF"), Color.WHITE)
        if (strict) {
            // Strict mode: snoozing costs a deliberate 3-second hold, not a reflex tap.
            snoozeButton.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        holdTimer = object : CountDownTimer(HOLD_MS, 1000L) {
                            override fun onTick(remainingMs: Long) {
                                snoozeButton.text = "Keep holding… ${remainingMs / 1000 + 1}"
                            }

                            override fun onFinish() {
                                if (isShowing) onSnooze()
                            }
                        }.start()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        holdTimer?.cancel()
                        holdTimer = null
                        snoozeButton.text = snoozeLabel
                        true
                    }
                    else -> false
                }
            }
        } else {
            snoozeButton.setOnClickListener { onSnooze() }
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(circle, LinearLayout.LayoutParams(dp(120f), dp(120f)).apply {
                bottomMargin = dp(40f)
            })
            addView(title, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12f) })
            addView(subtitle, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(48f) })
            addView(doneButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16f) })
            addView(snoozeButton)
        }

        // The back gesture must not be a free exit — the only ways out are the two
        // buttons. The window is focusable, so back arrives here as a key event;
        // swallow it.
        val container = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                if (event.keyCode == KeyEvent.KEYCODE_BACK) true
                else super.dispatchKeyEvent(event)
        }.apply {
            isFocusableInTouchMode = true
            setBackgroundColor(Color.parseColor("#F2081018"))
            addView(column, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                leftMargin = dp(32f); rightMargin = dp(32f)
            })
        }

        breather = ValueAnimator.ofFloat(1f, 1.25f).apply {
            duration = 4000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val s = it.animatedValue as Float
                circle.scaleX = s
                circle.scaleY = s
            }
            start()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(container, params)
        container.requestFocus()
        root = container
    }

    fun hide() {
        breather?.cancel()
        breather = null
        holdTimer?.cancel()
        holdTimer = null
        root?.let { windowManager.removeView(it) }
        root = null
    }

    companion object {
        private const val HOLD_MS = 3000L
    }
}
