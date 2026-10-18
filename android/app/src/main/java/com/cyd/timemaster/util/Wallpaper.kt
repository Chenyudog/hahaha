package com.cyd.timemaster.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 设置系统壁纸；华为系统若弹出主题服务确认框属正常流程 */
object WallpaperSetter {

    /** @return null=成功，否则为错误信息 */
    suspend fun setFromUri(context: Context, uri: Uri, alsoLock: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val wm = WallpaperManager.getInstance(context)
            // 先量尺寸再按屏幕分辨率采样解码，避免超大图片导致内存溢出
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return@withContext "无法读取所选图片"
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= wm.desiredMinimumWidth &&
                bounds.outHeight / (sample * 2) >= wm.desiredMinimumHeight
            ) sample *= 2
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext "无法解码所选图片"

            if (alsoLock && android.os.Build.VERSION.SDK_INT >= 24) {
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            } else {
                wm.setBitmap(bitmap)
            }
            null
        } catch (e: Exception) {
            e.message ?: "设置壁纸失败"
        }
    }
}
