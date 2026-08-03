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
            lineToRelative(2.6f, -2.6f)
            curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
            lineTo(20.0f, 8.69f)
            lineTo(20.0f, 5.0f)
            curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
            horizontalLineToRelative(-3.69f)
            lineToRelative(-2.6f, -2.6f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
            lineTo(8.69f, 4.0f)
            lineTo(5.0f, 4.0f)
            curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
            verticalLineToRelative(3.69f)
            lineToRelative(-2.6f, 2.6f)
            curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
            lineTo(4.0f, 15.3f)
            lineTo(4.0f, 19.0f)
            curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
            horizontalLineToRelative(3.69f)
            lineToRelative(2.6f, 2.6f)
            curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
            lineToRelative(2.6f, -2.6f)
            lineTo(19.0f, 20.0f)
            curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
            verticalLineToRelative(-3.69f)
            close()
            moveTo(12.0f, 16.9f)
            lineTo(12.0f, 7.1f)
            curveToRelative(0.0f, -0.61f, 0.55f, -1.11f, 1.15f, -0.99f)
            curveTo(15.91f, 6.65f, 18.0f, 9.08f, 18.0f, 12.0f)
            reflectiveCurveToRelative(-2.09f, 5.35f, -4.85f, 5.89f)
            curveToRelative(-0.6f, 0.12f, -1.15f, -0.38f, -1.15f, -0.99f)
            close()
        }.build().also { brightness = it }
    }
private var brightness: ImageVector? = null
