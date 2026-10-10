# 把手机上的库**连 WAL 一起**拉到 artifacts/db/，并打一份人能读的核对报告。
#
# 为什么要连 WAL：Room 默认开着 WAL，提交的行可能还只在 `wheremy.db-wal` 里。
# 只拉主库会看到**旧快照**——2026-10-10 就因此误判过一次（以为"搬走"没生效，其实是没拉 WAL）。
#
# 用法：pwsh -File artifacts/tools/pull-db.ps1
# 产出：artifacts/db/pull-<包名>.db / -wal / -shm + pull-<包名>.txt（节点与物品->位置）
param(
    [string]$Package = "com.wheremy.app.dsh",
    [string]$Serial = "9aa4b43e",
    [string]$Adb = "D:\Lib\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$outDir = Join-Path $root "artifacts\db"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$tag = ($Package -split '\.')[-1]
foreach ($f in @("wheremy.db", "wheremy.db-wal", "wheremy.db-shm")) {
    $dest = Join-Path $outDir "pull-$tag-$f"
    & $Adb -s $Serial exec-out run-as $Package cat "databases/$f" > $dest 2>$null
}

$dbPath = Join-Path $outDir "pull-$tag-wheremy.db"
$reportPath = Join-Path $outDir "pull-$tag.txt"
python - "$dbPath" "$reportPath" <<'PY'
import io, sqlite3, sys
db, report = sys.argv[1], sys.argv[2]
con = sqlite3.connect(db)
cur = con.cursor()
with io.open(report, "w", encoding="utf-8") as out:
    out.write("位置节点：%s\n" % [r[0] for r in cur.execute("select name from location_node order by name")])
    out.write("位置树（子 <- 父）：\n")
    for name, parent in cur.execute(
        "select c.name, p.name from location_node c left join location_node p on p.id=c.parentId"
    ):
        out.write("  %s  <- %s\n" % (name, parent or "(根)"))
    out.write("物品 -> 位置：\n")
    for n, loc in cur.execute(
        "select i.name, l.name from item i left join location_node l on l.id=i.locationId order by i.name"
    ):
        out.write("  %s -> %s\n" % (n, loc or "(没归位)"))
con.close()
print("报告：%s" % report)
PY
