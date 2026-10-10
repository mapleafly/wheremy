package com.wheremy.app.data.backup

import com.wheremy.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId

/**
 * 定时自动导出（`FR-BAK-08`）。
 *
 * 三条纪律：
 * 1. **失败要说出来**：自动导出失败而用户不知道，等于没有备份——所以无论成败都写进设置页那一行；
 * 2. **没配落点就跳过**，并说明"还没选保存位置"，不要静默什么都不做；
 * 3. 用**用户选的那棵目录树**（持久化授权过的），文件名带日期，一天一份。
 *
 * 📌 包在内存里拼完再写（`ByteArrayOutputStream`）。个人量级的库 + 照片是几 MB 级，
 * 这样最省事；将来真要支持几百 MB 的照片墙，得改成边导边写（`BackupRepository.exportTo` 本来就收流）。
 */
class AutoExporter(
    private val backup: BackupRepository,
    private val sinkProvider: suspend () -> BackupSink?,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** 跑一次。返回那句人会读到的话（同时写进设置）。 */
    suspend fun runOnce(): String {
        val at = now()
        val sink = sinkProvider()
            ?: return record(at, "还没选保存位置，这次没导出。点「选择保存位置」挑一个文件夹。")

        val bytes = withContext(Dispatchers.IO) {
            ByteArrayOutputStream().also { backup.exportTo(it, at = at) }.toByteArray()
        }
        val fileName = fileNameFor(at)
        return try {
            sink.write(bytes, fileName)
            record(at, "已导出 $fileName（${bytes.size / 1024} KB）")
        } catch (t: Throwable) {
            record(at, "导出失败：${t.message ?: t::class.simpleName}")
        }
    }

    /** 文件名带本地日期：一天一份，肉眼能对上。 */
    fun fileNameFor(at: Long): String =
        "${TreeDocumentSink.PREFIX}${Instant.ofEpochMilli(at).atZone(zone).toLocalDate()}.zip"

    private suspend fun record(at: Long, message: String): String {
        settings.setAutoExportResult(at, message)
        return message
    }
}
