package dev.javohir.podcast.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import kotlin.random.Random

/**
 * Episode/feed artwork with a generative fallback: when [url] is null or the
 * image fails to load, deterministic art derived from [seed] is drawn instead,
 * so the same show always gets the same cover.
 */
@Composable
fun Artwork(
    url: String?,
    seed: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    if (url.isNullOrBlank()) {
        GenerativeArt(seed = seed, modifier = modifier)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
            else -> GenerativeArt(seed = seed, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Deterministic abstract cover art: a layered composition (gradient base,
 * translucent orbs, sound ripples, a waveform, and the initial glyph) whose
 * palette and geometry are all derived from [seed].
 */
@Composable
fun GenerativeArt(seed: String, modifier: Modifier = Modifier) {
    val spec = remember(seed) { ArtSpec(seed) }
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        drawArt(spec, textMeasurer)
    }
}

private class Palette(
    val bg0: Color,
    val bg1: Color,
    val accent0: Color,
    val accent1: Color,
    val ink: Color
)

private val PALETTES = listOf(
    Palette(Color(0xFF1A1040), Color(0xFF3B1E78), Color(0xFFFF6F61), Color(0xFFFFC857), Color(0xFFFFF3E2)),
    Palette(Color(0xFF0B3D2E), Color(0xFF14594A), Color(0xFFA8E063), Color(0xFF56CCF2), Color(0xFFEFFBF3)),
    Palette(Color(0xFF4A0E2E), Color(0xFF7B1E4B), Color(0xFFFFB385), Color(0xFFF6D365), Color(0xFFFFEFE8)),
    Palette(Color(0xFF0F2557), Color(0xFF1B3B8B), Color(0xFF36D1DC), Color(0xFFE94FA1), Color(0xFFEAF4FF)),
    Palette(Color(0xFF23272E), Color(0xFF3A4150), Color(0xFFFF9F45), Color(0xFFF7D060), Color(0xFFF6F2EA)),
    Palette(Color(0xFF301B4D), Color(0xFF14365C), Color(0xFF00C9A7), Color(0xFFC56CF0), Color(0xFFF0EBFF)),
    Palette(Color(0xFF5A1E12), Color(0xFF93371E), Color(0xFFFFC15E), Color(0xFFE8A0BF), Color(0xFFFFF1DD)),
    Palette(Color(0xFF103C49), Color(0xFF1B5E6E), Color(0xFFF4A261), Color(0xFF9AE6B4), Color(0xFFEDFAFB)),
    Palette(Color(0xFF26134D), Color(0xFF5B2A86), Color(0xFFF95F8C), Color(0xFF6BD5E1), Color(0xFFFBEAF1)),
    Palette(Color(0xFF2F3E1E), Color(0xFF4F662F), Color(0xFFE9C46A), Color(0xFF8ECAE6), Color(0xFFF7F4E3))
)

private class Orb(val cx: Float, val cy: Float, val r: Float, val accent: Int, val alpha: Float)

/** All geometry precomputed as fractions of the canvas so any size renders the same art. */
private class ArtSpec(seed: String) {
    private val rnd = Random(seed.hashCode())

    val palette = PALETTES[rnd.nextInt(PALETTES.size)]
    val diagonalFlip = rnd.nextBoolean()

    val orbs = List(rnd.nextInt(4, 7)) {
        Orb(
            cx = rnd.nextFloat() * 1.4f - 0.2f,
            cy = rnd.nextFloat() * 1.4f - 0.2f,
            r = 0.25f + rnd.nextFloat() * 0.45f,
            accent = rnd.nextInt(2),
            alpha = 0.16f + rnd.nextFloat() * 0.18f
        )
    }

    val ringCx = 0.2f + rnd.nextFloat() * 0.6f
    val ringCy = 0.15f + rnd.nextFloat() * 0.5f
    val ringCount = rnd.nextInt(3, 6)
    val ringAccent = rnd.nextInt(2)

    val bars = List(rnd.nextInt(14, 22)) { 0.15f + rnd.nextFloat() * 0.85f }

    val letter = seed.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "♪"
}

private fun DrawScope.drawArt(spec: ArtSpec, textMeasurer: TextMeasurer) {
    val p = spec.palette
    val minDim = size.minDimension

    // Base: diagonal two-tone wash
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(p.bg0, p.bg1),
            start = if (spec.diagonalFlip) Offset(size.width, 0f) else Offset.Zero,
            end = if (spec.diagonalFlip) Offset(0f, size.height) else Offset(size.width, size.height)
        )
    )

    // Soft translucent orbs drifting across the frame
    for (orb in spec.orbs) {
        val color = if (orb.accent == 0) p.accent0 else p.accent1
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = orb.alpha), color.copy(alpha = 0f)),
                center = Offset(orb.cx * size.width, orb.cy * size.height),
                radius = (orb.r * minDim).coerceAtLeast(1f)
            ),
            radius = orb.r * minDim,
            center = Offset(orb.cx * size.width, orb.cy * size.height)
        )
    }

    // Sound ripples around a focal point
    val ringColor = (if (spec.ringAccent == 0) p.accent0 else p.accent1).copy(alpha = 0.45f)
    val ringCenter = Offset(spec.ringCx * size.width, spec.ringCy * size.height)
    for (i in 1..spec.ringCount) {
        drawCircle(
            color = ringColor,
            radius = minDim * 0.09f * i,
            center = ringCenter,
            style = Stroke(width = minDim * 0.012f)
        )
    }

    // Waveform along the bottom, mirrored around its baseline
    val barCount = spec.bars.size
    val slot = size.width / barCount
    val barWidth = slot * 0.55f
    val baseline = size.height * 0.84f
    val maxBarHalf = size.height * 0.10f
    spec.bars.forEachIndexed { i, h ->
        val half = maxBarHalf * h
        drawRoundRect(
            color = p.ink.copy(alpha = 0.5f),
            topLeft = Offset(slot * i + (slot - barWidth) / 2f, baseline - half),
            size = Size(barWidth, half * 2f),
            cornerRadius = CornerRadius(barWidth / 2f)
        )
    }

    // Initial glyph
    val layout = textMeasurer.measure(
        AnnotatedString(spec.letter),
        style = TextStyle(
            fontSize = (minDim * 0.48f).toSp(),
            fontWeight = FontWeight.Black,
            color = p.ink.copy(alpha = 0.92f)
        )
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            (size.width - layout.size.width) / 2f,
            size.height * 0.42f - layout.size.height / 2f
        )
    )
}
