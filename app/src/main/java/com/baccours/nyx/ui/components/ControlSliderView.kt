package com.baccours.nyx.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.annotation.ColorInt
import androidx.core.content.withStyledAttributes
import androidx.core.view.ViewCompat
import com.baccours.nyx.R
import com.baccours.nyx.databinding.ViewControlSliderBinding

/**
 * Custom compound component representing a single control slider row (Icon + Label + Value + SeekBar).
 */
class ControlSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewControlSliderBinding = ViewControlSliderBinding.inflate(
        LayoutInflater.from(context), this
    )

    var onProgressChangeListener: ((progress: Int, fromUser: Boolean) -> Unit)? = null

    /** Whether the user is holding the slider. */
    val isDragging: Boolean get() = binding.slider.isPressed

    var progress: Int
        get() = binding.slider.progress
        set(value) {
            if (binding.slider.progress != value) {
                binding.slider.progress = value
            }
        }

    var max: Int
        get() = binding.slider.max
        set(value) {
            binding.slider.max = value
        }

    var labelText: CharSequence?
        get() = binding.label.text
        set(value) {
            binding.label.text = value
            binding.slider.contentDescription = value
        }

    var valueText: CharSequence?
        get() = binding.value.text
        set(value) {
            binding.value.text = value
            ViewCompat.setStateDescription(binding.slider, value)
        }

    init {
        orientation = VERTICAL

        binding.slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onProgressChangeListener?.invoke(progress, fromUser)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        context.withStyledAttributes(attrs, R.styleable.ControlSliderView, defStyleAttr, 0) {
            labelText = getString(R.styleable.ControlSliderView_sliderLabel)

            val iconRes = getResourceId(R.styleable.ControlSliderView_sliderIcon, 0)
            if (iconRes != 0) {
                binding.icon.setImageResource(iconRes)
            }

            val accent = getColor(R.styleable.ControlSliderView_sliderAccentColor, 0)
            if (accent != 0) {
                updateAccentColor(accent)
            }
        }
    }

    private fun updateAccentColor(@ColorInt color: Int) {
        val tint = ColorStateList.valueOf(color)
        binding.icon.imageTintList = tint
        binding.slider.progressTintList = tint
        binding.slider.thumbTintList = tint
        binding.value.setTextColor(tint)
    }

    // Prevent child view state conflicts between multiple instances sharing the same child IDs.
    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        dispatchThawSelfOnly(container)
    }
}
