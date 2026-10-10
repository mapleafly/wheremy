# 记 CI 首次真跑全绿 + 登记 TalkBack 走查单 + CHANGELOG
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
DOCS = r"D:\src\dsh\wheremy\docs"
PLAN = DOCS + r"\PLAN.md"
RELEASE = DOCS + r"\RELEASE.md"
TESTING = DOCS + r"\TESTING.md"
PROJECT = DOCS + r"\PROJECT.md"
PROCESS = DOCS + r"\PROCESS.md"
CHANGELOG = DOCS + r"\CHANGELOG.md"

RUN = "38056478443"
RUN_URL = "https://github.com/mapleafly/wheremy/actions/runs/" + RUN

# —— PLAN：CI 行 + §3.3 第 9 步 ——
p = io.open(PLAN, encoding="utf-8").read()
old = "**真 GitHub 上仍未跑过**——本工作区没有 git 远端，建仓库与首次推送必须由人做（命令见 `RELEASE.md` §9 第 10 条） |"
new = ("**2026-10-10 首次真跑全绿**：`mapleafly/wheremy`（私人仓库），run "
       "[`" + RUN + "`](" + RUN_URL + ")，8m32s，四道门 + 上传产物全 success |")
if old in p:
    p = p.replace(old, new, 1)
    print("PLAN 的 CI 行已更新为真跑全绿")
else:
    print("!! PLAN CI 行没匹配")

old9 = "| 9 | CI | ⏳ 文件已写，**未在真 GitHub 上执行** | `.github/workflows/ci.yml` |"
new9 = ("| 9 | CI | ✅ **2026-10-10 首次真跑全绿**（`mapleafly/wheremy` 私人仓库，run `" + RUN + "`，8m32s，"
        "四道门全 success）；首跑抓出并修掉三处：`gradlew` 缺可执行位（exit 126）、"
        "阿里云镜像在 runner 上 502 导致插件解析全盘失败、无障碍测试的 Main 调度器 flake | `.github/workflows/ci.yml` |")
if old9 in p:
    p = p.replace(old9, new9, 1)
    print("PLAN §3.3 第 9 步已更新")
else:
    print("!! PLAN 第 9 步没匹配")

# F46 / F47
extra = (
    "| F46 | **仓库配置里硬编码中国大陆镜像，把 CI 搞红了** | 第一次真 CI 报「KSP 插件在所有仓库里找不到」。"
    "在 runner 上 curl 实测：Maven Central **200**、plugins.gradle.org **303**、"
    "两个阿里云镜像**都是 502**——镜像排在 `pluginManagement` 第一位，插件解析整体失败。"
    "而且对本机也没用：本机另有 init 脚本挂了同样的镜像，真正生效的是那一份。"
    "**修法**：仓库配置只用官方三源（可移植），镜像归本机 init 脚本。"
    "教训：**「本机加速」不能写进仓库配置** |\n"
    "| F47 | **两条无障碍测试没设 Main 调度器，在慢 runner 上 flake** | `verifyRoborazziDebug` 在 runner 上"
    "挂在 `ReviewAccessibilityTest` 的 `waitUntil` 超时；本地同一条命令却通过 → flake。"
    "根因：这两条测试没把 `Dispatchers.Main` 设成 `Unconfined`，VM 协程靠主 Looper 推进。"
    "**修法**：与其它 VM 测试统一，`@Before` 设 Unconfined、`@After` reset。"
    "教训：**CI 的机器比本机慢，超时类断言要留足余量，且别依赖 Looper 推进** |\n"
)
m = re.search(r"^\| F45 \|.*\n", p, flags=re.M)
if m and "F46" not in p:
    p = p[: m.end()] + extra + p[m.end() :]
    print("PLAN 已追加 F46/F47")
else:
    print("PLAN F46/F47 跳过")
io.open(PLAN, "w", encoding="utf-8", newline="").write(p)

# —— RELEASE §9 第 10 条：标成已做 ——
r = io.open(RELEASE, encoding="utf-8").read()
old10 = "10. [ ] **CI 首次真跑**（只在第一次或换了远端时做）：本工作区**没有 git 远端**，所以这一步必须由人做——"
new10 = ("10. [x] **CI 首次真跑** ✅ **2026-10-10 已完成**：私人仓库 `mapleafly/wheremy`，run "
         "[`" + RUN + "`](" + RUN_URL + ")，8m32s，四道门全 success。"
         "（首跑抓出三处问题，见 `PLAN.md` §3.3 第 9 步与 F46/F47。）以下为当时的操作步骤，换远端时重做——")
if old10 in r:
    r = r.replace(old10, new10, 1)
    print("RELEASE §9 第 10 条已标完成")
else:
    print("!! RELEASE 第 10 条没匹配")

# 在 §9 里补一条"无障碍专项"（把 TalkBack 走查单登记进来）
old21 = "21. [ ] 真机人工清单七条逐条签收（`REBUILD.md` §6）；交给用户的那份用 `templates/device-acceptance.md`"
if old21 in r:
    add = (old21 + "\n"
           "22. [ ] **无障碍专项：TalkBack 焦点次序走查**（`FR-PRM-05` 里唯一必须人工的一条）——"
           "交给用户的那份用 `docs/templates/talkback-walkthrough.md`；"
           "ATF 自动化只覆盖「有没有名字/触摸目标够不够大」，**次序只能靠人听**（§10.5）")
    r = r.replace(old21, add, 1)
    print("RELEASE §9 已补无障碍专项那一条")
