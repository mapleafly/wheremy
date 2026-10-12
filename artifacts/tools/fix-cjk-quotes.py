# 通用工具：把 Python 脚本里「中文引语」用的 ASCII 双引号换成「」。
#
# 为什么需要它：在 Python 字符串里写中文引语时若用 ASCII 双引号，会在**解析期**直接崩
# （一处都不执行），而写脚本的人常常以为"已经改过了"。这个坑今天踩了 7 次。
#
# 用法：
#   python fix-cjk-quotes.py                # 处理本目录下所有 *.py
#   python fix-cjk-quotes.py <文件> [<文件>...]   # 只处理指定文件
#
# 只动「中文字符紧邻的成对 ASCII 引号」，不碰 Python 的字符串定界符。
import glob
import io
import os
import re
import sys

sys.stdout.reconfigure(errors="replace")
CJK = r"[\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]"
PAT = re.compile("(" + CJK + ")\"([^\"\\n]{1,24}?)\"(" + CJK + "|[，。；：、！？）]|$)")

here = os.path.dirname(os.path.abspath(__file__))
targets = sys.argv[1:] or sorted(glob.glob(os.path.join(here, "*.py")))

total = 0
for path in targets:
    if not os.path.isfile(path):
        print("  跳过（不是文件）：%s" % path)
        continue
    t = io.open(path, encoding="utf-8").read()
    n = 0
    while True:
        m = PAT.search(t)
        if not m:
            break
        t = t[: m.start()] + m.group(1) + "「" + m.group(2) + "」" + m.group(3) + t[m.end() :]
        n += 1
        if n > 80:
            break
    if n:
        io.open(path, "w", encoding="utf-8", newline="").write(t)
        print("  %s：修了 %d 处" % (os.path.basename(path), n))
        total += n
print("共修 %d 处" % total)
