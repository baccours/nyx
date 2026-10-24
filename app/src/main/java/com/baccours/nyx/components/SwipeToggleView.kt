package com.baccours.nyx.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Switch
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
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
            animateThumbTo(value)
        }

    private val trackHeightPx = dp(56f)
    private val thumbSizePx = dp(48f)
    private val trackPaddingPx = dp(4f)
    private val iconSizePx = dp(24f).roundToInt()

    private val trackPaint = paintOf(R.color.color_surface_variant)
    private val activeTrackPaint = paintOf(R.color.color_primary_container)
    private val thumbPaint = paintOf(R.color.color_primary).apply {
        setShadowLayer(dp(3f), 0f, dp(1f), ContextCompat.getColor(context, R.color.thumb_shadow))
    }
    private val trackRect = RectF()

    // Tinted and sized once; drawn translated to the thumb.
    private val checkIcon = loadIcon(R.drawable.ic_check)
    private val arrowIcon = loadIcon(R.drawable.ic_arrow_right)

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
        // Shadow layers need software rendering before API 28.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
    }

    private fun paintOf(@ColorRes colorRes: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, colorRes) }

    private fun loadIcon(@DrawableRes id: Int): Drawable? =
        ContextCompat.getDrawable(context, id)?.mutate()?.apply {
            setTint(ContextCompat.getColor(context, R.color.color_on_primary))
            setBounds(0, 0, iconSizePx, iconSizePx)
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
        val h = height.toFloat()

        // Track background.
        trackRect.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(trackRect, h / 2f, h / 2f, trackPaint)

        // Active (filled) portion behind the thumb; fades in as the thumb moves.
        val progress = if (maxOffsetPx > 0f) (offsetX / maxOffsetPx).coerceIn(0f, 1f) else if (isChecked) 1f else 0f
        activeTrackPaint.alpha = ((0.2f + progress * 0.8f) * 255).roundToInt()
        val activeTop = trackPaddingPx
        val activeBottom = h - trackPaddingPx
        val activeLeft = trackPaddingPx
        val activeRight = (trackPaddingPx + thumbSizePx + offsetX).coerceAtMost(width - trackPaddingPx)
        val activeRadius = (activeBottom - activeTop) / 2f
        canvas.drawRoundRect(activeLeft, activeTop, activeRight, activeBottom, activeRadius, activeRadius, activeTrackPaint)

        // Thumb.
        val cx = trackPaddingPx + offsetX + thumbSizePx / 2f
        val cy = h / 2f
        canvas.drawCircle(cx, cy, thumbSizePx / 2f, thumbPaint)

        (if (isChecked) checkIcon else arrowIcon)?.let {
            canvas.withTranslation(cx - iconSizePx / 2f, cy - iconSizePx / 2f) { it.draw(this) }
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
                        currentProgress > 1f - SWIPE_THRESHOLD
                    } else {
                        currentProgress >= SWIPE_THRESHOLD
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

    private fun animateThumbTo(checked: Boolean) {
        if (maxOffsetPx <= 0f) {
            offsetX = if (checked) maxOffsetPx else 0f
            invalidate()
            return
        }
        val target = if (checked) maxOffsetPx else 0f
        if (offsetX == target && animator?.isRunning != true) {
            invalidate()
            return
        }

        animator?.cancel()
        animator = ValueAnimator.ofFloat(offsetX, target).apply {
            duration = 200
            addUpdateListener {
                offsetX = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
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

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        /** How far (as a fraction of the track) the thumb must be dragged before it flips. */
        const val SWIPE_THRESHOLD = 0.9f
    }
}
