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
val Icons.CheckCircle: ImageVector
    get() {
        val currentIcon = checkCircle
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Check Circle",
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
            moveTo(12.0f, 2.0f)
            curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
            reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
            reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
            reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
            close()
            moveTo(9.29f, 16.29f)
            lineTo(5.7f, 12.7f)
            curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0.0f, -1.41f)
            curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
            lineTo(10.0f, 14.17f)
            lineToRelative(6.88f, -6.88f)
            curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
            curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
            lineToRelative(-7.59f, 7.59f)
            curveToRelative(-0.38f, 0.39f, -1.02f, 0.39f, -1.41f, 0.0f)
            close()
        }.build().also { checkCircle = it }
    }
private var checkCircle: ImageVector? = null
