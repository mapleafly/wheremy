import io
import sqlite3
import sys

sys.stdout.reconfigure(errors="replace")
con = sqlite3.connect(r"C:\Users\lifxu\AppData\Local\Temp\h-wheremy.db")
cur = con.cursor()
out = io.open(r"D:\src\dsh\wheremy\artifacts\db\after-free-facet.txt", "w", encoding="utf-8")
rows = cur.execute("select name, category, seasons, searchText from item order by name").fetchall()
out.write("共 %d 件\n" % len(rows))
for name, category, seasons, search in rows:
    out.write("  %s | 类别=%s | 季节=%s\n    searchText=%r\n" % (name, category, seasons, search))
out.close()
con.close()
print("ok")
