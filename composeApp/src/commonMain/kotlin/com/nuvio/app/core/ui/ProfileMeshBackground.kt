package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp

private const val ProfileMeshDitherTileSize = 64

internal fun profileMeshDitherLevel(x: Int, y: Int): Int {
    var value = x * 0x1F123BB5 + y * 0x5F356495 + 0x68BC21EB
    value = value xor (value ushr 15)
    value *= 0x2C1B3C6D
    value = value xor (value ushr 12)
    return value ushr 31
}

private fun createProfileMeshDitherBrush(): ShaderBrush {
    val bitmap = ImageBitmap(
        width = ProfileMeshDitherTileSize,
        height = ProfileMeshDitherTileSize,
        config = ImageBitmapConfig.Argb8888,
        hasAlpha = true,
    )
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { blendMode = BlendMode.Src }
    for (y in 0 until ProfileMeshDitherTileSize) {
        for (x in 0 until ProfileMeshDitherTileSize) {
            paint.color = if (profileMeshDitherLevel(x, y) == 0) {
                Color.Transparent
            } else {
                Color.White.copy(alpha = 1f / 255f)
            }
            canvas.drawRect(
                Rect(
                    left = x.toFloat(),
                    top = y.toFloat(),
                    right = x + 1f,
                    bottom = y + 1f,
                ),
                paint,
            )
        }
    }
    bitmap.prepareToDraw()
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = ImageShader(
            image = bitmap,
            tileModeX = TileMode.Repeated,
            tileModeY = TileMode.Repeated,
        )
    }
}

@Composable
fun ProfileMeshBackground(
    profileColor: Color,
    modifier: Modifier = Modifier,
) {
    val ditherBrush = remember { createProfileMeshDitherBrush() }
    val animatedProfileColor by animateColorAsState(
        targetValue = profileColor,
        animationSpec = tween(durationMillis = 520),
        label = "profileMeshBackgroundColor",
    )
    val baseColor = Color.Black
    val primaryMeshColor = lerp(baseColor, animatedProfileColor, 0.58f)
    val secondaryMeshColor = lerp(animatedProfileColor, MaterialTheme.colorScheme.secondary, 0.32f)
    val tertiaryMeshColor = lerp(animatedProfileColor, MaterialTheme.colorScheme.tertiary, 0.28f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val maxDimension = maxOf(size.width, size.height)

                drawRect(color = baseColor, size = size)
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to primaryMeshColor.copy(alpha = 0.46f),
                            0.38f to primaryMeshColor.copy(alpha = 0.2f),
                            0.72f to primaryMeshColor.copy(alpha = 0.05f),
                            1f to Color.Transparent,
                        ),
                        center = Offset(size.width * -0.08f, size.height * -0.02f),
                        radius = maxDimension * 0.7f,
                    ),
                    size = size,
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to animatedProfileColor.copy(alpha = 0.24f),
                            0.44f to animatedProfileColor.copy(alpha = 0.09f),
                            0.78f to animatedProfileColor.copy(alpha = 0.02f),
                            1f to Color.Transparent,
                        ),
                        center = Offset(size.width * 0.16f, size.height * 0.18f),
                        radius = maxDimension * 0.46f,
                    ),
                    size = size,
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to secondaryMeshColor.copy(alpha = 0.16f),
                            0.5f to secondaryMeshColor.copy(alpha = 0.05f),
                            1f to Color.Transparent,
                        ),
                        center = Offset(size.width * 0.9f, size.height * 0.12f),
                        radius = maxDimension * 0.36f,
                    ),
                    size = size,
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to tertiaryMeshColor.copy(alpha = 0.08f),
                            0.52f to tertiaryMeshColor.copy(alpha = 0.03f),
                            1f to Color.Transparent,
                        ),
                        center = Offset(size.width * 0.28f, size.height * 0.4f),
                        radius = maxDimension * 0.28f,
                    ),
                    size = size,
                )
                // Skia's low-alpha radial ramps quantize visibly on an 8-bit desktop
                // surface. Add one deterministic least-significant bit before the
                // final blend so adjacent pixels can represent intermediate shades.
                drawRect(
                    brush = ditherBrush,
                    size = size,
                    blendMode = BlendMode.Plus,
                )
            },
    )
}
