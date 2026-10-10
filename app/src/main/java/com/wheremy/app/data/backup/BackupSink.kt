package com.wheremy.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自动导出的落点（`FR-BAK-08`）。
 *
 * 抽成接口是为了**能在 JVM 上测**：SAF 的 `ContentResolver` 在单测里不好造，
 * 而"该写什么、写几次、失败怎么说"这些逻辑才是要钉的。
 */
interface BackupSink {
    /**
     * 写一份包。
     *
     * @return 人能读的落点描述（文件名之类），会原样写进设置页那一行。
     */
    suspend fun write(bytes: ByteArray, fileName: String): String
}

/**
 * 真实落点：用户选的那棵 **SAF 目录树**（`OpenDocumentTree` 授权过的）。
 *
 * 两件事要注意：
 * 1. 写之前目录本身可能已经被用户删了/取消了授权 → `createDocument` 返回 null，这里抛出来，
 *    上层把这句话记进设置页（**不能静默失败**：自动导出失败而用户不知道，等于没有备份）；
 * 2. 每天一份会越攒越多 → 只留最近 [KEEP] 份，按文件名前缀认自己写的那些，
 *    **绝不碰这个目录里别的文件**。
 */
class TreeDocumentSink(private val resolver: ContentResolver, private val treeUri: Uri) : BackupSink {

    override suspend fun write(bytes: ByteArray, fileName: String): String = withContext(Dispatchers.IO) {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val created = DocumentsContract.createDocument(resolver, parent, MIME_ZIP, fileName)
            ?: error("在这个目录里建不了文件（可能被删了或授权过期）")
        resolver.openOutputStream(created)?.use { it.write(bytes) } ?: error("写不进这个目录")
        prune(parent)
        fileName
    }

    /** 只留最近 [KEEP] 份自动导出：认前缀，别的一律不动。 */
    private fun prune(parent: Uri) {
        runCatching {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val rows = resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            ) ?: return
            val mine = mutableListOf<Pair<String, String>>()
            rows.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    if (name.startsWith(PREFIX) && name.endsWith(".zip")) mine += id to name
                }
            }
            mine.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
                runCatching {
                    DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                }
            }
            parent // 只是为了让 runCatching 的返回值有个类型
        }
    }

    companion object {
        /** 认自己写的那些文件用的前缀；`AutoExporter` 也用同一个。 */
        const val PREFIX = "wheremy-auto-"
        const val MIME_ZIP = "application/zip"

        /** 📐 留最近 7 份：一天的粒度下一周足够回看，也不至于把目录塞满。 */
        const val KEEP = 7
    }
}
