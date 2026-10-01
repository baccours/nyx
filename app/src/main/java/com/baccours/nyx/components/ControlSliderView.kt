package com.baccours.nyx.components

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.content.withStyledAttributes
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

    val binding: ViewControlSliderBinding = ViewControlSliderBinding.inflate(
        LayoutInflater.from(context), this
    )

    @ColorInt
    var accentColor: Int = 0
        set(value) {
            field = value
            updateAccentColor(value)
        }

    init {
        orientation = VERTICAL

        context.withStyledAttributes(attrs, R.styleable.ControlSliderView, defStyleAttr, 0) {
            getString(R.styleable.ControlSliderView_sliderLabel)?.let {
                binding.label.text = it
            }
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

    fun setValueText(text: String) {
        binding.value.text = text
    }

    private fun updateAccentColor(@ColorInt color: Int) {
        binding.icon.setColorFilter(color)
        binding.slider.progressTintList = ColorStateList.valueOf(color)
        binding.slider.thumbTintList = ColorStateList.valueOf(color)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        binding.slider.isEnabled = enabled
        binding.icon.isEnabled = enabled
        binding.label.isEnabled = enabled
        binding.value.isEnabled = enabled
        if (enabled && accentColor != 0) {
            binding.icon.setColorFilter(accentColor)
            binding.value.setTextColor(accentColor)
        } else if (!enabled) {
            binding.icon.clearColorFilter()
        }
    }
}
