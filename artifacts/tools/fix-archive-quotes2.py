# 修 archive-ci-green.py 里两处 ASCII 引号（会打断 Python 字符串）。
# 纪律：脚本里凡是中文引语一律「」；必须匹配原文 ASCII 引号时，Python 用单引号定界。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\artifacts\tools\archive-ci-green.py"
t = io.open(PATH, encoding="utf-8").read()

fixes = [
    ('**"本机加速"不能写进仓库配置**', "**「本机加速」不能写进仓库配置**"),
    ('ATF 自动化只覆盖"有没有名字/触摸目标够不够大"，**次序只能靠人听**（§10.5）',
     "ATF 自动化只覆盖「有没有名字/触摸目标够不够大」，**次序只能靠人听**（§10.5）"),
]
for old, new in fixes:
    if old in t:
        t = t.replace(old, new, 1)
        print("已修：%s" % old[:24])
    else:
        print("没找到：%s" % old[:24])

io.open(PATH, "w", encoding="utf-8", newline="").write(t)

# 顺手做一个"先编译再跑"的守卫脚本
GUARD = r"D:\src\dsh\wheremy\artifacts\tools\check-tools-syntax.py"
io.open(GUARD, "w", encoding="utf-8", newline="").write('''# 把 artifacts/tools 下所有 Python 脚本编译一遍，提前抓语法错。
# 为什么要有它：写脚本时若在字符串里混进 ASCII 双引号，脚本会在**解析期**就崩（一处都不改），
# 而那时我常常以为"已经改过了"。跑这个守卫比事后排查便宜得多。
import glob
import os
import py_compile
import sys

sys.stdout.reconfigure(errors="replace")
here = os.path.dirname(os.path.abspath(__file__))
bad = []
for path in sorted(glob.glob(os.path.join(here, "*.py"))):
    try:
        py_compile.compile(path, doraise=True, cfile=os.devnull)
    except py_compile.PyCompileError as e:
        bad.append((os.path.basename(path), str(e).splitlines()[0]))
if bad:
    print("语法有问题的脚本 %d 个：" % len(bad))
    for name, msg in bad:
        print("  %s → %s" % (name, msg[:100]))
    sys.exit(1)
print("artifacts/tools 下所有 Python 脚本语法 OK")
''')
print("守卫脚本已生成：check-tools-syntax.py")
