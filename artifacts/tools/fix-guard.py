# 修守卫脚本：Windows 上 py_compile 的 cfile 不能用 os.devnull，用临时文件。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\artifacts\tools\check-tools-syntax.py"
t = io.open(PATH, encoding="utf-8").read()

old = """import glob
import os
import py_compile
import sys"""
new = """import glob
import os
import py_compile
import sys
import tempfile"""
t = t.replace(old, new, 1)

old2 = """for path in sorted(glob.glob(os.path.join(here, "*.py"))):
    try:
        py_compile.compile(path, doraise=True, cfile=os.devnull)
    except py_compile.PyCompileError as e:
        bad.append((os.path.basename(path), str(e).splitlines()[0]))"""
new2 = """# Windows 上 cfile 不能用 os.devnull（会报 non-regular file），用一个临时文件
with tempfile.TemporaryDirectory() as tmp:
    cfile = os.path.join(tmp, "out.pyc")
    for path in sorted(glob.glob(os.path.join(here, "*.py"))):
        try:
            py_compile.compile(path, doraise=True, cfile=cfile)
        except py_compile.PyCompileError as e:
            bad.append((os.path.basename(path), str(e).splitlines()[0]))"""
t = t.replace(old2, new2, 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("守卫脚本已修")
