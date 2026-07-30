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
val Icons.Warning: ImageVector
    get() {
        val currentIcon = warning
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Warning",
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
            moveTo(4.47f, 21.0f)
            horizontalLineToRelative(15.06f)
            curveToRelative(1.54f, 0.0f, 2.5f, -1.67f, 1.73f, -3.0f)
            lineTo(13.73f, 4.99f)
            curveToRelative(-0.77f, -1.33f, -2.69f, -1.33f, -3.46f, 0.0f)
            lineTo(2.74f, 18.0f)
            curveToRelative(-0.77f, 1.33f, 0.19f, 3.0f, 1.73f, 3.0f)
            close()
            moveTo(12.0f, 14.0f)
            curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
            verticalLineToRelative(-2.0f)
            curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
            reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
            verticalLineToRelative(2.0f)
            curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
            close()
            moveTo(13.0f, 18.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(2.0f)
            close()
        }.build().also { warning = it }
    }
private var warning: ImageVector? = null
