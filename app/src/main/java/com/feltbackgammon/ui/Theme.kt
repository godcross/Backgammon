package com.feltbackgammon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ivory = Color(0xFFF3EBD8)
val Brass = Color(0xFFD9A93F)
val DeepWood = Color(0xFF2A160A)
val FeltGreen = Color(0xFF1E5A35)

@Composable
fun FeltTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Brass,
            onPrimary = DeepWood,
            secondary = Ivory,
            surface = Color(0xFF2E1C10),
            onSurface = Ivory,
            background = DeepWood,
            onBackground = Ivory,
        ),
        content = content,
    )
}

/** Full-screen felt with a wood border, used behind the menu screens. */
@Composable
fun FeltBackground(content: @Composable BoxScope.() -> Unit) {
    val felt = remember { ShaderBrush(ImageShader(Textures.feltImage, TileMode.Repeated, TileMode.Repeated)) }
    val wood = remember { ShaderBrush(ImageShader(Textures.woodImage, TileMode.Mirror, TileMode.Mirror)) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(wood)
                val b = size.minDimension * 0.035f
                drawRect(felt, topLeft = androidx.compose.ui.geometry.Offset(b, b), size = androidx.compose.ui.geometry.Size(size.width - 2 * b, size.height - 2 * b))
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        center = center, radius = size.maxDimension * 0.7f
                    )
                )
            },
        content = content,
    )
}

@Composable
fun GameButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false) {
    if (primary) {
        Button(
            onClick = onClick, enabled = enabled, modifier = modifier,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Brass, contentColor = DeepWood,
                disabledContainerColor = Brass.copy(alpha = 0.25f), disabledContentColor = DeepWood.copy(alpha = 0.5f)
            ),
        ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    } else {
        OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = modifier,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            border = BorderStroke(1.5.dp, if (enabled) Ivory.copy(alpha = 0.7f) else Ivory.copy(alpha = 0.2f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ivory, disabledContentColor = Ivory.copy(alpha = 0.35f)),
        ) { Text(text, fontSize = 15.sp) }
    }
}

/** A row of selectable chips (one choice). */
@Composable
fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            val on = o == selected
            Button(
                onClick = { onSelect(o) },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (on) Brass else Color.Black.copy(alpha = 0.35f),
                    contentColor = if (on) DeepWood else Ivory,
                ),
            ) { Text(label(o), fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

val TitleFont = FontFamily.Serif

fun Modifier.panel(): Modifier = this.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
