# 归档本轮：F38（开发期入口在 release 里可达）+ 装机坑 3（第二次 install 成功意味着旧包被卸载）+ CHANGELOG
import io
import re

PLAN = r"D:\src\dsh\wheremy\docs\PLAN.md"
KEYSTORE = r"D:\src\dsh\wheremy\docs\KEYSTORE.md"

# —— PLAN §3.4 追加 F38 ——
p = io.open(PLAN, encoding="utf-8").read()
f38 = (
    "| F38 | **开发期入口在 release 包里仍然可达**：`--es dev seed` 会种演示数据、`--es dev words` 能改设置——"
    "路由注册与 extra 解析都没按 `BuildConfig.DEBUG` 门控 | 用户装了自己签名的 release 包之后核对时发现。"
    "把 extra → 路由的映射抽成纯函数 `devRouteFor(dev, debugBuild)`（release 里一律返回 null），"
    "并把五条开发期路由的注册整体放进 `if (BuildConfig.DEBUG)`——release 里它们连同这些屏一起被 R8 裁掉。"
    "证据：`DevRouteGateTest`（3 例，显式传 `debugBuild=false` 钉住 release 那一支）；真机装 release 后 `--es dev seed` **没有任何反应**（照片墙仍是空的） |\n"
)
anchor = "\n\n"
# 插在 F37 那一段之后（F37 是当前最后一条发现）
m = re.search(r"^\| F37 \|.*\n", p, flags=re.M)
assert m, "PLAN 里没找到 F37 那一行"
p = p[: m.end()] + f38 + p[m.end() :]
io.open(PLAN, "w", encoding="utf-8", newline="").write(p)
print("PLAN 已追加 F38")

# —— KEYSTORE.md §7 补第三个坑 ——
k = io.open(KEYSTORE, encoding="utf-8").read()
anchor7 = "### 坑 2：`versionCode` 只能涨，不能降"
assert anchor7 in k, "KEYSTORE §7 坑 2 锚点没找到"
pit3 = (
    "### 坑 3：第一次 `install` 失败之后**别急着再按一次**\n\n"
    "第一次失败会是 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（同包名、不同签名）。**如果第二次同样一条命令成功了**，"
    "那说明中间有人（或 MIUI 的安装对话框）把旧包**卸载**了——而卸载会**清掉旧包的全部数据**。\n\n"
    "所以看到第一次失败时，先决定走哪条路，再动手：\n\n"
    "- **要并存** → 给 release 加 `applicationIdSuffix`（例如 `.mine`）重新出包，两个应用各过各的；\n"
    "- **要替换** → **先**在旧包里「设置 → 备份 → 导出到文件」，把 zip 存到 `Download` 之外（例如电脑上），"
    "再卸载旧包、装新包、用「覆盖导入」把数据灌回来。\n\n"
    "⚠️ 本条是 2026-10-10 的真实教训：`com.wheremy.app` 被替换之后，旧应用的数据不在手机上了，"
    "好在 `/sdcard/Download` 里还留着之前导出的 zip，数据是可恢复的——**导出这一步是唯一的退路**。\n\n"
)
k = k.replace(anchor7, pit3 + anchor7, 1)
io.open(KEYSTORE, "w", encoding="utf-8", newline="").write(k)
print("KEYSTORE §7 已补坑 3")
