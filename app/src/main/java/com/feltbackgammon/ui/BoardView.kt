package com.feltbackgammon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.feltbackgammon.DieView
import com.feltbackgammon.Loc
import com.feltbackgammon.UiState
import com.feltbackgammon.engine.BAR
import com.feltbackgammon.engine.CubeOwner
import com.feltbackgammon.engine.OFF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Board layout in pixels. Points use absolute numbering from the human's side:
 * 1..6 bottom-right (human home), 7..12 bottom-left, 13..18 top-left, 19..24 top-right.
 * The bear-off tray sits on the right.
 */
class BoardGeometry(val w: Float, val h: Float) {
    val frame = h * 0.05f
    val trayW = w * 0.075f
    val left = frame
    val top = frame
    val bottom = h - frame
    val right = w - frame - trayW - frame * 0.7f
    val areaW = right - left
    val barW = areaW * 0.07f
    val pointW = (areaW - barW) / 12f
    val barLeft = left + pointW * 6f
    val barRight = barLeft + barW
    val barX = barLeft + barW / 2f
    val trayLeft = w - frame - trayW
    val trayRight = w - frame
    val trayX = (trayLeft + trayRight) / 2f
    val innerH = bottom - top
    val pointH = innerH * 0.41f
    val r = min(pointW * 0.45f, innerH / 22f)
    val midY = h / 2f
    val cubeSize = r * 1.75f
    val dieSize = r * 1.85f
    val slabH = min(r * 0.62f, innerH * 0.44f / 15f)

    fun col(abs: Int) = if (abs >= 13) abs - 13 else 12 - abs
    fun isTop(abs: Int) = abs >= 13
    fun colX(c: Int) = left + c * pointW + pointW / 2f + if (c >= 6) barW else 0f
    fun pointX(abs: Int) = colX(col(abs))

    fun spacing(count: Int): Float {
        val d = 2f * r
        if (count <= 1) return d
        // Keep each stack clear of the centre line where the dice sit.
        return min(d, (innerH / 2f - r * 3.1f) / (count - 1))
    }

    fun slot(abs: Int, slot: Int, count: Int): Offset {
        val sp = spacing(max(count, slot + 1))
        val x = pointX(abs)
        val y = if (isTop(abs)) top + r + slot * sp else bottom - r - slot * sp
        return Offset(x, y)
    }

    /** Human bar checkers sit below the centre, AI above. */
    fun bar(human: Boolean, slot: Int, count: Int): Offset {
        val sp = min(2f * r, (innerH / 2f - cubeSize / 2f - r * 1.2f) / max(1, count - 1).toFloat())
        val start = cubeSize / 2f + r * 1.15f
        val y = if (human) midY + start + slot * sp else midY - start - slot * sp
        return Offset(barX, y)
    }

    /** Human checkers stack up from the bottom of the tray, AI's down from the top. */
    fun off(human: Boolean, slot: Int): Offset {
        val y = if (human) bottom - slabH * 0.6f - slot * slabH else top + slabH * 0.6f + slot * slabH
        return Offset(trayX, y)
    }

    /** Returns the tapped location in the human's numbering: 1..24 point, 25 bar, 0 tray, null nothing. */
    fun hit(o: Offset): Int? {
        if (o.x >= trayLeft - frame * 0.4f) return OFF
        if (o.x in barLeft..barRight) return BAR
        if (o.x < left || o.x > right) return null
        val inRight = o.x > barRight
        val c = if (inRight) 6 + ((o.x - barRight) / pointW).toInt().coerceIn(0, 5)
        else ((o.x - left) / pointW).toInt().coerceIn(0, 5)
        return if (o.y < midY) 13 + c else 12 - c
    }
}

private val PointLight = Color(0xFFE6D8B8)
private val PointDark = Color(0xFF8A2A22)
private val Gold = Color(0xFFFFD45A)

private val HumanBase = Color(0xFFF3EBD8)
private val HumanEdge = Color(0xFFB9A57E)
private val HumanHi = Color(0xFFFFFFFF)
private val AiBase = Color(0xFF2B2624)
private val AiEdge = Color(0xFF0C0A09)
private val AiHi = Color(0xFF6A615B)

