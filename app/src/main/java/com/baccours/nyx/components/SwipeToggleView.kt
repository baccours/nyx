package com.baccours.nyx.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Switch
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.baccours.nyx.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A Material-3-flavored "slider" toggle.
 * Drag the thumb across the track to flip it.
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
            animateThumbTo(value)
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
    @ColorInt private var disabledOnSurfaceColor = 0
    @ColorInt private var disabledSurfaceColor = 0

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(dp(3f), 0f, dp(1f), "#40000000".toColorInt())
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

    private var animator: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        loadThemeColors(attrs, defStyleAttr)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            setLayerType(LAYER_TYPE_SOFTWARE, thumbPaint)
        }
    }

    private fun loadThemeColors(attrs: AttributeSet?, defStyleAttr: Int) {
        activeTrackColor = ContextCompat.getColor(context, R.color.color_primary_container)
        inactiveTrackColor = ContextCompat.getColor(context, R.color.color_surface_variant)
        thumbColor = ContextCompat.getColor(context, R.color.color_primary)
        thumbContentColor = ContextCompat.getColor(context, R.color.color_on_primary)
        disabledOnSurfaceColor = ContextCompat.getColor(context, R.color.color_on_surface)
        disabledSurfaceColor = ContextCompat.getColor(context, R.color.color_surface)

        context.withStyledAttributes(attrs, R.styleable.SwipeToggleView, defStyleAttr, 0) {
            activeTrackColor = getColor(R.styleable.SwipeToggleView_activeTrackColor, activeTrackColor)
            inactiveTrackColor = getColor(R.styleable.SwipeToggleView_inactiveTrackColor, inactiveTrackColor)
            thumbColor = getColor(R.styleable.SwipeToggleView_thumbColor, thumbColor)
            thumbContentColor = getColor(R.styleable.SwipeToggleView_thumbContentColor, thumbContentColor)
            isChecked = getBoolean(R.styleable.SwipeToggleView_checked, false)
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

        // Active (filled) portion behind the thumb.
        val progress = if (maxOffsetPx > 0f) (offsetX / maxOffsetPx).coerceIn(0f, 1f) else if (isChecked) 1f else 0f
        val activeAlpha = if (enabled) 0.2f + progress * 0.8f else 0.12f
        activeTrackPaint.color = withAlpha(activeTrackColor, activeAlpha)
        val activeTop = trackPaddingPx
        val activeBottom = h - trackPaddingPx
        val activeLeft = trackPaddingPx
        val activeRight = (trackPaddingPx + thumbSizePx + offsetX).coerceAtMost(width - trackPaddingPx)
        val activeRadius = (activeBottom - activeTop) / 2f
        canvas.drawRoundRect(activeLeft, activeTop, activeRight, activeBottom, activeRadius, activeRadius, activeTrackPaint)

        // Thumb.
        val cx = trackPaddingPx + offsetX + thumbSizePx / 2f
        val cy = h / 2f
        thumbPaint.color = if (enabled) thumbColor else withAlpha(disabledOnSurfaceColor, 0.38f)
        canvas.drawCircle(cx, cy, thumbSizePx / 2f, thumbPaint)

        val icon = if (isChecked) checkIcon else arrowIcon
        icon?.let {
            val iconSize = dp(24f).roundToInt()
            val left = (cx - iconSize / 2f).roundToInt()
            val top = (cy - iconSize / 2f).roundToInt()
            it.setBounds(left, top, left + iconSize, top + iconSize)
            it.setTint(if (enabled) thumbContentColor else disabledSurfaceColor)
            it.draw(canvas)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only the thumb itself is grabbable - the rest of the track ignores touches.
                if (!isTouchOnThumb(event.x, event.y)) return false

                animator?.cancel()
                downX = event.x
                downOffset = offsetX
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.x - downX
                if (!isDragging && abs(delta) > touchSlop) {
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
                    // A plain tap on the thumb, with no real drag: it does nothing.
                    animateThumbTo(isChecked)
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isDragging = false
                animateThumbTo(isChecked)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isTouchOnThumb(x: Float, y: Float): Boolean {
        val left = trackPaddingPx + offsetX
        val right = left + thumbSizePx
        val top = trackPaddingPx
        val bottom = top + thumbSizePx
        return x in left..right && y in top..bottom
    }

    /** performClick() only reachable via accessibility services like TalkBack. */
    override fun performClick(): Boolean {
        super.performClick()
        settleTo(!isChecked)
        return true
    }

    private fun settleTo(targetChecked: Boolean) {
        val changed = targetChecked != isChecked
        if (changed) {
            isChecked = targetChecked
            onCheckedChangeListener?.invoke(targetChecked)
        } else {
            animateThumbTo(targetChecked)
        }
    }

    private fun animateThumbTo(checked: Boolean, notify: Boolean = false) {
        if (maxOffsetPx <= 0f) {
            offsetX = if (checked) maxOffsetPx else 0f
            invalidate()
            return
        }
        val target = if (checked) maxOffsetPx else 0f
        if (offsetX == target && animator?.isRunning != true) {
            invalidate()
            if (notify) onCheckedChangeListener?.invoke(checked)
            return
        }

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
        /*info.className = Switch::class.java.name
        info.isCheckable = true
        info.isChecked = isChecked*/
        val compat = AccessibilityNodeInfoCompat.wrap(info)
        compat.className = Switch::class.java.name
        compat.isCheckable = true
        compat.setChecked(
            if (isChecked) AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE
            else AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE
        )
    }

    override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
        super.onPopulateAccessibilityEvent(event)
        event.isChecked = isChecked
    }

    private fun withAlpha(@ColorInt color: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color, (alpha.coerceIn(0f, 1f) * 255).roundToInt())

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
