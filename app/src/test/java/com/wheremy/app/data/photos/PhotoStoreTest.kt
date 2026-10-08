package com.wheremy.app.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 照片三档与 EXIF 方向（`ARCHITECTURE.md` §5.1）。
 * 这些断言在真机上出错时最难查：图会"躺倒"、缩略图会 OOM、失败会留下无主文件。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: PhotoStore

    @Before
    fun setUp() {
        store = PhotoStore(File(tmp.root, "photos"))
    }

    /** 造一张真 JPEG；左半边涂红，方便以后做方向断言时能看出朝向。 */
    private fun jpegOf(width: Int, height: Int, orientation: Int? = null): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        val bytes = out.toByteArray()
        if (orientation == null) return bytes

        val carrier = File(tmp.root, "carrier-$orientation.jpg").apply { writeBytes(bytes) }
        ExifInterface(carrier.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return carrier.readBytes()
    }

    private fun longEdgeOf(file: File): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return maxOf(bounds.outWidth, bounds.outHeight)
    }

    @Test
    fun `两档各自按长边落盘且目录名固定`() {
        val stored = store.write(jpegOf(2000, 1000))

        val original = store.file(stored.originalPath)
        val thumb = store.file(stored.thumbPath)

        assertTrue("原图必须落在 originals/ 下", stored.originalPath.startsWith("${PhotoStore.DIR_ORIGINALS}/"))
        assertTrue("缩略图必须落在 thumbs/ 下", stored.thumbPath.startsWith("${PhotoStore.DIR_THUMBS}/"))
        assertEquals(PhotoStore.ORIGINAL_EDGE, longEdgeOf(original))
        assertEquals(PhotoStore.THUMB_EDGE, longEdgeOf(thumb))
    }

    @Test
    fun `EXIF 方向被烤进像素且新文件不再带方向标签`() {
        // 取向 6 = 需要顺时针转 90°：800×400 存出来必须是 400×800
        val stored = store.write(jpegOf(800, 400, orientation = ExifInterface.ORIENTATION_ROTATE_90))

        val baked = BitmapFactory.decodeFile(store.file(stored.originalPath).absolutePath)
        assertEquals("宽高必须已经换过来", 400, baked.width)
        assertEquals(800, baked.height)

        val exif = ExifInterface(store.file(stored.originalPath).absolutePath)
        val orientation =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        assertTrue(
            "存出来的文件不该再带「要转 90°」这类方向标签（实测值=$orientation）；" +
                "重编码器可能写 0=UNDEFINED，那也算没有方向",
            orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED,
        )
    }

    @Test
    fun `不转方向的照片尺寸不变`() {
        val stored = store.write(jpegOf(800, 400))

        val baked = BitmapFactory.decodeFile(store.file(stored.originalPath).absolutePath)
        assertEquals(800, baked.width)
        assertEquals(400, baked.height)
    }

    @Test
    fun `分析图只给字节不落盘`() {
        val stored = store.write(jpegOf(2000, 1000))

        assertTrue("分析图必须有字节", stored.analysisJpeg.isNotEmpty())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(stored.analysisJpeg, 0, stored.analysisJpeg.size, bounds)
        assertTrue(
            "分析图长边不超过 ${PhotoStore.ANALYSIS_EDGE}",
            maxOf(bounds.outWidth, bounds.outHeight) <= PhotoStore.ANALYSIS_EDGE,
        )

        assertEquals("originals/ 只该有这一张", 1, File(store.rootDir, PhotoStore.DIR_ORIGINALS).listFiles()!!.size)
        assertEquals("thumbs/ 只该有这一张", 1, File(store.rootDir, PhotoStore.DIR_THUMBS).listFiles()!!.size)
    }

    @Test
    fun `解不开的字节抛固定那句话且不留半张文件`() {
        val error = assertThrows(IllegalStateException::class.java) {
            store.write(byteArrayOf(1, 2, 3, 4, 5))
        }

        assertEquals(PhotoStore.ERROR_DECODE, error.message)
        assertEquals(0, File(store.rootDir, PhotoStore.DIR_ORIGINALS).listFiles()!!.size)
        assertEquals(0, File(store.rootDir, PhotoStore.DIR_THUMBS).listFiles()!!.size)
    }

    @Test
    fun `file 与 delete 按相对路径工作`() {
        val stored = store.write(jpegOf(800, 400))

        assertTrue(store.file(stored.originalPath).exists())
        assertTrue(store.delete(stored.originalPath))
        assertTrue("删过就不该还在", !store.file(stored.originalPath).exists())
    }
}