else:
    print("!! RELEASE 没找到第 21 条")
io.open(RELEASE, "w", encoding="utf-8", newline="").write(r)

# —— TESTING：模板表里加一行 ——
t = io.open(TESTING, encoding="utf-8").read()
old_row = "| 真机验收单 | 交付/发布前（G5） | `templates/device-acceptance.md` |"
if old_row in t and "talkback-walkthrough" not in t:
    t = t.replace(old_row, old_row + "\n| TalkBack 走查单 | 交付/发布前（G5，无障碍专项） | `templates/talkback-walkthrough.md` |", 1)
    print("TESTING 模板表已加一行")
else:
    print("TESTING 模板表跳过（没匹配或已有）")
io.open(TESTING, "w", encoding="utf-8", newline="").write(t)

# —— PROJECT：交付物表里加一行 ——
pr = io.open(PROJECT, encoding="utf-8").read()
old_p = "| 4 | 真机验收签收单 | `templates/device-acceptance.md` | 七条人工清单逐条回\"看到了什么\" |"
if old_p in pr and "talkback-walkthrough" not in pr:
    pr = pr.replace(old_p, old_p + "\n| 4b | TalkBack 走查单（无障碍专项） | `templates/talkback-walkthrough.md` | 逐屏回\"读屏念的顺序对不对、有没有读不出名字的控件\" |", 1)
    print("PROJECT 交付物表已加一行")
else:
    print("PROJECT 表跳过")
io.open(PROJECT, "w", encoding="utf-8", newline="").write(pr)

# —— PROCESS §5 矩阵：G5 那行补上专项 ——
pc = io.open(PROCESS, encoding="utf-8").read()
if "talkback-walkthrough" not in pc:
    m2 = re.search(r"^\| \*\*G5 真机人工清单\*\* \|.*$", pc, flags=re.M)
    if m2:
        pc = pc[: m2.end()] + "\n| **G5b 无障碍专项** | `templates/talkback-walkthrough.md` 里逐屏回\"次序对不对、有没有读不出名字的控件\" |" + pc[m2.end() :]
        print("PROCESS §5 已加 G5b 一行")
    else:
        print("!! PROCESS 没找到 G5 行")
io.open(PROCESS, "w", encoding="utf-8", newline="").write(pc)

# —— CHANGELOG ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-10 · CI 首次真跑全绿（并修掉它抓出来的三处问题）+ TalkBack 走查单

**第一次在真实 GitHub 上跑 CI**：私人仓库 [`mapleafly/wheremy`](https://github.com/mapleafly/wheremy)，
run [`""" + RUN + """`](""" + RUN_URL + """)，**8m32s，四道门全 success**（Spotless → 单元测试 → 截图回归 → R8 发布构建）。

**这一轮最大的价值是"真跑抓出了三个本地永远发现不了的问题"**（都记进 `PLAN.md` §3.4）：

1. **`gradlew` 没有可执行位**（Windows 上 git 不保留 Unix 权限位）→ runner 上 `./gradlew` 报 **exit 126**
   （找到了但不可执行）。本地一直用 `gradlew.bat`，所以永远撞不到；
2. **仓库配置里硬编码的阿里云镜像把 CI 搞红了（F46）**：报「KSP 插件在所有仓库里找不到」。
   在 runner 上 curl 实测——Maven Central **200**、plugins.gradle.org **303**、**两个阿里云镜像都是 502**。
   镜像排在 `pluginManagement` 第一位，插件解析整体失败；而且对本机也没用（本机另有 init 脚本挂了同样的镜像，
   真正生效的是那一份）。**修法**：仓库配置只用官方三源（可移植），镜像归本机 init 脚本。
   教训：**「本机加速」不能写进仓库配置**；
3. **两条无障碍测试的 flake（F47）**：`verifyRoborazziDebug` 在 runner 上挂在 `ReviewAccessibilityTest`
   的 `waitUntil` 超时，而本地同一条命令通过。根因是这两条测试没把 `Dispatchers.Main` 设成 `Unconfined`，
   VM 协程靠主 Looper 推进，runner 慢/忙时就等不到。已与其它 VM 测试统一。

**顺带**：把仓库推到了 GitHub 之后，`.gitignore` 里 `artifacts/tools/` 的脚本也一并入库（文档点名引用它们）。

**新增 `docs/templates/talkback-walkthrough.md`**：`FR-PRM-05` 里唯一必须人工的一条——
**TalkBack 焦点次序走查**（ATF 自动化只覆盖「有没有名字、触摸目标够不够大」，**次序只能靠人听**）。
清单按屏分 7 段（照片墙 / 位置 / 待归类 / 确认页 / 详情 / 多选态 / 设置），每段写清"应该听到什么次序"与"要盯哪几件事"；
已登记进 `PROCESS.md` §5（G5b）、`TESTING.md`、`PROJECT.md`、`RELEASE.md` §9 第 22 条。

---

"""
anchor = c.index("## 2026-10-10 · 工程侧收尾")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")
