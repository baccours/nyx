package com.baccours.nyx.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("UnusedReceiverParameter")
val Icons.Brightness: ImageVector
    get() {
        val currentIcon = brightness
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Brightness",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f
        ).path(
            fill = SolidColor(Color(0xFF000000)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(20.0f, 15.31f)
            lineTo(23.31f, 12.0f)
            lineTo(20.0f, 8.69f)
            verticalLineTo(4.0f)
            horizontalLineToRelative(-4.69f)
            lineTo(12.0f, 0.69f)
            lineTo(8.69f, 4.0f)
            horizontalLineTo(4.0f)
            verticalLineToRelative(4.69f)
            lineTo(0.69f, 12.0f)
            lineTo(4.0f, 15.31f)
            verticalLineTo(20.0f)
            horizontalLineToRelative(4.69f)
            lineTo(12.0f, 23.31f)
            lineTo(15.31f, 20.0f)
            horizontalLineTo(20.0f)
            verticalLineToRelative(-4.69f)
            close()
            moveTo(12.0f, 18.0f)
            verticalLineTo(6.0f)
            curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
            reflectiveCurveToRelative(-2.69f, 6.0f, -6.0f, 6.0f)
            close()
        }.build().also { brightness = it }
    }
private var brightness: ImageVector? = null
