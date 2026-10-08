package com.wheremy.app.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/** 造一张真 JPEG 给照片链路用（测试里不写磁盘文件，直接给字节）。 */
internal fun testJpeg(width: Int = 800, height: Int = 400): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(Color.WHITE)
        drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    bitmap.recycle()
    return out.toByteArray()
}