@Composable
fun Board(
    state: UiState,
    onTap: (Int?) -> Unit,
    onFlightFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val feltBrush = remember { ShaderBrush(ImageShader(Textures.feltImage, TileMode.Repeated, TileMode.Repeated)) }
    val woodBrush = remember { ShaderBrush(ImageShader(Textures.woodImage, TileMode.Mirror, TileMode.Mirror)) }
    val barBrush = remember {
        ShaderBrush(
            ImageShader(Textures.barImage, TileMode.Mirror, TileMode.Mirror)
        )
    }
    val measurer = rememberTextMeasurer()
    val tap by rememberUpdatedState(onTap)
    val finished by rememberUpdatedState(onFlightFinished)

    val flight = state.flight
    val flightAnim = remember { Animatable(0f) }
    LaunchedEffect(flight?.id) {
        if (flight != null) {
            flightAnim.snapTo(0f)
            flightAnim.animateTo(1f, tween(flight.durationMs, easing = FastOutSlowInEasing))
            finished(flight.id)
        }
    }

    val rollAnim = remember { Animatable(1f) }
    LaunchedEffect(state.rollId) {
        if (state.rolling) {
            rollAnim.snapTo(0f)
            rollAnim.animateTo(1f, tween(state.rollMs, easing = LinearOutSlowInEasing))
        }
    }

    val cubePos by animateFloatAsState(
        targetValue = when (state.cubeOwner) {
            CubeOwner.CENTER -> 0f
            CubeOwner.HUMAN -> 1f
            CubeOwner.AI -> -1f
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "cube",
    )

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseValue",
    )

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { o ->
                val g = BoardGeometry(size.width.toFloat(), size.height.toFloat())
                tap(g.hit(o))
            }
        }
    ) {
        val g = BoardGeometry(size.width, size.height)
        drawFrame(g, woodBrush, barBrush, feltBrush)
        drawPointNumbers(g, measurer)
        drawPoints(g)
        drawHighlights(g, state, if (state.targets.isEmpty()) 1f else pulse)
        drawCheckers(g, state, measurer)
        if (state.cubeVisible) drawCube(g, state.cubeValue, cubePos, measurer)
        val t = if (state.rolling) rollAnim.value else 1f
        drawDice(g, state.dice, t, state.rollId)
        if (flight != null) {
            val from = locOffset(g, state, flight.from, flight.human)
            val to = locOffset(g, state, flight.to, flight.human)
            val p = flightAnim.value
            val lift = sin(p * PI.toFloat())
            val pos = Offset(from.x + (to.x - from.x) * p, from.y + (to.y - from.y) * p - lift * g.r * 0.7f)
            drawChecker(pos, g.r * (1f + 0.16f * lift), flight.human, lift)
        }
    }
}

// ---------------------------------------------------------------- drawing helpers

private fun countAt(state: UiState, human: Boolean, abs: Int): Int =
    if (human) state.human[abs] else state.ai[25 - abs]

private fun locOffset(g: BoardGeometry, state: UiState, loc: Loc, human: Boolean): Offset = when (loc) {
    is Loc.Point -> g.slot(loc.abs, loc.slot, countAt(state, human, loc.abs) + 1)
    is Loc.Bar -> g.bar(loc.human, loc.slot, (if (loc.human) state.human[BAR] else state.ai[BAR]) + 1)
    is Loc.Off -> g.off(loc.human, loc.slot)
}

private fun DrawScope.drawFrame(g: BoardGeometry, wood: Brush, barWood: Brush, felt: Brush) {
    // Outer wood frame.
    drawRect(wood, size = size)
    // Bevel: light top-left edge, dark bottom-right edge.
    drawRect(Color.White.copy(alpha = 0.10f), size = Size(size.width, 3f))
    drawRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(0f, size.height - 4f), size = Size(size.width, 4f))

    // Felt playing fields (left and right of the bar).
    val leftField = Size(g.barLeft - g.left, g.innerH)
    val rightField = Size(g.right - g.barRight, g.innerH)
    drawRect(felt, topLeft = Offset(g.left, g.top), size = leftField)
    drawRect(felt, topLeft = Offset(g.barRight, g.top), size = rightField)
    feltShading(Offset(g.left, g.top), leftField)
    feltShading(Offset(g.barRight, g.top), rightField)

    // Bar.
    drawRect(barWood, topLeft = Offset(g.barLeft, g.top - g.frame * 0.2f), size = Size(g.barW, g.innerH + g.frame * 0.4f))
    drawRect(
        Brush.horizontalGradient(
            listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.45f)),
            startX = g.barLeft, endX = g.barRight
        ),
        topLeft = Offset(g.barLeft, g.top - g.frame * 0.2f), size = Size(g.barW, g.innerH + g.frame * 0.4f)
    )

    // Bear-off tray: recessed felt.
    val trayTop = Offset(g.trayLeft, g.top)
    val traySize = Size(g.trayW, g.innerH)
    drawRect(felt, topLeft = trayTop, size = traySize)
    drawRect(Color.Black.copy(alpha = 0.30f), topLeft = trayTop, size = traySize)
    feltShading(trayTop, traySize)
    // Divider across the middle of the tray.
    drawRect(wood, topLeft = Offset(g.trayLeft, g.midY - g.frame * 0.18f), size = Size(g.trayW, g.frame * 0.36f))
}

