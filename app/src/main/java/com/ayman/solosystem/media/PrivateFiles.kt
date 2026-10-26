package com.ayman.solosystem.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * App-private image storage (filesDir — never MediaStore, never backed up: allowBackup=false) and on-tap sharing via FileProvider.
 * Images are re-encoded (downscaled, EXIF incl. GPS stripped, orientation applied).
 */
object PrivateFiles {
    fun authority(ctx: Context) = ctx.packageName + ".files"
    fun file(ctx: Context, rel: String) = File(ctx.filesDir, rel)
    fun dir(ctx: Context, name: String) = File(ctx.filesDir, name).apply { mkdirs() }

    /** Fresh camera target inside filesDir/transform (the camera app writes through a one-shot FileProvider grant). */
    fun newCameraTarget(ctx: Context, id: String): Pair<File, Uri> {
        val f = File(dir(ctx, "transform"), "$id.raw.jpg")
        return f to FileProvider.getUriForFile(ctx, authority(ctx), f)
    }

    private fun orientation(open: () -> java.io.InputStream?): Int = runCatching {
        open()?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) } ?: 1
    }.getOrDefault(1)

    fun decode(open: () -> java.io.InputStream?, maxPx: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val b = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rot = when (orientation(open)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        val scale = (maxPx.toFloat() / maxOf(b.width, b.height)).coerceAtMost(1f)
        if (rot == 0f && scale == 1f) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot); postScale(scale, scale) }, true)
    }.getOrNull()

    /** Copy + re-encode an image (picker Uri or raw camera file) into filesDir/[folder]/[id].jpg; returns the relative path. */
    fun importImage(ctx: Context, open: () -> java.io.InputStream?, folder: String, id: String, maxPx: Int = 1600): String? {
        val b = decode(open, maxPx) ?: return null
        val rel = "$folder/$id.jpg"
        val out = file(ctx, rel); out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, "$id.tmp")
        tmp.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (!tmp.renameTo(out)) { tmp.copyTo(out, true); tmp.delete() }
        return rel
    }
    fun importUri(ctx: Context, uri: Uri, folder: String, id: String, maxPx: Int = 1600) =
        importImage(ctx, { ctx.contentResolver.openInputStream(uri) }, folder, id, maxPx)

    fun load(ctx: Context, rel: String?, maxPx: Int = 1200): Bitmap? {
        val f = rel?.let { file(ctx, it) }?.takeIf { it.exists() } ?: return null
        return decode({ f.inputStream() }, maxPx)
    }

    /** Share-only copy in cache/share (cleared of older exports first). */
    fun shareTarget(ctx: Context, name: String): File {
        val d = File(ctx.cacheDir, "share").apply { mkdirs() }
        d.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000 }?.forEach { it.delete() }
        return File(d, name)
    }

    fun share(ctx: Context, f: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(ctx, authority(ctx), f)
        val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
