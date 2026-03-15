package com.mushroom.feature.account.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.mushroom.core.logging.MushroomLogger
import java.io.ByteArrayOutputStream

private const val TAG = "ImageCompressor"

object ImageCompressor {
    private const val MAX_SIZE = 256
    private const val QUALITY = 80

    fun compressAvatar(context: Context, uri: Uri): ByteArray? {
        return try {
            MushroomLogger.i(TAG, "compressAvatar: uri=$uri")
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                MushroomLogger.w(TAG, "compressAvatar: openInputStream returned null, uri=$uri")
                return null
            }
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) {
                MushroomLogger.w(TAG, "compressAvatar: BitmapFactory.decodeStream returned null, uri=$uri")
                return null
            }

            val scale = minOf(MAX_SIZE.toFloat() / original.width, MAX_SIZE.toFloat() / original.height, 1f)
            val width = (original.width * scale).toInt()
            val height = (original.height * scale).toInt()
            val origW = original.width
            val origH = original.height
            val scaled = Bitmap.createScaledBitmap(original, width, height, true)

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, baos)

            if (scaled != original) scaled.recycle()
            original.recycle()

            val result = baos.toByteArray()
            MushroomLogger.i(TAG, "compressAvatar: ${origW}x${origH} -> ${width}x${height}, outputBytes=${result.size}")
            result
        } catch (e: Exception) {
            MushroomLogger.e(TAG, "compressAvatar failed: uri=$uri", e)
            null
        }
    }
}
