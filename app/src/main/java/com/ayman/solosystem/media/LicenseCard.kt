package com.ayman.solosystem.media

import android.content.Context
import android.graphics.*
import androidx.core.content.res.ResourcesCompat
import com.ayman.solosystem.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Hunter License card drawn with the platform Canvas (same pixels for the in-app preview and the shared PNG). Original art. */
object LicenseCard {
    const val W = 1600
    const val H = 1000

    data class Data(val name: String, val rank: Int, val score: Int, val push: Int, val plankSec: Int, val squat: Int, val run1kmSec: Int?,
                    val issueDay: Long, val licenseNo: String, val level: Int, val ar: Boolean, val photo: Bitmap?)

    val rankColors = intArrayOf(0xFF9AA7B8.toInt(), 0xFF39D98A.toInt(), 0xFF3FA9FF.toInt(), 0xFFB36BFF.toInt(), 0xFFFF8A3D.toInt(), 0xFFFFD36B.toInt())

    fun render(ctx: Context, d: Data): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val body = runCatching { ResourcesCompat.getFont(ctx, R.font.tajawal_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val bodyR = runCatching { ResourcesCompat.getFont(ctx, R.font.tajawal_medium) }.getOrNull() ?: Typeface.DEFAULT
        val tech = runCatching { ResourcesCompat.getFont(ctx, R.font.rajdhani_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val rc = rankColors[d.rank.coerceIn(0, 5)]
        val cyan = 0xFF4FD8FF.toInt(); val white = 0xFFF4F8FF.toInt(); val dim = 0xFF8FA6C8.toInt()
        // background
        val r = RectF(20f, 20f, W - 20f, H - 20f)
        c.drawRoundRect(r, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), intArrayOf(0xFF060B18.toInt(), 0xFF0B1E3A.toInt(), 0xFF050912.toInt()), null, Shader.TileMode.CLAMP) })
        // hex grid texture
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1A4FD8FF; style = Paint.Style.STROKE; strokeWidth = 2f }
        c.save(); c.clipPath(Path().apply { addRoundRect(r, 48f, 48f, Path.Direction.CW) })
        var yy = 0f; var row = 0
        while (yy < H) { var xx = if (row % 2 == 0) 0f else 45f; while (xx < W) { c.drawPath(hex(xx, yy, 40f), grid); xx += 90f }; yy += 78f; row++ }
        // diagonal light sweep
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(W * 0.35f, 0f, W * 0.65f, H.toFloat(), intArrayOf(0, 0x224FD8FF, 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) })
        // rank-coloured glow behind the emblem
        c.drawCircle(W - 300f, 520f, 330f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(W - 300f, 520f, 330f, intArrayOf((rc and 0x00FFFFFF) or 0x66000000, 0), null, Shader.TileMode.CLAMP) })
        c.restore()
        // frame + corner brackets
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan; style = Paint.Style.STROKE; strokeWidth = 4f; setShadowLayer(18f, 0f, 0f, cyan) }
        c.drawRoundRect(r, 48f, 48f, glow)
        val br = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white; style = Paint.Style.STROKE; strokeWidth = 10f; strokeCap = Paint.Cap.ROUND }
        listOf(56f to 56f, W - 56f to 56f, 56f to H - 56f, W - 56f to H - 56f).forEach { (x, y) ->
            val sx = if (x < W / 2) 1 else -1; val sy = if (y < H / 2) 1 else -1
            c.drawLine(x, y, x + sx * 70f, y, br); c.drawLine(x, y, x, y + sy * 70f, br)
        }
        fun text(t: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface, align: Paint.Align = Paint.Align.LEFT, glowC: Int? = null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = tf; textAlign = align; if (glowC != null) setShadowLayer(16f, 0f, 0f, glowC) }
            c.drawText(t, x, y, p)
        }
        // header
        text("HUNTER LICENSE", 100f, 150f, 78f, white, tech, glowC = cyan)
        text("رخصة صياد • نقابة الصيادين", W - 100f, 150f, 50f, cyan, body, Paint.Align.RIGHT)
        c.drawRect(100f, 180f, W - 100f, 184f, Paint().apply { shader = LinearGradient(100f, 0f, W - 100f, 0f, intArrayOf(cyan, 0x004FD8FF), null, Shader.TileMode.CLAMP) })
        // photo
        val ph = RectF(100f, 230f, 460f, 690f)
        c.drawRoundRect(ph, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A1426.toInt() })
        if (d.photo != null) {
            c.save(); c.clipPath(Path().apply { addRoundRect(ph, 24f, 24f, Path.Direction.CW) })
            val p = d.photo; val s = maxOf(ph.width() / p.width, ph.height() / p.height)
            val dw = p.width * s; val dh = p.height * s
            c.drawBitmap(p, null, RectF(ph.centerX() - dw / 2, ph.centerY() - dh / 2, ph.centerX() + dw / 2, ph.centerY() + dh / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        } else {
            val sil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C3358.toInt() }
            c.drawCircle(ph.centerX(), 390f, 85f, sil)
            c.drawRoundRect(RectF(ph.centerX() - 140f, 500f, ph.centerX() + 140f, 720f), 120f, 120f, sil)
        }
        c.drawRoundRect(ph, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan; style = Paint.Style.STROKE; strokeWidth = 4f })
        // identity
        val lx = 520f
        text(if (d.ar) "الاسم" else "NAME", lx, 270f, 34f, dim, if (d.ar) bodyR else tech)
        text(d.name.ifBlank { "Hunter" }.take(22), lx, 340f, 66f, white, body)
        text(if (d.ar) "المستوى" else "LEVEL", lx, 410f, 34f, dim, if (d.ar) bodyR else tech)
        text("Lv. ${d.level}", lx, 470f, 58f, white, tech)
        text(if (d.ar) "تاريخ الإصدار" else "ISSUED", lx, 540f, 34f, dim, if (d.ar) bodyR else tech)
        text(LocalDate.ofEpochDay(d.issueDay).format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)), lx, 600f, 52f, white, tech)
        text(d.licenseNo, lx, 670f, 44f, cyan, tech)
        // rank emblem
        val cx = W - 300f; val cy = 430f
        c.drawPath(hex(cx, cy, 190f, true), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC050A14.toInt() })
        c.drawPath(hex(cx, cy, 190f, true), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rc; style = Paint.Style.STROKE; strokeWidth = 9f; setShadowLayer(28f, 0f, 0f, rc) })
        c.drawPath(hex(cx, cy, 160f, true), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (rc and 0x00FFFFFF) or 0x55000000; style = Paint.Style.STROKE; strokeWidth = 3f })
        text(com.ayman.solosystem.domain.guild.Guild.rankLabel(d.rank), cx, cy + 100f, 280f, white, tech, Paint.Align.CENTER, rc)
        text(if (d.ar) "الرتبة" else "RANK", cx, cy + 250f, 40f, rc, if (d.ar) body else tech, Paint.Align.CENTER)
        // stats strip
        val stats = listOf(
            (if (d.ar) "ضغط" else "PUSH-UPS") to "${d.push}", (if (d.ar) "بلانك" else "PLANK") to "${d.plankSec}s",
            (if (d.ar) "سكوات" else "SQUATS") to "${d.squat}", (if (d.ar) "1 كم" else "1 KM") to (d.run1kmSec?.let { "%d:%02d".format(Locale.US, it / 60, it % 60) } ?: "—"),
            (if (d.ar) "النقاط" else "SCORE") to "${d.score}")
        val sw = (W - 200f - 360f) / stats.size
        stats.forEachIndexed { i, (k, v) ->
            val x = 100f + sw * i + sw / 2
            c.drawRoundRect(RectF(100f + sw * i + 8f, 740f, 100f + sw * (i + 1) - 8f, 900f), 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x664FD8FF; style = Paint.Style.STROKE; strokeWidth = 3f })
            text(v, x, 830f, 66f, white, tech, Paint.Align.CENTER)
            text(k, x, 880f, 32f, dim, if (d.ar) bodyR else tech, Paint.Align.CENTER)
        }
        // QR-like ornament (decorative pattern, not a real code)
        drawOrnament(c, W - 330f, 740f, 160f, d.licenseNo.hashCode(), cyan)
        text("SOLO SYSTEM", W - 120f, 935f, 30f, dim, tech, Paint.Align.RIGHT)
        return bmp
    }

    private fun hex(cx: Float, cy: Float, r: Float, pointy: Boolean = false) = Path().apply {
        for (i in 0..5) {
            val a = Math.toRadians((60.0 * i) + if (pointy) 30.0 else 0.0)
            val x = cx + r * Math.cos(a).toFloat(); val y = cy + r * Math.sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun drawOrnament(c: Canvas, x: Float, y: Float, size: Float, seed: Int, color: Int) {
        val n = 15; val cell = size / n
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val rnd = java.util.Random(seed.toLong())
        for (i in 0 until n) for (j in 0 until n) {
            val finder = (i < 4 && j < 4) || (i < 4 && j >= n - 4) || (i >= n - 4 && j < 4)
            if (!finder && rnd.nextInt(100) < 46) c.drawRect(x + j * cell, y + i * cell, x + (j + 1) * cell - 1, y + (i + 1) * cell - 1, p)
        }
        listOf(0 to 0, 0 to n - 4, n - 4 to 0).forEach { (i, j) ->
            val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = cell * 0.8f }
            c.drawRect(x + j * cell + cell * 0.4f, y + i * cell + cell * 0.4f, x + (j + 4) * cell - cell * 0.4f - 1, y + (i + 4) * cell - cell * 0.4f - 1, st)
            c.drawRect(x + (j + 1.4f) * cell, y + (i + 1.4f) * cell, x + (j + 2.6f) * cell, y + (i + 2.6f) * cell, p)
        }
    }
}
