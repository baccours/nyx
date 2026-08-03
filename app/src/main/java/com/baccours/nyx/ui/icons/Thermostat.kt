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
val Icons.Thermostat: ImageVector
    get() {
        val currentIcon = thermostat
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Thermostat",
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
            moveTo(15.0f, 13.0f)
            verticalLineTo(5.0f)
            curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
            reflectiveCurveTo(9.0f, 3.34f, 9.0f, 5.0f)
            verticalLineToRelative(8.0f)
            curveToRelative(-1.21f, 0.91f, -2.0f, 2.37f, -2.0f, 4.0f)
            curveToRelative(0.0f, 2.76f, 2.24f, 5.0f, 5.0f, 5.0f)
            reflectiveCurveToRelative(5.0f, -2.24f, 5.0f, -5.0f)
            curveTo(17.0f, 15.37f, 16.21f, 13.91f, 15.0f, 13.0f)
            close()
            moveTo(11.0f, 11.0f)
            verticalLineTo(5.0f)
            curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
            reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
            verticalLineToRelative(1.0f)
            horizontalLineToRelative(-1.0f)
            verticalLineToRelative(1.0f)
            horizontalLineToRelative(1.0f)
            verticalLineToRelative(1.0f)
            verticalLineToRelative(1.0f)
            horizontalLineToRelative(-1.0f)
            verticalLineToRelative(1.0f)
            horizontalLineToRelative(1.0f)
            verticalLineToRelative(1.0f)
            horizontalLineTo(11.0f)
            close()
        }.build().also { thermostat = it }
    }
private var thermostat: ImageVector? = null
