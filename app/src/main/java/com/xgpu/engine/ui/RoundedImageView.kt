package com.xgpu.engine.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView biasa - clip ditangani oleh CardView parent.
 * Border putih ditangani oleh foreground drawable di CardView.
 */
class RoundedImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs)
