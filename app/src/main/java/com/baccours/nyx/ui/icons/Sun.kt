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
val Icons.Sun: ImageVector
    get() {
        val currentIcon = sun
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Sun",
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
            moveTo(6.76f, 4.84f)
            lineToRelative(-1.8f, -1.79f)
            lineToRelative(-1.41f, 1.41f)
            lineToRelative(1.79f, 1.79f)
            lineToRelative(1.42f, -1.41f)
            close()
            moveTo(4.0f, 10.5f)
            lineTo(1.0f, 10.5f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(3.0f)
            verticalLineToRelative(-2.0f)
            close()
            moveTo(13.0f, 0.55f)
            horizontalLineToRelative(-2.0f)
            lineTo(11.0f, 3.5f)
            horizontalLineToRelative(2.0f)
            lineTo(13.0f, 0.55f)
            close()
            moveTo(20.45f, 4.46f)
            lineToRelative(-1.41f, -1.41f)
            lineToRelative(-1.79f, 1.79f)
            lineToRelative(1.41f, 1.41f)
            lineToRelative(1.79f, -1.79f)
            close()
            moveTo(17.24f, 18.16f)
            lineToRelative(1.79f, 1.8f)
            lineToRelative(1.41f, -1.41f)
            lineToRelative(-1.8f, -1.79f)
            lineToRelative(-1.4f, 1.4f)
            close()
            moveTo(20.0f, 10.5f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(3.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(-3.0f)
            close()
            moveTo(12.0f, 5.5f)
            curveToRelative(-3.31f, 0.0f, -6.0f, 2.69f, -6.0f, 6.0f)
            reflectiveCurveToRelative(2.69f, 6.0f, 6.0f, 6.0f)
            reflectiveCurveToRelative(6.0f, -2.69f, 6.0f, -6.0f)
            reflectiveCurveToRelative(-2.69f, -6.0f, -6.0f, -6.0f)
            close()
            moveTo(11.0f, 22.45f)
            horizontalLineToRelative(2.0f)
            lineTo(13.0f, 19.5f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(2.95f)
            close()
            moveTo(3.55f, 18.54f)
            lineToRelative(1.41f, 1.41f)
            lineToRelative(1.79f, -1.8f)
            lineToRelative(-1.41f, -1.41f)
            lineToRelative(-1.79f, 1.8f)
            close()
        }.build().also { sun = it }
    }
private var sun: ImageVector? = null
