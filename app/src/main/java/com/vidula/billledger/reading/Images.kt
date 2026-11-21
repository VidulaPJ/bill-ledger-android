package com.vidula.billledger.reading

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_SIDE = 1600

/** Loads a photo (rotated upright on Android 9+) or the first two pages of a PDF as bitmaps no larger than 1600 px. */
fun loadPages(context: Context, uri: Uri): List<Bitmap> {
    val type = context.contentResolver.getType(uri) ?: ""
    return if (type == "application/pdf" || uri.toString().endsWith(".pdf", true)) pdfPages(context, uri) else listOf(photo(context, uri))
}

private fun photo(context: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= 28) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val k = MAX_SIDE.toFloat() / max(w, h)
            if (k < 1f) decoder.setTargetSize((w * k).roundToInt(), (h * k).roundToInt())
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
        ?: error("Couldn't open that image")
}

private fun pdfPages(context: Context, uri: Uri): List<Bitmap> {
    // PdfRenderer needs a seekable file, so copy the PDF into the cache first.
    val tmp = File(context.cacheDir, "bill.pdf")
    context.contentResolver.openInputStream(uri).use { input ->
        tmp.outputStream().use { out -> requireNotNull(input) { "Couldn't open that PDF" }.copyTo(out) }
    }
    val fd = android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    return PdfRenderer(fd).use { pdf ->
        (0 until minOf(pdf.pageCount, 2)).map { i ->
            pdf.openPage(i).use { page ->
                val k = 1400f / page.width
                val bmp = Bitmap.createBitmap((page.width * k).roundToInt(), (page.height * k).roundToInt(), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }.also { tmp.delete() }
}

fun Bitmap.toJpeg(quality: Int = 85): ByteArray =
    ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
