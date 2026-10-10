import io
import sqlite3
import sys

sys.stdout.reconfigure(errors="replace")
con = sqlite3.connect(r"C:\Users\lifxu\AppData\Local\Temp\s-wheremy.db")
cur = con.cursor()
out = io.open(r"D:\src\dsh\wheremy\artifacts\db\seed-categories.txt", "w", encoding="utf-8")
out.write("item 表列：%s\n" % [r[1] for r in cur.execute("pragma table_info(item)")])
out.write("物品：\n")
for r in cur.execute("select name, category, seasons from item order by name"):
    out.write("  %s\n" % (r,))
tabs = [r[0] for r in cur.execute("select name from sqlite_master where type='table'")]
out.write("所有表：%s\n" % tabs)
for t in tabs:
    if "tag" in t.lower():
        out.write("%s 的行：%s\n" % (t, cur.execute("select * from " + t).fetchall()))
out.close()
con.close()
print("ok")