/** Vignette and inner shadow so the felt looks recessed under the frame. */
private fun DrawScope.feltShading(tl: Offset, s: Size) {
    val shadow = min(s.width, s.height) * 0.06f
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
            center = Offset(tl.x + s.width / 2f, tl.y + s.height / 2f),
            radius = max(s.width, s.height) * 0.75f
        ),
        topLeft = tl, size = s
    )
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), startY = tl.y, endY = tl.y + shadow), topLeft = tl, size = Size(s.width, shadow))
    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.40f), Color.Transparent), startX = tl.x, endX = tl.x + shadow), topLeft = tl, size = Size(shadow, s.height))
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)), startX = tl.x + s.width - shadow, endX = tl.x + s.width), topLeft = Offset(tl.x + s.width - shadow, tl.y), size = Size(shadow, s.height))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), startY = tl.y + s.height - shadow, endY = tl.y + s.height), topLeft = Offset(tl.x, tl.y + s.height - shadow), size = Size(s.width, shadow))
}

private fun pointPath(g: BoardGeometry, abs: Int): Path {
    val x = g.pointX(abs)
    val half = g.pointW * 0.47f
    return Path().apply {
        if (g.isTop(abs)) {
            moveTo(x - half, g.top)
            lineTo(x + half, g.top)
            lineTo(x, g.top + g.pointH)
        } else {
            moveTo(x - half, g.bottom)
            lineTo(x + half, g.bottom)
            lineTo(x, g.bottom - g.pointH)
        }
        close()
    }
}

private fun DrawScope.drawPoints(g: BoardGeometry) {
    for (abs in 1..24) {
        val c = if (g.col(abs) % 2 == 0) PointDark else PointLight
        val path = pointPath(g, abs)
        val (y0, y1) = if (g.isTop(abs)) g.top to g.top + g.pointH else g.bottom to g.bottom - g.pointH
        drawPath(path, Brush.verticalGradient(listOf(c, c.copy(alpha = 0.82f)), startY = y0, endY = y1))
        drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(width = 1.2f))
    }
}

private fun DrawScope.drawPointNumbers(g: BoardGeometry, m: TextMeasurer) {
    val style = TextStyle(color = Color(0xFFE8D3A8).copy(alpha = 0.75f), fontSize = (g.frame * 0.46f).toSp(), fontWeight = FontWeight.Medium)
    for (abs in 1..24) {
        val layout = m.measure(abs.toString(), style)
        val x = g.pointX(abs) - layout.size.width / 2f
        val y = if (g.isTop(abs)) (g.top - layout.size.height) / 2f else g.bottom + (g.frame - layout.size.height) / 2f
        drawText(layout, topLeft = Offset(x, y))
    }
}

private fun DrawScope.drawHighlights(g: BoardGeometry, state: UiState, pulse: Float) {
    // Legal destinations.
    for (t in state.targets) {
        if (t == OFF) {
            val tl = Offset(g.trayLeft, g.midY)
            drawRoundRect(Gold.copy(alpha = 0.22f * pulse + 0.08f), topLeft = tl, size = Size(g.trayW, g.innerH / 2f))
            drawRoundRect(Gold.copy(alpha = 0.9f * pulse), topLeft = tl, size = Size(g.trayW, g.innerH / 2f), style = Stroke(width = g.r * 0.12f))
        } else if (t in 1..24) {
            drawPath(pointPath(g, t), Gold.copy(alpha = 0.18f + 0.25f * pulse))
            val n = state.human[t]
            val landing = if (state.ai[25 - t] == 1) 0 else n
            val c = g.slot(t, landing, n + 1)
            drawCircle(Gold.copy(alpha = 0.35f * pulse), radius = g.r * 0.95f, center = c)
            drawCircle(Gold.copy(alpha = 0.95f), radius = g.r * 0.95f, center = c, style = Stroke(width = g.r * 0.1f))
        }
    }
}

