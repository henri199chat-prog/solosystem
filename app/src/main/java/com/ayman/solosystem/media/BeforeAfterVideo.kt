package com.ayman.solosystem.media

import android.content.Context
import android.graphics.*
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.core.content.res.ResourcesCompat
import com.ayman.solosystem.R
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * On-device before/after video (no Media3, no network): H.264 via MediaCodec with an input Surface drawn by a hardware Canvas,
 * plus an AAC track of synthesized System tones, muxed with MediaMuxer. 720×1280, 30 fps, 9 s.
 */
object BeforeAfterVideo {
    const val W = 720; const val H = 1280; const val FPS = 30; const val SECONDS = 9
    private const val SR = 44_100

    data class Info(val before: Bitmap, val after: Bitmap, val beforeLabel: String, val afterLabel: String, val title: String, val delta: String, val ar: Boolean)

    fun render(ctx: Context, info: Info, out: File, progress: (Float) -> Unit = {}): File {
        val tech = runCatching { ResourcesCompat.getFont(ctx, R.font.rajdhani_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val body = runCatching { ResourcesCompat.getFont(ctx, R.font.tajawal_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        out.parentFile?.mkdirs(); out.delete()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // ---- audio first (into memory), so both tracks exist before the muxer starts
        val audio = runCatching { encodeAudio() }.getOrNull()
        val aTrack = audio?.let { muxer.addTrack(it.first) } ?: -1
        // ---- video
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000); setInteger(MediaFormat.KEY_FRAME_RATE, FPS); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); setInteger("max-bframes", 0)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = enc.createInputSurface()
        enc.start()
        var vTrack = -1; var started = false
        val bi = MediaCodec.BufferInfo()
        var firstPts = -1L
        var outFrames = 0L
        val pendingAudio = audio?.second
        fun drain(eos: Boolean) {
            while (true) {
                val idx = enc.dequeueOutputBuffer(bi, if (eos) 10_000 else 0)
                if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!eos) return else continue }
                if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    vTrack = muxer.addTrack(enc.outputFormat); muxer.start(); started = true
                    pendingAudio?.forEach { (buf, inf) -> muxer.writeSampleData(aTrack, buf, inf) }
                    continue
                }
                if (idx < 0) continue
                val buf = enc.getOutputBuffer(idx)!!
                if (bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bi.size = 0
                if (bi.size > 0 && started) {
                    // Surface timestamps follow the wall clock, which stretches the clip on slow devices
                    // (seen on the emulator: 9 s -> 258 s). Re-stamp by output order instead; B-frames are off.
                    if (firstPts < 0) firstPts = bi.presentationTimeUs
                    bi.presentationTimeUs = outFrames * 1_000_000L / FPS; outFrames++
                    buf.position(bi.offset); buf.limit(bi.offset + bi.size)
                    muxer.writeSampleData(vTrack, buf, bi)
                }
                enc.releaseOutputBuffer(idx, false)
                if (bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            }
        }
        val frames = FPS * SECONDS
        val frameNs = 1_000_000_000L / FPS
        val t0 = System.nanoTime()
        try {
            for (f in 0 until frames) {
                val c = surface.lockHardwareCanvas()
                try { drawFrame(c, info, f / FPS.toFloat(), tech, body) } finally { surface.unlockCanvasAndPost(c) }
                drain(false)
                progress((f + 1f) / frames)
                // real-time pacing: surface timestamps come from the clock, so frames are posted at 30 fps
                val wait = t0 + (f + 1) * frameNs - System.nanoTime()
                if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
            }
            enc.signalEndOfInputStream()
            drain(true)
        } finally {
            runCatching { enc.stop() }; enc.release(); surface.release()
            runCatching { if (started) muxer.stop() }; muxer.release()
        }
        return out
    }

    // ------------------------------------------------------------------ frames
    private fun ease(x: Float) = (1 - cos(PI * x.coerceIn(0f, 1f)).toFloat()) / 2
    private fun drawCover(c: Canvas, b: Bitmap, zoom: Float, clip: RectF? = null, alpha: Int = 255) {
        val s = max(W.toFloat() / b.width, H.toFloat() / b.height) * zoom
        val dw = b.width * s; val dh = b.height * s
        c.save(); clip?.let { c.clipRect(it) }
        c.drawBitmap(b, null, RectF((W - dw) / 2, (H - dh) / 2, (W + dw) / 2, (H + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha })
        c.restore()
    }
    private val cyan = 0xFF4FD8FF.toInt(); private val white = 0xFFF4F8FF.toInt(); private val violet = 0xFF8B5CFF.toInt()

    private fun hud(c: Canvas, t: Float, label: String, sub: String, tf: Typeface, bodyTf: Typeface, accent: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; style = Paint.Style.STROKE; strokeWidth = 6f; setShadowLayer(14f, 0f, 0f, accent) }
        val m = 40f; val l = 90f
        listOf(m to m, W - m to m, m to H - m, W - m to H - m).forEach { (x, y) ->
            val sx = if (x < W / 2) 1 else -1; val sy = if (y < H / 2) 1 else -1
            c.drawLine(x, y, x + sx * l, y, p); c.drawLine(x, y, x, y + sy * l, p)
        }
        // scanline
        val sy = (t * 0.6f % 1f) * H
        c.drawRect(0f, sy, W.toFloat(), sy + 3f, Paint().apply { color = (accent and 0x00FFFFFF) or 0x33000000 })
        // bottom plate
        c.drawRect(0f, H - 300f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, H - 300f, 0f, H.toFloat(), 0x00000000, 0xDD000000.toInt(), Shader.TileMode.CLAMP) })
        c.drawText(label, W / 2f, H - 170f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white; textSize = 84f; typeface = tf; textAlign = Paint.Align.CENTER; setShadowLayer(20f, 0f, 0f, accent) })
        c.drawText(sub, W / 2f, H - 100f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; textSize = 40f; typeface = bodyTf; textAlign = Paint.Align.CENTER })
    }

    private fun drawFrame(c: Canvas, i: Info, t: Float, tech: Typeface, body: Typeface) {
        c.drawColor(0xFF03060C.toInt())
        val beforeTxt = if (i.ar) "قبل" else "BEFORE"; val afterTxt = if (i.ar) "بعد" else "AFTER"
        val labelTf = if (i.ar) body else tech
        when {
            t < 1.2f -> { // intro
                val a = ease(t / 0.5f)
                val grid = Paint().apply { color = (cyan and 0x00FFFFFF) or ((0x22 * a).toInt() shl 24); strokeWidth = 2f }
                var x = 0f; while (x < W) { c.drawLine(x, 0f, x, H.toFloat(), grid); x += 60f }
                var y = 0f; while (y < H) { c.drawLine(0f, y, W.toFloat(), y, grid); y += 60f }
                val box = RectF(60f, H / 2f - 180f, W - 60f, H / 2f + 180f)
                val h = box.height() * ease(t / 0.35f)
                val r = RectF(box.left, box.centerY() - h / 2, box.right, box.centerY() + h / 2)
                c.drawRect(r, Paint().apply { color = 0xCC06101E.toInt() })
                c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan; style = Paint.Style.STROKE; strokeWidth = 4f; setShadowLayer(16f, 0f, 0f, cyan) })
                if (t > 0.35f) {
                    val ta = (ease((t - 0.35f) / 0.3f) * 255).toInt()
                    c.drawText("[SYSTEM]", W / 2f, box.centerY() - 50f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan; alpha = ta; textSize = 54f; typeface = tech; textAlign = Paint.Align.CENTER })
                    c.drawText(i.title, W / 2f, box.centerY() + 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white; alpha = ta; textSize = 62f; typeface = body; textAlign = Paint.Align.CENTER; setShadowLayer(18f, 0f, 0f, cyan) })
                    c.drawText(i.delta, W / 2f, box.centerY() + 120f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan; alpha = ta; textSize = 40f; typeface = body; textAlign = Paint.Align.CENTER })
                }
            }
            t < 4.0f -> { val k = (t - 1.2f) / 2.8f; drawCover(c, i.before, 1.0f + 0.08f * k, alpha = (ease((t - 1.2f) / 0.3f) * 255).toInt()); hud(c, t, beforeTxt, i.beforeLabel, labelTf, body, cyan) }
            t < 4.8f -> { // glowing wipe
                val k = ease((t - 4.0f) / 0.8f); val x = W * k
                drawCover(c, i.before, 1.08f)
                drawCover(c, i.after, 1.0f, RectF(0f, 0f, x, H.toFloat()))
                c.drawRect(x - 6f, 0f, x + 6f, H.toFloat(), Paint().apply { color = white; setShadowLayer(40f, 0f, 0f, cyan) })
                c.drawRect(x - 60f, 0f, x + 60f, H.toFloat(), Paint().apply { shader = LinearGradient(x - 60f, 0f, x + 60f, 0f, intArrayOf(0, (cyan and 0x00FFFFFF) or 0x66000000, 0), null, Shader.TileMode.CLAMP) })
            }
            t < 7.6f -> { val k = (t - 4.8f) / 2.8f; drawCover(c, i.after, 1.0f + 0.08f * k); hud(c, t, afterTxt, i.afterLabel, labelTf, body, violet) }
            else -> { // split comparison + outro
                val half = W / 2f
                drawCover(c, i.before, 1.0f, RectF(0f, 0f, half, H.toFloat()))
                drawCover(c, i.after, 1.0f, RectF(half, 0f, W.toFloat(), H.toFloat()))
                c.drawRect(half - 3f, 0f, half + 3f, H.toFloat(), Paint().apply { color = white; setShadowLayer(24f, 0f, 0f, cyan) })
                c.drawRect(0f, H - 330f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, H - 330f, 0f, H.toFloat(), 0x00000000, 0xEE000000.toInt(), Shader.TileMode.CLAMP) })
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 56f; typeface = labelTf; textAlign = Paint.Align.CENTER; color = white }
                c.drawText(beforeTxt, half / 2, 120f, tp); c.drawText(afterTxt, half * 1.5f, 120f, tp)
                c.drawText(i.delta, W / 2f, H - 170f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white; textSize = 52f; typeface = body; textAlign = Paint.Align.CENTER; setShadowLayer(18f, 0f, 0f, cyan) })
                c.drawText("SOLO SYSTEM", W / 2f, H - 90f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan; textSize = 40f; typeface = tech; textAlign = Paint.Align.CENTER; letterSpacing = 0.2f })
                val fade = ((t - 8.5f) / 0.5f).coerceIn(0f, 1f)
                if (fade > 0) c.drawColor(((fade * 255).toInt() shl 24))
            }
        }
    }

    // ------------------------------------------------------------------ audio (synthesized System tones → AAC)
    private fun tone(buf: ShortArray, startS: Float, durS: Float, f0: Double, f1: Double, vol: Double, noise: Boolean = false) {
        val n0 = (startS * SR).toInt(); val n = (durS * SR).toInt()
        var ph = 0.0; val rnd = java.util.Random(7)
        for (i in 0 until n) {
            val k = n0 + i; if (k >= buf.size) break
            val x = i.toDouble() / n
            val env = min(1.0, x * 40) * (1 - x).pow(2.0)
            val f = f0 + (f1 - f0) * x
            ph += 2 * PI * f / SR
            val s = if (noise) (rnd.nextGaussian() * 0.5 * sin(ph * 0.05)) else (sin(ph) + 0.3 * sin(ph * 2.01))
            buf[k] = (buf[k] + (s * env * vol * 32767 * 0.6)).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
    }

    private fun pcm(): ShortArray {
        val b = ShortArray(SR * SECONDS)
        tone(b, 0.05f, 0.35f, 1318.0, 1318.0, 0.5)          // intro ping
        tone(b, 0.40f, 0.6f, 1760.0, 1975.0, 0.35)
        tone(b, 1.15f, 0.25f, 880.0, 880.0, 0.35)           // before blip
        tone(b, 3.90f, 0.95f, 300.0, 2400.0, 0.4, noise = true) // wipe whoosh
        tone(b, 4.75f, 0.5f, 1046.0, 1046.0, 0.45)          // after reveal
        tone(b, 4.85f, 0.9f, 1568.0, 1568.0, 0.35)
        tone(b, 7.55f, 0.8f, 1318.0, 1760.0, 0.4)           // compare chime
        return b
    }

    private fun encodeAudio(): Pair<MediaFormat, List<Pair<ByteBuffer, MediaCodec.BufferInfo>>> {
        val data = pcm()
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SR, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC); setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); enc.start()
        val out = mutableListOf<Pair<ByteBuffer, MediaCodec.BufferInfo>>()
        var outFmt: MediaFormat? = null
        var pos = 0; var inEos = false
        val bi = MediaCodec.BufferInfo()
        try {
            while (true) {
                if (!inEos) {
                    val ii = enc.dequeueInputBuffer(5_000)
                    if (ii >= 0) {
                        val ib = enc.getInputBuffer(ii)!!; ib.clear()
                        val n = min(ib.capacity() / 2, data.size - pos)
                        val pts = pos * 1_000_000L / SR
                        if (n <= 0) { enc.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inEos = true }
                        else {
                            val bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN); bb.asShortBuffer().put(data, pos, n)
                            ib.put(bb); enc.queueInputBuffer(ii, 0, n * 2, pts, 0); pos += n
                        }
                    }
                }
                val oi = enc.dequeueOutputBuffer(bi, 5_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { outFmt = enc.outputFormat; continue }
                if (oi < 0) continue
                if (bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && bi.size > 0) {
                    val ob = enc.getOutputBuffer(oi)!!; ob.position(bi.offset); ob.limit(bi.offset + bi.size)
                    val copy = ByteBuffer.allocate(bi.size); copy.put(ob); copy.flip()
                    out += copy to MediaCodec.BufferInfo().apply { set(0, bi.size, bi.presentationTimeUs, bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()) }
                }
                val eos = bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                enc.releaseOutputBuffer(oi, false)
                if (eos) break
            }
        } finally { runCatching { enc.stop() }; enc.release() }
        return (outFmt ?: error("no aac format")) to out
    }
}
