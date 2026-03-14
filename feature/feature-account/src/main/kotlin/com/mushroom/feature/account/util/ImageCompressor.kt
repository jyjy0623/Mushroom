package com.mushroom.feature.account.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageCompressor {
    private const val MAX_SIZE = 256
    private const val QUALITY = 80

    fun compressAvatar(context: Context, uri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return null

            val scale = minOf(MAX_SIZE.toFloat() / original.width, MAX_SIZE.toFloat() / original.height, 1f)
            val width = (original.width * scale).toInt()
            val height = (original.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(original, width, height, true)

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, baos)

            if (scaled != original) scaled.recycle()
            original.recycle()

            baos.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
