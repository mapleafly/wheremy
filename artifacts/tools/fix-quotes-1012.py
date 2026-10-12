# 把 archive-acceptance-1012.py 里中文引语用的 ASCII 双引号换成「」。
# 做法：只处理"中文字符紧邻的 ASCII 引号"这种模式，避免碰 Python 的字符串定界符。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\artifacts\tools\archive-acceptance-1012.py"
t = io.open(PATH, encoding="utf-8").read()

CJK = r"[\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]"
# 中文后的 "xxx"（内部是中文/字母数字）→ 「xxx」
pat = re.compile('(' + CJK + ')"([^"\\n]{1,20}?)"(' + CJK + '|[，。；：、！？）]|$)')

fixed = 0
while True:
    m = pat.search(t)
    if not m:
        break
    t = t[: m.start()] + m.group(1) + "「" + m.group(2) + "」" + m.group(3) + t[m.end() :]
    fixed += 1
    if fixed > 40:
        break

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("修了 %d 处中文引语" % fixed)
