package com.baccours.nyx.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.ColorUtils
import com.baccours.nyx.R
import kotlin.math.roundToInt

/**
 * A Material-3-flavored "slider" toggle, ported 1:1 (behaviourally) from the original
 * Jetpack Compose `SwipeToggle` composable. Drag the thumb across the track to flip it,
 * or tap it (e.g. via TalkBack / an accessibility service) to toggle it directly.
 */
class SwipeToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    var isChecked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            contentDescription = if (value) "On" else "Off"
            animateThumbTo(value, notify = false)
        }

    /** How far (as a fraction of the track) the thumb must be dragged before it flips. */
    var swipeThreshold: Float = 0.9f

    private val trackHeightPx = dp(56f)
    private val thumbSizePx = dp(48f)
    private val trackPaddingPx = dp(4f)

    @ColorInt private var activeTrackColor = 0
    @ColorInt private var inactiveTrackColor = 0
    @ColorInt private var thumbColor = 0
    @ColorInt private var thumbContentColor = 0

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(dp(3f), 0f, dp(1f), Color.parseColor("#40000000"))
    }
    private val trackRect = RectF()

    private val checkIcon = ContextCompat.getDrawable(context, R.drawable.ic_check)
    private val arrowIcon = ContextCompat.getDrawable(context, R.drawable.ic_arrow_right)

    /** Current thumb offset, in px, from the left edge of the (padded) track. */
    private var offsetX = 0f
    private var maxOffsetPx = 0f

    private var isDragging = false
    private var downX = 0f
    private var downOffset = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var velocityTracker: VelocityTracker? = null

    private var animator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
        loadThemeColors(attrs, defStyleAttr)
        setLayerType(LAYER_TYPE_SOFTWARE, thumbPaint)
    }

    private fun loadThemeColors(attrs: AttributeSet?, defStyleAttr: Int) {
        // Pull Material colors straight from the current theme, so the toggle always
        // matches whatever color scheme (light/dark) the app is currently using.
        activeTrackColor = resolveThemeColor(com.google.android.material.R.attr.colorPrimaryContainer)
        inactiveTrackColor = resolveThemeColor(com.google.android.material.R.attr.colorSurfaceVariant)
        thumbColor = resolveThemeColor(com.google.android.material.R.attr.colorPrimary)
        thumbContentColor = resolveThemeColor(com.google.android.material.R.attr.colorOnPrimary)

        context.withStyledAttributes(attrs, R.styleable.SwipeToggleView, defStyleAttr, 0) {
            activeTrackColor = getColor(R.styleable.SwipeToggleView_activeTrackColor, activeTrackColor)
            inactiveTrackColor = getColor(R.styleable.SwipeToggleView_inactiveTrackColor, inactiveTrackColor)
            thumbColor = getColor(R.styleable.SwipeToggleView_thumbColor, thumbColor)
            thumbContentColor = getColor(R.styleable.SwipeToggleView_thumbContentColor, thumbContentColor)
            isChecked = getBoolean(R.styleable.SwipeToggleView_checked, false)
        }
    }

    private fun resolveThemeColor(attr: Int): Int {
        val value = android.util.TypedValue()
        return if (context.theme.resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
        } else {
            Color.GRAY
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = trackHeightPx.roundToInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maxOffsetPx = (w - 2 * trackPaddingPx - thumbSizePx).coerceAtLeast(0f)
        offsetX = if (isChecked) maxOffsetPx else 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val enabled = isEnabled
        val h = height.toFloat()

        // Track background.
        trackPaint.color = if (enabled) inactiveTrackColor else withAlpha(inactiveTrackColor, 0.38f)
        trackRect.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(trackRect, h / 2f, h / 2f, trackPaint)

        // Active (filled) portion behind the thumb, matching the composable's
        // `activeTrackColor.copy(alpha = 0.2f + progress * 0.8f)` behaviour.
        val progress = if (maxOffsetPx > 0f) (offsetX / maxOffsetPx).coerceIn(0f, 1f) else if (isChecked) 1f else 0f
        val activeAlpha = if (enabled) 0.2f + progress * 0.8f else 0.12f
        activeTrackPaint.color = withAlpha(activeTrackColor, activeAlpha)
        val activeWidth = trackPaddingPx + thumbSizePx + offsetX
        canvas.drawRoundRect(0f, 0f, activeWidth.coerceAtMost(width.toFloat()), h, h / 2f, h / 2f, activeTrackPaint)

        // Thumb.
        val cx = trackPaddingPx + offsetX + thumbSizePx / 2f
        val cy = h / 2f
        thumbPaint.color = if (enabled) thumbColor else withAlpha(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface), 0.38f)
        canvas.drawCircle(cx, cy, thumbSizePx / 2f, thumbPaint)

        val icon = if (isChecked) checkIcon else arrowIcon
        icon?.let {
            val iconSize = dp(24f).roundToInt()
            val left = (cx - iconSize / 2f).roundToInt()
            val top = (cy - iconSize / 2f).roundToInt()
            it.setBounds(left, top, left + iconSize, top + iconSize)
            it.setTint(if (enabled) thumbContentColor else resolveThemeColor(com.google.android.material.R.attr.colorSurface))
            it.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        velocityTracker = velocityTracker ?: VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                downX = event.x
                downOffset = offsetX
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.x - downX
                if (!isDragging && Math.abs(delta) > touchSlop) {
                    isDragging = true
                }
                if (isDragging && maxOffsetPx > 0f) {
                    offsetX = (downOffset + delta).coerceIn(0f, maxOffsetPx)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (isDragging && maxOffsetPx > 0f) {
                    val currentProgress = offsetX / maxOffsetPx
                    val targetChecked = if (isChecked) {
                        currentProgress > (1f - swipeThreshold.coerceIn(0.01f, 1f))
                    } else {
                        currentProgress >= swipeThreshold.coerceIn(0.01f, 1f)
                    }
                    settleTo(targetChecked)
                } else {
                    // A plain tap (no real drag): treat it as an accessibility-style click.
                    performClick()
                }
                isDragging = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isDragging = false
                animateThumbTo(isChecked, notify = false)
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        settleTo(!isChecked)
        return true
    }

    /** Animates the thumb to reflect [targetChecked], notifying the listener if it changed. */
    private fun settleTo(targetChecked: Boolean) {
        val changed = targetChecked != isChecked
        isChecked = targetChecked
        if (changed) onCheckedChangeListener?.invoke(targetChecked)
    }

    private fun animateThumbTo(checked: Boolean, notify: Boolean) {
        if (maxOffsetPx <= 0f) {
            offsetX = if (checked) maxOffsetPx else 0f
            invalidate()
            return
        }
        val target = if (checked) maxOffsetPx else 0f
        if (offsetX == target && animator?.isRunning != true) return

        animator?.cancel()
        animator = ValueAnimator.ofFloat(offsetX, target).apply {
            duration = 200
            addUpdateListener {
                offsetX = it.animatedValue as Float
                invalidate()
            }
            if (notify) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onCheckedChangeListener?.invoke(checked)
                    }
                })
            }
            start()
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Switch::class.java.name
        info.isCheckable = true
        info.isChecked = isChecked
    }

    override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
        super.onPopulateAccessibilityEvent(event)
        event.isChecked = isChecked
    }

    private fun withAlpha(@ColorInt color: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color, (alpha.coerceIn(0f, 1f) * 255).roundToInt())

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
