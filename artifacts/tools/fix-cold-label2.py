# 修 ci-local.ps1 的标题行：-Cold 时也要说清楚自己是什么模式。
# 上次的正则没匹配是因为行尾多一个引号；这次直接按内容子串替换。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\artifacts\tools\ci-local.ps1"
t = io.open(PATH, encoding="utf-8").read()

old = "$(if ($Clean) { 'clean 起' } else { '带缓存' })"
new = "$(if ($Cold) { 'cold：clean + 禁缓存 + 强制重跑' } elseif ($Clean) { 'clean 起' } else { '带缓存' })"
if old in t:
    t = t.replace(old, new, 1)
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("标题行已修")
else:
    # 兜底：按行找
    for line in t.split("\n"):
        if "本地 CI（" in line and "Write-Output" in line:
            print("实际行：%s" % line.strip())
    print("!! 没匹配到标题行")
