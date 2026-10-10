# 修超长行（ktlint max-line-length 120）。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\data\backup\BackupAsymmetryTest.kt"
t = io.open(PATH, encoding="utf-8").read()

old = '''                """{"formatVersion":1,"schemaVersion":$DB_SCHEMA_VERSION,"appVersion":"x","exportedAt":1}""".toByteArray(),'''
new = '''                (
                    """{"formatVersion":1,"schemaVersion":$DB_SCHEMA_VERSION,""" +
                        """"appVersion":"x","exportedAt":1}"""
                ).toByteArray(),'''
assert old in t, "没找到那行"
t = t.replace(old, new, 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已拆行")