private fun DrawScope.drawCheckers(g: BoardGeometry, state: UiState, m: TextMeasurer) {
    for (abs in 1..24) {
        drawStack(g, abs, state.ai[25 - abs], human = false, state, m)
        drawStack(g, abs, state.human[abs], human = true, state, m)
    }
    // Bar.
    for (i in 0 until state.ai[BAR]) drawChecker(g.bar(false, i, state.ai[BAR]), g.r, false)
    for (i in 0 until state.human[BAR]) {
        val c = g.bar(true, i, state.human[BAR])
        drawChecker(c, g.r, true)
        if (i == state.human[BAR] - 1) sourceRing(g, state, BAR, c)
    }
    // Borne off: side-on slabs.
    for (i in 0 until state.human[OFF]) drawSlab(g, g.off(true, i), true)
    for (i in 0 until state.ai[OFF]) drawSlab(g, g.off(false, i), false)
}

private fun DrawScope.drawStack(g: BoardGeometry, abs: Int, count: Int, human: Boolean, state: UiState, m: TextMeasurer) {
    if (count <= 0) return
    for (i in 0 until count) {
        val c = g.slot(abs, i, count)
        drawChecker(c, g.r, human)
        if (i == count - 1) {
            if (count > 5) {
                val style = TextStyle(
                    color = if (human) Color(0xFF5A4630) else Color(0xFFE9DCC2),
                    fontSize = (g.r * 0.85f).toSp(), fontWeight = FontWeight.Bold
                )
                val layout = m.measure(count.toString(), style)
                drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
            }
            if (human) sourceRing(g, state, abs, c)
        }
    }
}

private fun DrawScope.sourceRing(g: BoardGeometry, state: UiState, rel: Int, c: Offset) {
    if (state.selected == rel) {
        drawCircle(Gold.copy(alpha = 0.35f), radius = g.r * 1.18f, center = c)
        drawCircle(Gold, radius = g.r * 1.08f, center = c, style = Stroke(width = g.r * 0.16f))
    } else if (rel in state.sources) {
        drawCircle(Gold.copy(alpha = 0.75f), radius = g.r * 1.04f, center = c, style = Stroke(width = g.r * 0.08f))
    }
}

fun DrawScope.drawChecker(c: Offset, r: Float, human: Boolean, lift: Float = 0f) {
    val base = if (human) HumanBase else AiBase
    val edge = if (human) HumanEdge else AiEdge
    val hi = if (human) HumanHi else AiHi
    // Drop shadow grows as the checker lifts.
    drawCircle(
        Color.Black.copy(alpha = 0.38f - lift * 0.12f),
        radius = r * (0.98f + lift * 0.08f),
        center = c + Offset(r * (0.10f + lift * 0.35f), r * (0.14f + lift * 0.55f))
    )
    drawCircle(
        Brush.radialGradient(listOf(hi, base, edge), center = c - Offset(r * 0.35f, r * 0.4f), radius = r * 1.65f),
        radius = r, center = c
    )
    // Turned grooves.
    drawCircle(edge.copy(alpha = 0.65f), radius = r * 0.74f, center = c, style = Stroke(width = r * 0.07f))
    drawCircle(hi.copy(alpha = 0.35f), radius = r * 0.67f, center = c, style = Stroke(width = r * 0.035f))
    drawCircle(
        Brush.radialGradient(listOf(hi.copy(alpha = 0.35f), Color.Transparent), center = c - Offset(r * 0.25f, r * 0.3f), radius = r * 0.5f),
        radius = r * 0.6f, center = c
    )
    drawCircle(Color.Black.copy(alpha = 0.45f), radius = r, center = c, style = Stroke(width = r * 0.05f))
}

private fun DrawScope.drawSlab(g: BoardGeometry, c: Offset, human: Boolean) {
    val w = g.trayW * 0.78f
    val h = g.slabH * 0.86f
    val base = if (human) HumanBase else AiBase
    val edge = if (human) HumanEdge else AiEdge
    drawRoundRect(
        Brush.verticalGradient(listOf(base, edge), startY = c.y - h / 2f, endY = c.y + h / 2f),
        topLeft = Offset(c.x - w / 2f, c.y - h / 2f), size = Size(w, h), cornerRadius = CornerRadius(h * 0.4f)
    )
    drawRoundRect(
        Color.Black.copy(alpha = 0.4f), topLeft = Offset(c.x - w / 2f, c.y - h / 2f), size = Size(w, h),
        cornerRadius = CornerRadius(h * 0.4f), style = Stroke(width = 1f)
    )
}

