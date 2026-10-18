package com.cyd.timemaster.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 图片解码工具：按需采样，避免大图占满内存 */

fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var inSampleSize = 1
    while (bounds.outWidth / (inSampleSize * 2) >= reqWidth &&
        bounds.outHeight / (inSampleSize * 2) >= reqHeight
    ) {
        inSampleSize *= 2
    }
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { this.inSampleSize = inSampleSize },
    )
}

@Composable
fun rememberThumbnail(path: String, size: Int = 256): ImageBitmap? {
    val state = remember(path) { produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmap(path, size, size)?.asImageBitmap()
        }
    } }
    return state.value
}

fun deleteQuietly(file: File?) {
    file?.let { if (it.exists()) it.delete() }
}
