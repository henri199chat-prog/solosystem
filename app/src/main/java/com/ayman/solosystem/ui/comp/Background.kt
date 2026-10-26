package com.ayman.solosystem.ui.comp

import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import com.ayman.solosystem.R
import com.ayman.solosystem.domain.model.BgFx
import com.ayman.solosystem.ui.theme.LocalReducedMotion
import com.ayman.solosystem.ui.theme.SystemTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.random.Random

/*
 * "Digital Realm" animated background (reference: the anime's system screen), bottom → top:
 *  1. radial gradient #0F172A centre → #020617 edges
 *  2. slow nebula fog (#1E3A8A / #312E81) from two baked tileable noise fields, domain-warped, 18 s undulation
 *  3. crystalline tech web (#3B82F6 ~15–20 %) from a baked 2048² shattered-glass network, near + far parallax layers
 *     - travelling light dashes along a subset of edges, and electric bursts (#22D3EE → white) every 0.5–1.5 s that
 *       snap outward along the lines from a random point and decay in ~0.3 s
 *  4. glowing cyan (#67E8F9) motes with bloom in two depth layers (in-shader) + near bokeh motes (one Canvas pass)
 *  5. soft diagonal light streaks, fine horizontal scanlines (~5 %) and vignette
 * Layers 1–5 are ONE AGSL RuntimeShader draw on API 33+. The whole background lives in its own RenderNode and reads
 * the shared FX clock only in the draw phase, so nothing recomposes per frame.
 */
private const val DIGITAL_REALM = """
uniform shader web;
uniform shader aux;
uniform float2 res;
uniform float t;
uniform float k;
uniform float hq;
uniform float dens;
uniform float3 cBaseIn;
uniform float3 cBaseOut;
uniform float3 cFog1;
uniform float3 cFog2;
uniform float3 cWeb;
uniform float3 cBurst;
uniform float3 cMote;
uniform float3 cStreak;
uniform float evo;

float h1(float n) { return fract(sin(n) * 43758.5453); }
float2 h2(float2 p) { return fract(sin(float2(dot(p, float2(127.1, 311.7)), dot(p, float2(269.5, 183.3)))) * 43758.5453); }

float motes(float2 p, float cell, float spd, float sz, float seed, float density) {
  p.y += t * spd;
  p.x += t * spd * 0.16 + sin(t * 0.25 + seed) * cell * 0.25;
  float2 id = floor(p / cell);
  float2 f = fract(p / cell) - 0.5;
  float2 r = h2(id + seed);
  if (r.x > density) return 0.0;
  float2 o = (h2(id + seed + 7.3) - 0.5) * 0.5;
  float2 dv = (f - o) * cell;
  float d = dot(dv, dv);
  float tw = 0.35 + 0.65 * (0.5 + 0.5 * sin(t * (1.3 + r.y * 3.5) + r.x * 60.0));
  float s = sz * (0.6 + 0.9 * r.y);
  return tw * (exp(-d / (s * s)) + 0.22 * exp(-d / (s * s * 12.0)));
}

half4 main(float2 fc) {
  float2 uv = fc / res;
  float asp = res.x / res.y;
  float rr = length((uv - float2(0.5, 0.45)) * float2(asp, 1.0));
  float3 col = mix(cBaseIn, cBaseOut, smoothstep(0.05, 0.85, rr));

  // nebula fog
  float w = 6.2831853 * t / 18.0;
  float4 a1 = aux.eval(fc * k * 0.5 + float2(sin(w) * 70.0, -t * 8.0));
  float4 a2 = aux.eval(fc * k * 0.85 + float2(-t * 5.0 + a1.g * 220.0, cos(w) * 90.0 + a1.b * 160.0));
  float fog = smoothstep(0.38, 0.95, a1.g * 0.55 + a2.b * 0.6);
  float fog2 = smoothstep(0.45, 1.0, a2.g * 0.7 + a1.b * 0.4);
  col += cFog1 * fog * (0.6 + 0.25 * evo) + cFog2 * fog2 * (0.5 + 0.2 * evo);

  // crystalline web: near + far (parallax)
  float2 np = fc * k + float2(t * 3.0, -t * 7.0);
  float4 wn = web.eval(np);
  float rid = aux.eval(np).r;
  float4 wf = web.eval(fc * k * 1.7 + float2(400.0 - t * 1.5, 900.0 - t * 3.5));
  float3 blue = cWeb;
  float3 cyan = cBurst;
  col += blue * (wn.r * 0.22 + wn.g * 0.11 + hq * (wf.r * 0.08 + wf.g * 0.05)) * (0.75 + 0.35 * fog);

  // travelling light dashes
  float sel = step(0.6, rid);
  float pos = fract(t * (0.16 + 0.34 * h1(rid * 91.0)) + rid * 13.0);
  float da = abs(wn.b - pos);
  da = min(da, 1.0 - da);
  float dash = exp(-da * da * 900.0) * sel;

  // electric bursts: 3 channels → a burst every ~0.5–1.5 s, snapping outward along the lines, ~0.3 s decay
  float burst = 0.0;
  for (int c = 0; c < 3; c++) {
    float cf = float(c);
    float per = 0.55 + 0.45 * cf;
    float tt = t + cf * 0.37;
    float kk = floor(tt / per);
    float age = tt - kk * per;
    float2 ctr = h2(float2(kk, cf * 17.0 + 3.0)) * res;
    float dist = length(fc - ctr) / res.y;
    float front = age * 1.25;
    float x = (dist - front) / 0.035;
    float ring = exp(-x * x);
    float inside = smoothstep(front + 0.02, front - 0.06, dist) * 0.3;
    float branch = step(0.3, fract(rid * 7.0 + kk * 0.618));
    burst += (ring + inside) * exp(-age / 0.3) * branch * smoothstep(0.33, 0.0, dist);
  }
  float e = wn.r * (dash * 1.5 + burst * 2.4) + wn.g * (dash * 0.55 + burst * 1.0) + hq * wf.r * burst * 0.6;
  col += mix(cyan, float3(1.0), clamp(wn.r * (dash + burst), 0.0, 1.0)) * e;

  // motes: mid + far depth layers
  float m = motes(fc, 46.0 * dens, res.y / 11.0, 1.15 * dens, 3.1, 0.42)
          + hq * 0.55 * motes(fc + 333.0, 30.0 * dens, res.y / 21.0, 0.6 * dens, 9.7, 0.33);
  col += cMote * m;

  // soft diagonal light streaks
  float diag = (fc.x * 0.55 + fc.y) / res.y;
  col += cStreak * pow(0.5 + 0.5 * sin(diag * 9.0 - t * 0.35), 24.0) * 0.05 * hq;

  // scanlines + vignette
  col *= 1.0 - 0.05 * step(1.5, mod(fc.y, 3.0));
  col *= 1.0 - 0.35 * smoothstep(0.5, 1.15, rr);
  return half4(half3(col), 1.0);
}
"""

