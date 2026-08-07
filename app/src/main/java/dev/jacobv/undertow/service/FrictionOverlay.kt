package dev.jacobv.undertow.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
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

    val isShowing: Boolean get() = root != null

    fun show(appLabel: String, sessionMinutes: Long, onDone: () -> Unit, onSnooze: () -> Unit) {
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

        fun pillButton(label: String, bg: Int, fg: Int, onClick: () -> Unit) =
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
                setOnClickListener { onClick() }
            }

        val doneButton = pillButton(
            "I'm done — take me out",
            Color.parseColor("#4FC3F7"), Color.parseColor("#0B1F2A")
        ) { onDone() }

        val snoozeButton = pillButton(
            "A little longer",
            Color.parseColor("#22FFFFFF"), Color.WHITE
        ) { onSnooze() }

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

        val container = FrameLayout(context).apply {
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
        root = container
    }

    fun hide() {
        breather?.cancel()
        breather = null
        root?.let { windowManager.removeView(it) }
        root = null
    }
}
