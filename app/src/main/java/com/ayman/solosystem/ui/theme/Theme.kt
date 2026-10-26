package com.ayman.solosystem.ui.theme

import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.R
import com.ayman.solosystem.domain.model.Txt
import com.ayman.solosystem.domain.rewards.RewardCatalog

object Sys {
    // themed (HOLO blue ↔ MONARCH violet, see SystemTheme)
    val bg get() = SystemTheme.c(0xFF04070D, 0xFF07030E)
    val bg2 get() = SystemTheme.c(0xFF081222, 0xFF120822)
    val panel get() = SystemTheme.c(0xE60A1322, 0xE6120A22)
    val panelLine get() = SystemTheme.c(0x66C9D3E8, 0x66DCC6F2)
    val text get() = SystemTheme.c(0xFFEFF6FF, 0xFFF7F0FF)
    val dim get() = SystemTheme.c(0xFF8FA3BF, 0xFFA898C4)
    val red = Color(0xFFFF3B4E)
    val green = Color(0xFF39F28A)
    val amber = Color(0xFFFFB547)
    val gold = Color(0xFFFFC447)
}

data class Palette(val primary: Color, val secondary: Color, val frame: Color, val notches: Int, val doubleLine: Boolean)

val LocalArabic = staticCompositionLocalOf { true }
val LocalPalette = staticCompositionLocalOf { Palette(Color(0xFF2F8CFF), Color(0xFF7FD4FF), Color(0xFF2F8CFF), 3, false) }
val LocalReducedMotion = staticCompositionLocalOf { false }

fun paletteFor(themeId: String, frameId: String): Palette {
    val t = RewardCatalog.get(themeId) ?: RewardCatalog.get("theme_azure")!!
    val f = RewardCatalog.get(frameId) ?: RewardCatalog.get("frame_standard")!!
    return Palette(Color(t.primary), Color(t.secondary), Color(f.primary), f.notches, f.doubleLine)
}

val Arabic = FontFamily(Font(R.font.tajawal_regular, FontWeight.Normal), Font(R.font.tajawal_medium, FontWeight.Medium), Font(R.font.tajawal_bold, FontWeight.Bold))
val Tech = FontFamily(Font(R.font.rajdhani_semibold, FontWeight.SemiBold), Font(R.font.rajdhani_bold, FontWeight.Bold))

@Composable fun tr(ar: String, en: String) = if (LocalArabic.current) ar else en
@Composable fun tr(t: Txt) = t.get(LocalArabic.current)

@Composable fun body(size: TextUnit = 15.sp, w: FontWeight = FontWeight.Normal, color: Color = Sys.text) = TextStyle(fontFamily = Arabic, fontSize = size, fontWeight = w, color = color, lineHeight = size * 1.5f, lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None))
@Composable fun tech(size: TextUnit = 16.sp, color: Color = Sys.text) = TextStyle(fontFamily = Tech, fontSize = size, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp)

@Composable fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    fun read() = try { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (_: Exception) { false }
    var reduced by remember { mutableStateOf(read()) }
    // Follow the system "Remove animations" switch live, without restarting the app.
    DisposableEffect(ctx) {
        val obs = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        ctx.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, obs)
        onDispose { ctx.contentResolver.unregisterContentObserver(obs) }
    }
    return reduced
}
