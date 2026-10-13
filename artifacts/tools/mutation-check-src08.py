# FR-SRC-08 这一批的变异验牙（G2）：把新代码逐处改坏，确认对应的那条会红。
# 规矩：REBUILD.md §1.3；读写都用 newline=""（别把 CRLF 改成 LF，那会变成一次假的代码改动）。
import io
import re
import subprocess
import sys

sys.stdout.reconfigure(errors="replace")
ROOT = r"D:\src\dsh\wheremy"

REPO = r"app\src\main\java\com\wheremy\app\data\repo\ItemRepository.kt"
SCREEN = r"app\src\main\java\com\wheremy\app\ui\detail\DetailScreen.kt"


def read_text(p):
    return io.open(p, encoding="utf-8", newline="").read()


def write_text(p, t):
    io.open(p, "w", encoding="utf-8", newline="").write(t)


CASES = [
    dict(
        label="M5 删物品只回收第一张",
        path=REPO,
        old="""        val paths = photoDao.forOwner(PhotoOwner.ITEM, itemId)
            .flatMap { listOf(it.filePath, it.thumbPath) }
            .filter { it.isNotBlank() }
        db.withWriteTransaction {
            itemDao.deleteById(itemId)
            itemDao.clearTags(itemId)
            photoDao.deleteForOwner(PhotoOwner.ITEM, itemId)
        }""",
        new="""        val paths = photoDao.forOwner(PhotoOwner.ITEM, itemId).firstOrNull()
            ?.let { listOf(it.filePath, it.thumbPath) }
            .orEmpty()
        db.withWriteTransaction {
            itemDao.deleteById(itemId)
            itemDao.clearTags(itemId)
            photoDao.deleteForOwner(PhotoOwner.ITEM, itemId)
        }""",
        pattern="*ItemPhotoMultiTest*",
        expect="删物品会回收该物品全部照片的文件，不是只看第一张",
    ),
    dict(
        label="M6 确认框只数第一张",
        path=REPO,
        old="""        photos.flatMap { listOf(it.filePath, it.thumbPath) }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { path ->""",
        new="""        photos.take(1).flatMap { listOf(it.filePath, it.thumbPath) }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { path ->""",
        pattern="*ItemPhotoMultiTest*",
        expect="删除确认框要按全部照片数别的物品，不能只数第一张",
    ),
    dict(
        label="M7 加照片插到最前面",
        path=REPO,
        old="sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,",
        new="sortOrder = 0,",
        pattern="*ItemPhotoMultiTest*",
        expect="加一张追加在末尾，封面不动",
    ),
    dict(
        label="M8 去掉 9 张上限",
        path=REPO,
        old="if (existing.size >= MAX_PHOTOS_PER_ITEM) return false",
        new="if (false) return false",
        pattern="*ItemPhotoMultiTest*",
        expect="到 9 张上限就不再写，且一个字节都不落盘",
    ),
    dict(
        label="M9 上限只提示不置灰",
        path=SCREEN,
        old="                enabled = canAddPhoto,",
        new="                enabled = true,",
        pattern="*DetailScreenPhotoTest*",
        expect="到 9 张上限时加照片置灰",
    ),
    dict(
        label="M10 最后一张的文案不分情况",
        path=SCREEN,
        old="""                    if (last) {
                        "删掉这张照片？这件物品就没有照片了"
                    } else {
                        "删掉这张照片？"
                    },""",
        new="""                    "删掉这张照片？",""",
        pattern="*DetailScreenPhotoTest*",
        expect="只剩一张时说清删完就没有照片了",
    ),
]

if len(sys.argv) > 1:
    CASES = [c for c in CASES if any(a in c["label"] for a in sys.argv[1:])]

print("== FR-SRC-08 变异验牙（%d 处）==" % len(CASES))
results = []
for c in CASES:
    full = ROOT + "\\" + c["path"]
    original = read_text(full)
    norm = original.replace("\r\n", "\n")
    if c["old"] not in norm:
        print("  !! %s：找不到锚点，跳过" % c["label"])
        results.append((c["label"], "找不到锚点"))
        continue
    write_text(full, norm.replace(c["old"], c["new"], 1).replace("\n", "\r\n"))
    try:
        p = subprocess.run(
            [ROOT + r"\gradlew.bat", ":app:testDebugUnitTest", "--tests", c["pattern"], "--console=plain"],
            cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=900,
        )
        out = (p.stdout or "") + (p.stderr or "")
        failed = [l.strip() for l in out.splitlines() if "FAILED" in l]
        hit = any(c["expect"] in l for l in failed)
        m = re.search(r"(\d+) tests completed(?:, (\d+) failed)?", out)
        detail = "%s 例，失败 %s" % (m.group(1), m.group(2) or "0") if m else "?"
        print("  %-24s %s  （%s）" % (c["label"], "红了 ✓" if hit else "没红 ✗", detail))
        if hit:
            print("      %s" % [l for l in failed if c["expect"] in l][0][:110])
        results.append((c["label"], "红了 ✓" if hit else "没红 ✗"))
    finally:
        write_text(full, original)

print()
print("== 汇总 ==")
for label, r in results:
    print("  %-24s %s" % (label, r))
