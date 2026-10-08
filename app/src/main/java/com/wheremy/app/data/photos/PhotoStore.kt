package com.wheremy.app.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** 一次写入的产出（`ARCHITECTURE.md` §5.1）。分析用的那份只在进程内，不落盘。 */
data class StoredPhoto(val originalPath: String, val thumbPath: String, val analysisJpeg: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredPhoto) return false
        return originalPath == other.originalPath &&
            thumbPath == other.thumbPath &&
            analysisJpeg.contentEquals(other.analysisJpeg)
    }

    override fun hashCode(): Int {
        var result = originalPath.hashCode()
        result = 31 * result + thumbPath.hashCode()
        result = 31 * result + analysisJpeg.contentHashCode()
        return result
    }
}

/**
 * 照片落盘（`ARCHITECTURE.md` §5.1 / `RULES.md` §9.4）。
 *
 * 三档：原图 [ORIGINAL_EDGE]/q[QUALITY_ORIGINAL] → `originals/<uuid>.jpg`，
 * 缩略图 [THUMB_EDGE]/q[QUALITY_THUMB] → `thumbs/<uuid>.jpg`，
 * 喂模型的那份 [ANALYSIS_EDGE]/q[QUALITY_ANALYSIS] **不落盘**。
 *
 * 三条硬要求（少一条都会在真机上以最难查的方式出问题）：
 * 1. **先读尺寸定 `inSampleSize`**，别把手机原图整张塞进内存；
 * 2. **EXIF 方向在这一步烤进像素**——相机只把"要转 90°"写进标签，而重编码不再带标签；
 * 3. **失败自清理**：此刻库里还没有任何一行认得这两个路径，谁写出来谁扫干净。
 */
class PhotoStore(val rootDir: File) {

    private val originals = File(rootDir, DIR_ORIGINALS).apply { mkdirs() }
    private val thumbs = File(rootDir, DIR_THUMBS).apply { mkdirs() }

    fun file(relPath: String): File = File(rootDir, relPath)

    fun delete(relPath: String): Boolean = File(rootDir, relPath).delete()

    fun write(jpeg: ByteArray): StoredPhoto {
        val upright = decodeUpright(jpeg)
        val id = UUID.randomUUID().toString()
        val originalFile = File(originals, "$id.jpg")
        val thumbFile = File(thumbs, "$id.jpg")
        try {
            compress(upright, ORIGINAL_EDGE, QUALITY_ORIGINAL, originalFile)
            compress(upright, THUMB_EDGE, QUALITY_THUMB, thumbFile)
            return StoredPhoto(
                originalPath = "$DIR_ORIGINALS/$id.jpg",
                thumbPath = "$DIR_THUMBS/$id.jpg",
                analysisJpeg = encode(upright, ANALYSIS_EDGE, QUALITY_ANALYSIS),
            )
        } catch (t: Throwable) {
            originalFile.delete()
            thumbFile.delete()
            throw t
        } finally {
            upright.recycle()
        }
    }

    /** 解码到"已经是正的"位图：bounds → inSampleSize → 烤 EXIF 方向。 */
    private fun decodeUpright(jpeg: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { ERROR_DECODE }

        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longEdge / (sample * 2) >= ORIGINAL_EDGE) sample *= 2

        val decoded = BitmapFactory.decodeByteArray(
            jpeg,
            0,
            jpeg.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error(ERROR_DECODE)

        val upright = applyOrientation(decoded, orientationOf(jpeg))
        if (upright !== decoded) decoded.recycle()
        return upright
    }

    private fun orientationOf(jpeg: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(jpeg))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    /** 方向全表（含两种镜像）都要处理：漏一种，那部分照片在三个地方一起躺倒。 */
    private fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun compress(src: Bitmap, longEdge: Int, quality: Int, into: File) {
        val scaled = scaleTo(src, longEdge)
        into.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (scaled !== src) scaled.recycle()
    }

    private fun encode(src: Bitmap, longEdge: Int, quality: Int): ByteArray {
        val scaled = scaleTo(src, longEdge)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== src) scaled.recycle()
        return out.toByteArray()
    }

    private fun scaleTo(src: Bitmap, longEdge: Int): Bitmap {
        val edge = maxOf(src.width, src.height)
        if (edge <= longEdge) return src
        val ratio = longEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    companion object {
        /** 备份导入要靠这两个名字认出"哪些目录下的文件才是照片"。 */
        const val DIR_ORIGINALS = "originals"
        const val DIR_THUMBS = "thumbs"

        /** 三档的长边与质量。公开出来是为了让测试断言不写魔法数字（`ARCHITECTURE.md` §5.1 的表）。 */
        const val ORIGINAL_EDGE = 1600
        const val THUMB_EDGE = 256
        const val ANALYSIS_EDGE = 1024
        const val QUALITY_ORIGINAL = 85
        const val QUALITY_THUMB = 80
        const val QUALITY_ANALYSIS = 80

        /** 解码失败时抛出的那句话（逐字，`RULES.md` §16 的文案纪律）。 */
        const val ERROR_DECODE = "照片解码失败"
    }
}
