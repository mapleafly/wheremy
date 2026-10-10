# 对账：PRD 条目标题上的状态标记 vs DELTA 矩阵里"本实现"那一列。
# 两边必须一致；不一致就打印出来（归档时最容易在这里漏一条）。
import re
import sys

PRD = r"D:\src\dsh\wheremy\docs\PRD.md"
DELTA = r"D:\src\dsh\wheremy\docs\DELTA.md"

delta = {}
for line in open(DELTA, encoding="utf-8"):
    m = re.match(r"^\|\s*`(FR-[A-Z]+-\d+)`\s*\|(.+)$", line)
    if not m:
        continue
    cells = [c.strip() for c in line.strip().strip("|").split("|")]
    if len(cells) >= 4:
        delta[m.group(1)] = cells[3]

prd = {}
for line in open(PRD, encoding="utf-8"):
    # 标记后面可能跟括号说明（如「✅（安全条款）」），只取标记那一个字符
    m = re.match(r"^\*\*(?:⭐|⛔)?(FR-[A-Z]+-\d+)\s+([^*]+)\*\*\s*([✅🟡⬜⛔])", line)
    if m:
        prd[m.group(1)] = (m.group(3), m.group(2).strip())

print("DELTA 有 %d 条，PRD 标题里找到 %d 条" % (len(delta), len(prd)))

only_delta = sorted(set(delta) - set(prd))
only_prd = sorted(set(prd) - set(delta))

bad = []
for fid, status in sorted(delta.items()):
    if fid not in prd:
        continue
    prd_status, title = prd[fid]
    if prd_status != status:
        bad.append((fid, title, status, prd_status))

REPORT = r"D:\src\dsh\wheremy\artifacts\tools\prd-delta-report.txt"
with open(REPORT, "w", encoding="utf-8") as f:
    f.write("只在 DELTA 里：%s\n" % (", ".join(only_delta) or "（无）"))
    f.write("只在 PRD 里：%s\n" % (", ".join(only_prd) or "（无）"))
    f.write("不一致 %d 条：\n" % len(bad))
    for fid, title, d, p in bad:
        f.write("  %s %s | DELTA=%s PRD=%s\n" % (fid, title, d, p))

print("不一致 %d 条，报告写到 %s" % (len(bad), REPORT))
sys.exit(1 if (bad or only_delta or only_prd) else 0)
