package com.baccours.nyx.components

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.ColorUtils
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

    private var onProgressChangeListener: ((progress: Int, fromUser: Boolean) -> Unit)? = null

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

    @ColorInt
    var accentColor: Int = 0
        set(value) {
            field = value
            updateAccentColor(value)
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
            valueText = getString(R.styleable.ControlSliderView_sliderValueText)
            max = getInt(R.styleable.ControlSliderView_sliderMax, 100)
            progress = getInt(R.styleable.ControlSliderView_sliderProgress, 0)

            val iconRes = getResourceId(R.styleable.ControlSliderView_sliderIcon, 0)
            if (iconRes != 0) {
                binding.icon.setImageResource(iconRes)
            }

            val accent = getColor(R.styleable.ControlSliderView_sliderAccentColor, 0)
            if (accent != 0) {
                accentColor = accent
            }
        }
    }

    fun setOnProgressChangeListener(listener: (progress: Int, fromUser: Boolean) -> Unit) {
        this.onProgressChangeListener = listener
    }

    private fun updateAccentColor(@ColorInt color: Int) {
        val disabledColor = ColorUtils.setAlphaComponent(
            ContextCompat.getColor(context, R.color.color_on_surface_variant),
            (0.5f * 255).toInt()
        )
        val stateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(color, disabledColor)
        )

        binding.icon.imageTintList = stateList
        binding.slider.progressTintList = stateList
        binding.slider.thumbTintList = stateList
        binding.value.setTextColor(stateList)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        binding.slider.isEnabled = enabled
        binding.icon.isEnabled = enabled
        binding.label.isEnabled = enabled
        binding.value.isEnabled = enabled
    }
}
