# 修正 DELTA.md L114：把 ✅ 从「参照状态」列挪到「本实现」列，参照列还原为 ⬜ 未做。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\docs\DELTA.md"
t = io.open(PATH, encoding="utf-8").read()

wrong = ("| ⭐FR-RLS-01 release 签名包 | ✅ **已完成（2026-10-10，用户自己做）** | ⬜ | "
         "用户生成 keystore、仓库外放 `keystore.properties`；`signingReport` 为 `Config: release`，"
         "真机装的是正式签名包（`KEYSTORE.md`） | |")
right = ("| ⭐FR-RLS-01 release 签名包 | ⬜ 未做 | ✅ **已完成（2026-10-10，用户自己做）** | "
         "用户生成 keystore、仓库外放 `keystore.properties`；`signingReport` 为 `Config: release`，"
         "真机装的是正式签名包（`KEYSTORE.md`） | |")

if wrong in t:
    t = t.replace(wrong, right, 1)
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("DELTA L114 已修正：参照列还原为 ⬜ 未做，本实现列为 ✅")
else:
    print("没匹配到那一行，实际内容：")
    for i, line in enumerate(t.split("\n"), 1):
        if "FR-RLS-01" in line:
            print("  L%d: %s" % (i, line))