private class Mote(val x: Float, val y: Float, val speed: Float, val r: Float, val phase: Float, val sway: Float)

@RequiresApi(33)
private class RealmShader(web: android.graphics.Bitmap, aux: android.graphics.Bitmap) {
    val shader = RuntimeShader(DIGITAL_REALM).apply {
        setInputShader("web", BitmapShader(web, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
        setInputShader("aux", BitmapShader(aux, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
    }
    val brush = ShaderBrush(shader)
}

/** Full-screen animated background behind every screen. [fx] = user intensity (High / Low / Off). */
@Composable
fun SystemBackground(modifier: Modifier = Modifier, fx: BgFx = BgFx.HIGH, content: @Composable BoxScope.() -> Unit) {
    val reduced = LocalReducedMotion.current
    val clock = LocalFxClock.current
    val ctx = LocalContext.current
    val animate = !reduced && fx != BgFx.OFF
    val hq = fx == BgFx.HIGH
    // textures decoded off the main thread; until ready the gradient + motes fallback is drawn
    var realm by remember { mutableStateOf<Any?>(null) }
    if (Build.VERSION.SDK_INT >= 33) LaunchedEffect(Unit) {
        realm = withContext(Dispatchers.IO) {
            try {
                val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                val w = BitmapFactory.decodeResource(ctx.resources, R.drawable.tex_web, o)
                val a = BitmapFactory.decodeResource(ctx.resources, R.drawable.tex_aux, o)
                if (w != null && a != null) RealmShader(w, a) else null
            } catch (e: Throwable) { android.util.Log.e("SoloFx", "background shader unavailable", e); null }
        }
    }
    val motes = remember(hq) { val r = Random(5); List(if (hq) 16 else 8) { Mote(r.nextFloat(), r.nextFloat(), 0.10f + r.nextFloat() * 0.06f, 1.6f + r.nextFloat() * 2.4f, r.nextFloat() * 6.28f, 0.01f + r.nextFloat() * 0.03f) } }
    val fallbackWeb = remember { val r = Random(3); List(26) { Offset(r.nextFloat(), r.nextFloat()) to Offset(r.nextFloat(), r.nextFloat()) } }
    Box(modifier.fillMaxSize()) {
        Spacer(Modifier.fillMaxSize().graphicsLayer().drawBehind {
            val t = if (animate) clock.seconds.floatValue else 7.5f
            val rs = realm
            if (Build.VERSION.SDK_INT >= 33 && rs is RealmShader) {
                rs.shader.setFloatUniform("res", size.width, size.height)
                rs.shader.setFloatUniform("t", t)
                rs.shader.setFloatUniform("k", 2.4f / density)
                rs.shader.setFloatUniform("hq", if (hq) 1f else 0f)
                rs.shader.setFloatUniform("dens", density)
                // themed colours (HOLO ↔ MONARCH), read in the draw phase only
                listOf("cBaseIn" to SystemTheme.shader { it.baseIn }, "cBaseOut" to SystemTheme.shader { it.baseOut }, "cFog1" to SystemTheme.shader { it.fog1 },
                    "cFog2" to SystemTheme.shader { it.fog2 }, "cWeb" to SystemTheme.shader { it.web }, "cBurst" to SystemTheme.shader { it.burst },
                    "cMote" to SystemTheme.shader { it.mote }, "cStreak" to SystemTheme.shader { it.streak }).forEach { (n, v) -> rs.shader.setFloatUniform(n, v[0], v[1], v[2]) }
                rs.shader.setFloatUniform("evo", SystemTheme.blend.coerceIn(0f, 1f))
                drawRect(rs.brush)
            } else drawFallback(fallbackWeb, t)
            // near bokeh motes (closest depth layer: biggest, fastest)
            val d = density
            motes.forEach { m ->
                val yy = (((m.y - t * m.speed) % 1f) + 1f) % 1f
                val xx = m.x + sin(t * 0.4f + m.phase) * m.sway
                val tw = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * 1.7f + m.phase * 3f))
                val c = Offset(xx * size.width, yy * size.height)
                drawCircle(Mote1.copy(alpha = 0.10f * tw), m.r * d * 4.5f, c)
                drawCircle(Mote1.copy(alpha = 0.28f * tw), m.r * d * 1.8f, c)
                drawCircle(Color.White.copy(alpha = 0.85f * tw), m.r * d * 0.55f, c)
            }
        })
        content()
    }
}

private val Mote1 get() = SystemTheme.c(0xFF67E8F9, 0xFFD8A8FF)

private fun DrawScope.drawFallback(web: List<Pair<Offset, Offset>>, t: Float) {
    drawRect(Brush.radialGradient(listOf(SystemTheme.c(0xFF0F172A, 0xFF180A26), SystemTheme.c(0xFF020617, 0xFF06020B)), Offset(size.width / 2, size.height * 0.45f), size.maxDimension * 0.75f))
    val f1 = Offset(size.width * (0.3f + 0.1f * sin(t * 0.35f)), size.height * (0.35f + 0.05f * sin(t * 0.2f)))
    drawCircle(Brush.radialGradient(listOf(SystemTheme.c(0xFF1E3A8A, 0xFF54178F).copy(alpha = 0.35f), Color.Transparent), f1, size.width * 0.8f), size.width * 0.8f, f1)
    val f2 = Offset(size.width * (0.7f - 0.1f * sin(t * 0.27f)), size.height * (0.7f + 0.04f * sin(t * 0.31f)))
    drawCircle(Brush.radialGradient(listOf(SystemTheme.c(0xFF312E81, 0xFF6B126B).copy(alpha = 0.3f), Color.Transparent), f2, size.width * 0.7f), size.width * 0.7f, f2)
    web.forEachIndexed { i, (a, b) ->
        val p1 = Offset(a.x * size.width, a.y * size.height); val p2 = Offset(b.x * size.width, b.y * size.height)
        val pulse = ((t * 0.9f + i * 0.37f) % 3f).let { if (it < 0.3f) 1f - it / 0.3f else 0f }
        drawLine(SystemTheme.c(0xFF3B82F6, 0xFF9945FA).copy(alpha = 0.15f), p1, p2, 1.2f)
        if (pulse > 0f) drawLine(SystemTheme.c(0xFF22D3EE, 0xFFED4DF5).copy(alpha = 0.8f * pulse), p1, p2, 2.5f)
    }
}
