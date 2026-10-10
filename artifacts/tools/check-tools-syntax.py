# 把 artifacts/tools 下所有 Python 脚本编译一遍，提前抓语法错。
# 为什么要有它：写脚本时若在字符串里混进 ASCII 双引号，脚本会在**解析期**就崩（一处都不改），
# 而那时我常常以为"已经改过了"。跑这个守卫比事后排查便宜得多。
import glob
import os
import py_compile
import sys
import tempfile

sys.stdout.reconfigure(errors="replace")
here = os.path.dirname(os.path.abspath(__file__))
bad = []
# Windows 上 cfile 不能用 os.devnull（会报 non-regular file），用一个临时文件
with tempfile.TemporaryDirectory() as tmp:
    cfile = os.path.join(tmp, "out.pyc")
    for path in sorted(glob.glob(os.path.join(here, "*.py"))):
        try:
            py_compile.compile(path, doraise=True, cfile=cfile)
        except py_compile.PyCompileError as e:
            bad.append((os.path.basename(path), str(e).splitlines()[0]))
if bad:
    print("语法有问题的脚本 %d 个：" % len(bad))
    for name, msg in bad:
        print("  %s → %s" % (name, msg[:100]))
    sys.exit(1)
print("artifacts/tools 下所有 Python 脚本语法 OK")
