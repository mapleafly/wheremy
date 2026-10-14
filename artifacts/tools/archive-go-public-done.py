# 把"已翻转"的执行结果补进 CHANGELOG
import io
import sys

sys.stdout.reconfigure(errors="replace")
p = r"D:\src\dsh\wheremy\docs\CHANGELOG.md"
t = io.open(p, encoding="utf-8", newline="").read()
old = "## 2026-10-14 · 仓库转公开（渠道 A）：先扫一遍，再翻开关"
new = (
    "## 2026-10-14 · 仓库转公开（渠道 A）：先扫一遍，再翻开关 —— **已执行**\n\n"
    "> **2026-10-14 执行完毕**：`gh repo edit --visibility public`，仓库现为 **PUBLIC**"
    "（https://github.com/mapleafly/wheremy）。翻转后核对：README 已被 GitHub 识别（3,656 字节）、"
    "描述与八个主题已填（android / jetpack-compose / kotlin / room / local-first / offline-first / material3 / hilt）、"
    "CI 在新提交上仍然全绿（run 38502060420，1m41s）。**LICENSE 仍为「无」**（＝保留所有权利，等用户定）。"
    "附带的好处：公开仓库的 Actions 分钟数免费，GitHub 还会默认开 secret scanning——多一道安全网。\n"
)
assert old in t, "找不到锚点"
io.open(p, "w", encoding="utf-8", newline="").write(t.replace(old, new, 1))
print("CHANGELOG 已补执行结果")
