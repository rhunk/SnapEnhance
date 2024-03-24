package me.rhunk.snapenhance.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp


val AppleLogo: ImageVector
    get() {
        return ImageVector.Builder(
            defaultWidth = 800.dp,
            defaultHeight = 800.dp,
            viewportWidth = 27f,
            viewportHeight = 27f,
        ).apply {
            group {
                path(
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
                    moveTo(15.769f, 0f)
                    curveToRelative(0.053f, 0f, 0.106f, 0f, 0.162f, 0f)
                    curveToRelative(0.13f, 1.606f, -0.483f, 2.806f, -1.228f, 3.675f)
                    curveToRelative(-0.731f, 0.863f, -1.732f, 1.7f, -3.351f, 1.573f)
                    curveToRelative(-0.108f, -1.583f, 0.506f, -2.694f, 1.25f, -3.561f)
                    curveTo(13.292f, 0.879f, 14.557f, 0.16f, 15.769f, 0f)
                    close()
                }
                path(
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
                    moveTo(20.67f, 16.716f)
                    curveToRelative(0f, 0.016f, 0f, 0.03f, 0f, 0.045f)
                    curveToRelative(-0.455f, 1.378f, -1.104f, 2.559f, -1.896f, 3.655f)
                    curveToRelative(-0.723f, 0.995f, -1.609f, 2.334f, -3.191f, 2.334f)
                    curveToRelative(-1.367f, 0f, -2.275f, -0.879f, -3.676f, -0.903f)
                    curveToRelative(-1.482f, -0.024f, -2.297f, 0.735f, -3.652f, 0.926f)
                    curveToRelative(-0.155f, 0f, -0.31f, 0f, -0.462f, 0f)
                    curveToRelative(-0.995f, -0.144f, -1.798f, -0.932f, -2.383f, -1.642f)
                    curveToRelative(-1.725f, -2.098f, -3.058f, -4.808f, -3.306f, -8.276f)
                    curveToRelative(0f, -0.34f, 0f, -0.679f, 0f, -1.019f)
                    curveToRelative(0.105f, -2.482f, 1.311f, -4.5f, 2.914f, -5.478f)
                    curveToRelative(0.846f, -0.52f, 2.009f, -0.963f, 3.304f, -0.765f)
                    curveToRelative(0.555f, 0.086f, 1.122f, 0.276f, 1.619f, 0.464f)
                    curveToRelative(0.471f, 0.181f, 1.06f, 0.502f, 1.618f, 0.485f)
                    curveToRelative(0.378f, -0.011f, 0.754f, -0.208f, 1.135f, -0.347f)
                    curveToRelative(1.116f, -0.403f, 2.21f, -0.865f, 3.652f, -0.648f)
                    curveToRelative(1.733f, 0.262f, 2.963f, 1.032f, 3.723f, 2.22f)
                    curveToRelative(-1.466f, 0.933f, -2.625f, 2.339f, -2.427f, 4.74f)
                    curveTo(17.818f, 14.688f, 19.086f, 15.964f, 20.67f, 16.716f)
                    close()
                }
            }
        }.build()
    }