private fun DrawScope.drawCube(g: BoardGeometry, value: Int, pos: Float, m: TextMeasurer) {
    val s = g.cubeSize
    val centerY = g.midY
    val edgeY = g.innerH / 2f - s / 2f - g.r * 0.15f
    val y = centerY + pos * edgeY
    val tl = Offset(g.barX - s / 2f, y - s / 2f)
    drawRoundRect(Color.Black.copy(alpha = 0.4f), topLeft = tl + Offset(s * 0.06f, s * 0.08f), size = Size(s, s), cornerRadius = CornerRadius(s * 0.16f))
    drawRoundRect(
        Brush.linearGradient(listOf(Color(0xFFFFF8E6), Color(0xFFD9C9A3)), start = tl, end = tl + Offset(s, s)),
        topLeft = tl, size = Size(s, s), cornerRadius = CornerRadius(s * 0.16f)
    )
    val label = if (value == 1) "64" else value.toString()
    val style = TextStyle(color = Color(0xFF2B1A0E), fontSize = (s * (if (label.length > 1) 0.42f else 0.55f)).toSp(), fontWeight = FontWeight.Bold)
    val layout = m.measure(label, style)
    drawText(layout, topLeft = Offset(g.barX - layout.size.width / 2f, y - layout.size.height / 2f))
}

private val PIPS = mapOf(
    1 to listOf(0.5f to 0.5f),
    2 to listOf(0.27f to 0.27f, 0.73f to 0.73f),
    3 to listOf(0.27f to 0.27f, 0.5f to 0.5f, 0.73f to 0.73f),
    4 to listOf(0.27f to 0.27f, 0.73f to 0.27f, 0.27f to 0.73f, 0.73f to 0.73f),
    5 to listOf(0.27f to 0.27f, 0.73f to 0.27f, 0.5f to 0.5f, 0.27f to 0.73f, 0.73f to 0.73f),
    6 to listOf(0.27f to 0.25f, 0.73f to 0.25f, 0.27f to 0.5f, 0.73f to 0.5f, 0.27f to 0.75f, 0.73f to 0.75f),
)

private fun DrawScope.drawDice(g: BoardGeometry, dice: List<DieView>, t: Float, rollId: Long) {
    if (dice.isEmpty()) return
    val leftCx = (g.left + g.barLeft) / 2f
    val rightCx = (g.barRight + g.right) / 2f
    val groups = dice.withIndex().groupBy { it.value.human }
    for ((human, items) in groups) {
        val n = items.size
        val size = if (n > 2) g.dieSize * 0.85f else g.dieSize
        val gap = size * 0.35f
        val total = n * size + (n - 1) * gap
        val cx = if (human) rightCx else leftCx
        items.forEachIndexed { k, iv ->
            val d = iv.value
            val idx = iv.index
            val finalX = cx - total / 2f + size / 2f + k * (size + gap)
            val rem = 1f - t
            val dir = if (human) 1f else -1f
            val x = finalX + rem * rem * dir * g.pointW * 2.2f * (1f + 0.3f * k)
            val bounce = abs(sin(t * PI.toFloat() * 3f)) * rem * size * 0.9f
            val y = g.midY - bounce
            val rot = rem * 600f * (if (idx % 2 == 0) 1f else -1f) + (if (t >= 1f) (idx * 7 % 9 - 4).toFloat() else 0f)
            val face = if (t >= 1f) d.value else (((rollId * 31 + idx * 17 + (t * 14f).toInt() * 7) % 6) + 1).toInt()
            drawDie(Offset(x, y), size, face, rot, human, if (d.used) 0.35f else 1f)
        }
    }
}

private fun DrawScope.drawDie(c: Offset, s: Float, value: Int, rotation: Float, human: Boolean, alpha: Float) {
    rotate(rotation, pivot = c) {
        val tl = Offset(c.x - s / 2f, c.y - s / 2f)
        drawRoundRect(Color.Black.copy(alpha = 0.4f * alpha), topLeft = tl + Offset(s * 0.07f, s * 0.09f), size = Size(s, s), cornerRadius = CornerRadius(s * 0.2f))
        val body = if (human) listOf(Color(0xFFFFFBF0), Color(0xFFDCCFB2)) else listOf(Color(0xFFB0322A), Color(0xFF6E1712))
        drawRoundRect(
            Brush.linearGradient(body, start = tl, end = tl + Offset(s, s)),
            topLeft = tl, size = Size(s, s), cornerRadius = CornerRadius(s * 0.2f), alpha = alpha
        )
        val pip = if (human) Color(0xFF1E1A17) else Color(0xFFF7EFDF)
        for ((fx, fy) in PIPS.getValue(value.coerceIn(1, 6))) {
            drawCircle(pip, radius = s * 0.085f, center = Offset(tl.x + fx * s, tl.y + fy * s), alpha = alpha)
        }
    }
}
