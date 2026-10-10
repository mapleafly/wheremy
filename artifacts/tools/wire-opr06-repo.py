# FR-OPR-06 第一步：仓库三个批量方法 + 批量删除的文案 + BatchDelete 状态。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
REPO = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\data\repo\ItemRepository.kt"
FMT = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\UiFormat.kt"

# —— 1) 仓库：三个批量方法，挂在 moveAllFrom 之后 ——
t = io.open(REPO, encoding="utf-8").read()
anchor = "    /** 确认入库：名称必须非空；顺手算 `lastMovedAt` 与 `searchText`，并整批替换标签。 */"
batch = '''    // —— 批量操作（`FR-OPR-06`）——
    //
    // 三件事都**逐件复用单件那条路**，不另写一套 SQL：
    // 单件的口径（`lastMovedAt` 与 `updatedAt` 同一个时间戳、"手工搬动即视为确认"、删文件按引用计数）
    // 已经在测试里钉死了，批量再写一遍就等于把那些规则抄两遍——迟早抄歪。

    /**
     * 批量搬到同一个位置。返回真的搬了几件（**本来就在那儿的不算**，免得说"搬了 5 件"其实没动）。
     */
    suspend fun moveAll(itemIds: Collection<String>, locationId: String?): Int {
        val targets = itemIds.filter { id -> itemDao.byId(id)?.locationId != locationId }
        if (targets.isEmpty()) return 0
        targets.forEach { move(it, locationId) }
        return targets.size
    }

    /** 批量改类别。类别不进 `searchText`（那是名称/品牌/颜色/材质/别名/描述/备注），所以不用重算。 */
    suspend fun setCategoryAll(itemIds: Collection<String>, category: String?): Int {
        var changed = 0
        db.withWriteTransaction {
            itemIds.forEach { id ->
                val item = itemDao.byId(id) ?: return@forEach
                if (item.category == category) return@forEach
                itemDao.upsert(item.copy(category = category, updatedAt = now()))
                changed++
            }
        }
        return changed
    }

    /** 批量删除。逐件走 [delete]（它自己开事务、自己按引用计数删文件），顺序与单件一致。 */
    suspend fun deleteAll(itemIds: Collection<String>): Int {
        var deleted = 0
        itemIds.forEach { id ->
            if (itemDao.byId(id) == null) return@forEach
            delete(id)
            deleted++
        }
        return deleted
    }

'''
if "suspend fun moveAll(" in t:
    print("仓库已有 moveAll，跳过")
else:
    assert anchor in t, "仓库里没找到 confirm 的注释锚点"
    t = t.replace(anchor, batch + anchor, 1)
    io.open(REPO, "w", encoding="utf-8", newline="").write(t)
    print("仓库已加三个批量方法")

# —— 2) UiFormat：批量删除的文案 + BatchDelete ——
f = io.open(FMT, encoding="utf-8").read()
anchor2 = "    /**\n     * AI 失败的 11 条文案"
warn = '''    /**
     * 批量删除的文案（`FR-OPR-06`）。
     *
     * 单件那句（[deleteWarningOf]）说的是"这一件"；批量必须把**两类照片分开数**：
     * 会跟着没的、还有别的记录在用所以会留着的——含糊说"照片可能被删"等于没说。
     */
    fun deleteWarningOfBatch(count: Int, photosToDelete: Int, photosKept: Int): String = when {
        photosToDelete == 0 && photosKept == 0 -> "这 $count 条记录会被删除，无法恢复。"
        photosToDelete > 0 && photosKept > 0 ->
            "删除这 $count 件：$photosToDelete 张照片会跟着没；另有 $photosKept 张还有别的记录在用，照片留着。"
        photosKept > 0 -> "删除这 $count 件：记录会没，照片留着（还有别的记录在用）。"
        else -> "删除这 $count 件：照片和记录会一起删除，无法恢复。"
    }

'''
if "deleteWarningOfBatch" in f:
    print("UiFormat 已有 deleteWarningOfBatch，跳过")
else:
    assert anchor2 in f, "UiFormat 里没找到 AI 文案的注释锚点"
    f = f.replace(anchor2, warn + anchor2, 1)

# BatchDelete：挂在 PendingDelete 之后
anchor3 = "data class PendingDelete(val itemId: String, val warning: String)"
batch_state = anchor3 + '''

/** 批量删除的待确认状态（`FR-OPR-06`）：要删哪些 + 那句已经算好的话。 */
data class BatchDelete(val itemIds: List<String>, val warning: String)'''
if "data class BatchDelete" in f:
    print("已有 BatchDelete，跳过")
else:
    assert anchor3 in f, "没找到 PendingDelete"
    f = f.replace(anchor3, batch_state, 1)

io.open(FMT, "w", encoding="utf-8", newline="").write(f)
print("UiFormat 已加批量文案与 BatchDelete")
