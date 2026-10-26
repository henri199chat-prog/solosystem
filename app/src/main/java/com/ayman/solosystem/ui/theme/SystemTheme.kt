package com.ayman.solosystem.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * v1.2 — the System's two visual identities, ONE palette switch (no duplicated screens):
 *  - HOLO    : the classic dark-blue / cyan holographic System.
 *  - MONARCH : the violet / magenta "Shadow Monarch" System unlocked by the one-time awakening at level 100.
 * [blend] is snapshot state: 0 = HOLO, 1 = MONARCH; values in between are only used by the awakening cinematic.
 * Every themed colour in the app is a getter that reads [c], so changing [blend] re-tints frames, glows, the background
 * shader, buttons, windows, rings and charts everywhere at once.
 */
object SystemTheme {
    var blend by mutableFloatStateOf(0f)
    val monarch: Boolean get() = blend >= 0.5f

    /** Target blend for the persisted state: awakened and not switched back to classic → MONARCH. */
    fun target(awakened: Boolean, classic: Boolean) = if (awakened && !classic) 1f else 0f

    fun c(holo: Long, monarch: Long): Color {
        val b = blend
        return if (b <= 0f) Color(holo) else if (b >= 1f) Color(monarch) else lerp(Color(holo), Color(monarch), b)
    }
    fun c(holo: Color, monarch: Color): Color { val b = blend; return if (b <= 0f) holo else if (b >= 1f) monarch else lerp(holo, monarch, b) }

    // Monarch accents (from the reference: neon magenta / violet bars, cyan-white header glow, red failure lines)
    val MonarchPrimary = Color(0xFF9D4DFF)
    val MonarchSecondary = Color(0xFFE07BFF)
    val MonarchFrame = Color(0xFFB04DFF)
    val MonarchMagenta = Color(0xFFFF3DDB)
    val HeaderCyanWhite = Color(0xFFCFF7FF)

    /** Background-shader colours (linear 0..1 RGB) — interpolated and pushed as uniforms in the draw phase. */
    class ShaderColors(val baseIn: FloatArray, val baseOut: FloatArray, val fog1: FloatArray, val fog2: FloatArray, val web: FloatArray,
                       val burst: FloatArray, val mote: FloatArray, val streak: FloatArray)
    private val holoSh = ShaderColors(f(0.059f, 0.090f, 0.165f), f(0.008f, 0.024f, 0.090f), f(0.118f, 0.227f, 0.541f), f(0.192f, 0.180f, 0.506f),
        f(0.231f, 0.510f, 0.965f), f(0.133f, 0.827f, 0.933f), f(0.404f, 0.910f, 0.976f), f(0.30f, 0.60f, 1.0f))
    private val monSh = ShaderColors(f(0.075f, 0.035f, 0.130f), f(0.020f, 0.006f, 0.045f), f(0.330f, 0.090f, 0.560f), f(0.420f, 0.070f, 0.420f),
        f(0.600f, 0.270f, 0.980f), f(0.930f, 0.300f, 0.960f), f(0.820f, 0.560f, 1.000f), f(0.75f, 0.35f, 1.0f))
    private fun f(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)
    fun shader(pick: (ShaderColors) -> FloatArray): FloatArray {
        val a = pick(holoSh); val m = pick(monSh); val b = blend.coerceIn(0f, 1f)
        return floatArrayOf(a[0] + (m[0] - a[0]) * b, a[1] + (m[1] - a[1]) * b, a[2] + (m[2] - a[2]) * b)
    }

    /** Applies the Monarch override to the equipped cosmetic palette (notch count / double line kept). */
    fun palette(base: Palette): Palette {
        val b = blend
        if (b <= 0f) return base
        return base.copy(primary = lerp(base.primary, MonarchPrimary, b), secondary = lerp(base.secondary, MonarchSecondary, b), frame = lerp(base.frame, MonarchFrame, b))
    }
}
