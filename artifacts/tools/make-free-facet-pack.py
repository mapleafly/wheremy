# 造一个"词表外的类别/季节"的备份包，用来在真机上验 FR-AI-11。
# 等价于：模型回了 category="数码产品"、seasons="冬季" 的那种数据。
import io
import json
import sys
import zipfile

sys.stdout.reconfigure(errors="replace")
OUT = r"D:\src\dsh\wheremy\artifacts\backup\free-facet-pack.zip"

manifest = {
    "formatVersion": 1,
    "schemaVersion": 1,
    "appVersion": "0.1.0-test",
    "exportedAt": 1,
}
item = {
    "id": "free-facet-1",
    "name": "移动硬盘",
    "category": "数码产品",   # 词表外
    "seasons": "冬季",        # 词表外
    "color": None,
    "brand": None,
    "material": None,
    "aliases": None,
    "description": "1TB，蓝色外壳",
    "notes": None,
    "quantity": 1,
    "locationId": None,
    "status": "CONFIRMED",
    "expiresAt": None,
    "expireKind": None,
    "expireNotifiedAt": None,
    "searchText": "移动硬盘",
    "createdAt": 1,
    "updatedAt": 1,
    "lastMovedAt": None,
}
data = {"locations": [], "items": [item], "photos": []}

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False))
    z.writestr("data.json", json.dumps(data, ensure_ascii=False))

print("已生成 %s" % OUT)
