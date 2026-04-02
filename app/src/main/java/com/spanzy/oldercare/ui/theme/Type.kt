package com.spanzy.oldercare.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/**
 * 字体大小档位映射表
 * Level 1 (标准): time 48sp, date 18sp, lunar/weekday 16sp
 * Level 2 (大):   time 56sp, date 20sp, lunar/weekday 18sp
 * Level 3 (较大): time 64sp, date 22sp, lunar/weekday 20sp
 * Level 4 (超大): time 72sp, date 24sp, lunar/weekday 22sp
 */
sealed class FontSizeLevel(val level: Int) {
    abstract val timeSp: TextUnit
    abstract val dateSp: TextUnit
    abstract val lunarSp: TextUnit

    data object Level1 : FontSizeLevel(1) {
        override val timeSp = 48.sp
        override val dateSp = 18.sp
        override val lunarSp = 16.sp
    }
    data object Level2 : FontSizeLevel(2) {
        override val timeSp = 56.sp
        override val dateSp = 20.sp
        override val lunarSp = 18.sp
    }
    data object Level3 : FontSizeLevel(3) {
        override val timeSp = 64.sp
        override val dateSp = 22.sp
        override val lunarSp = 20.sp
    }
    data object Level4 : FontSizeLevel(4) {
        override val timeSp = 72.sp
        override val dateSp = 24.sp
        override val lunarSp = 22.sp
    }

    companion object {
        fun fromLevel(level: Int): FontSizeLevel = when (level) {
            1 -> Level1
            2 -> Level2
            3 -> Level3
            4 -> Level4
            else -> Level4 // 默认超大
        }
    }
}

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
