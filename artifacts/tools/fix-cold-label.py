# 修 ci-local.ps1 的标题行（-Cold 时也应说 cold）。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\artifacts\tools\ci-local.ps1"
t = io.open(PATH, encoding="utf-8").read()

m = re.search(r'^Write-Output "== 本地 CI（.*==\n', t, flags=re.M)
if m:
    new = 'Write-Output "== 本地 CI（$(if ($Cold) { \'cold：clean + 禁缓存 + 强制重跑\' } elseif ($Clean) { \'clean 起\' } else { \'带缓存\' }))=="\n'
    t = t[: m.start()] + new + t[m.end() :]
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("已改标题行")
else:
    print("没找到标题行")
