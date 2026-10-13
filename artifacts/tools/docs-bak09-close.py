# ARCHITECTURE §10 更正块 + §10.4 加密容器 + PLAN F51 + PRD/DELTA/提案收口 + CHANGELOG
import io
import sys

sys.stdout.reconfigure(errors="replace")
D = r"D:\src\dsh\wheremy\docs"
ok, fail = 0, []


def sub(path, old, new, label):
    global ok
    t = io.open(path, encoding="utf-8", newline="").read()
    if old not in t:
        fail.append(label)
        return
    io.open(path, "w", encoding="utf-8", newline="").write(t.replace(old, new, 1))
    ok += 1


correction = """## 10. 备份链路

> ⚠️ **2026-10-13 更正（`PLAN.md` F51）**：本节原先是照着**参照实现**（`D:\\src\\qoder\\wheremy`）的备份链路写的——
> `§10.1 BackupFormat.kt`、`§10.2 BackupIo.kt` 这两个**文件名在本实现里根本不存在**（那是参照实现的文件），
> 里面的行号与 API 名（`export(into:)` / `import(from:)`）也都是那边的。本文档自称 as-built，这是失真。
>
> **本实现的实际文件与入口**（以代码为准）：
>
> | 文件 | 负责 |
> |---|---|
> | `data/backup/BackupModels.kt` | 常量与序列化模型：`BACKUP_FORMAT_VERSION`(2) / `PLAIN_FORMAT_VERSION`(1) / `ENTRY_*`、`BackupManifest`、`BackupEncryption`、`BackupData`、`ImportMode`、`BackupSummary`、`BackupFormatException` 与两个子类、`isPhotoEntry` 白名单 |
> | `data/backup/BackupCrypto.kt` | 加密原语（`ADR-27`）：PBKDF2 派生、AES-256-GCM 按条目加解密、verifier。**不认识 zip / 数据库 / 界面** |
> | `data/backup/BackupSink.kt` | 落点抽象（SAF 目录树写入 + 文件名前缀） |
> | `data/backup/BackupRepository.kt` | `exportTo(stream, at, password)` / `importFrom(stream, mode, password)` |
> | `data/backup/AutoExporter.kt` + `AutoExportWorker.kt` | 定时自动导出（用本机记住的口令，`FR-BAK-08`） |
>
> **规则**（逐字文案、判据、四种拒）以 `RULES.md` §12 为准，那份是当前且逐条核对过的；
> 本节 §10.1–§10.3 的正文**保留为参照实现的描述**，重做或排障时别拿它的行号去对代码。

### 10.1–10.3（参照实现的备份链路，保留备查）

"""
sub(D + r"\ARCHITECTURE.md", "## 10. 备份链路（三个文件）\n\n", correction, "ARCH §10 更正块")

crypto_section = """### 10.4 加密容器 v2（`FR-BAK-09` / `ADR-27`，2026-10-13）

**两种包并存**，读的人靠 `manifest.json` 的 `encryption` 段分辨：

| 条目 | 明文包（v1，`formatVersion = 1`） | 加密包（v2，`formatVersion = 2`） |
|---|---|---|
| `manifest.json` | 明文 | **明文**（不这样读的人不知道要不要口令） |
| 表的行 | `data.json` | `data.enc` |
| 照片 | `photos/**` 明文 | 同名条目，内容是密文 |

- 条目内容 = **12 字节随机 IV + 密文与认证标签**（AES-256-GCM）；**AAD = 条目名**；
- 口令 → 密钥走 **PBKDF2-HMAC-SHA256**（salt 与迭代数写在 `encryption` 里，起步 210 000）；
- `encryption.verifierBase64` 是用派生密钥加密的一小段固定明文：**先验它**，于是
  「口令不对」与「文件坏了」能分开报（`BackupWrongPassword` vs `BackupFormatException`）；
- **导入是两阶段的**（这是本设计的关键）：`importFrom` 先把整包落到 `stagingDir/pack.zip`
  （SAF 给的流不能回头），再用 `ZipFile` 随机访问 → **先读 manifest** → 才知道要不要口令 →
  `keyFor()` 要口令/验口令 → 再逐条目解密。**口令不对或没给口令时，一张表都还没写过**；
- `formatVersion > BACKUP_FORMAT_VERSION` 直接拒（「这个备份来自更新的版本，先升级应用再导入」）——
  将来加 v3 时，旧应用会拒收而不是按老规矩乱读；
- **明文包永远是 v1**（不加密导出写 `PLAIN_FORMAT_VERSION`）：这样"不加密"的包在任何旧版本上都能导入；
- 口令存在本机 DataStore（`AppSettings.backupPassword`），**不进包、不同步**（`FR-BAK-04`）；
  自动导出用它，换机导入要手输（`RULES.md` §12.5）。

**为什么先落盘再读**：加密包必须在解析负载**之前**就知道口令；而 `ContentResolver` 给的 `InputStream`
只能读一遍。整包落盘后 `ZipFile` 可以随机访问（先 manifest、再负载），代价是包体积的临时磁盘占用——
个人量级（几 MB）可接受，几百 MB 的库要改成流式两遍读。

---

"""
sub(D + r"\ARCHITECTURE.md", "## 11. 呈现层", crypto_section + "## 11. 呈现层", "ARCH §10.4")

# ---- PLAN F51 ----
sub(D + r"\PLAN.md",
    "| F50 | **TalkBack 走查第二轮抓到三条**",
    "| F51 | **`ARCHITECTURE.md` §10「备份链路」是照参照实现写的**：`BackupFormat.kt` / `BackupIo.kt` 这两个文件名**本实现里不存在**"
    "（实际是 `BackupModels.kt` / `BackupSink.kt` / `AutoExporter.kt`），行号与 API 名也都是参照那边的 ✗ "
    "本文档自称 as-built，属失真 | 2026-10-13 已加**更正块**（列出本实现的实际文件与入口，并把 §10.1–§10.3 标注为参照实现的描述）"
    "＋新增 **§10.4 加密容器 v2**（本实现的新链路，逐条对得上代码）；§10.1–§10.3 正文的逐条重写**留作后续**"
    "（规则已在 `RULES.md` §12 逐条核对过，不影响使用） |\n"
    "| F50 | **TalkBack 走查第二轮抓到三条**",
    "PLAN F51")

# ---- PRD / DELTA / 提案 ----
sub(D + r"\PRD.md",
    "**FR-BAK-09 备份加密** 🟡 **实施中**（2026-10-13 用户拍板「全包加密、自选口令」，ADR-27）",
    "**FR-BAK-09 备份加密** ✅ **已落地**（2026-10-13 用户拍板「全包加密、自选口令」，`ADR-27`）："
    "`formatVersion = 2`（明文 manifest + AES-256-GCM 负载 + verifier），口令不写进包、明文 v1 仍可读、"
    "更新的格式拒收；导出/导入两个对话框按 `RULES.md` §12.5 的逐字文案。"
    "`BackupEncryptionTest` 8 例 + `SettingsBackupEncryptionUiTest` 6 例守着，G2 六处变异 6/6 都红。"
    "**本实现超出参照实现**（参照没有加密）",
    "PRD FR-BAK-09")
sub(D + r"\DELTA.md",
    "| `FR-BAK-09` | 备份加密 | ⬜ | 🟡 |",
    "| `FR-BAK-09` | 备份加密 | ⬜ | ✅ |",
    "DELTA FR-BAK-09")
sub(D + r"\proposals\FR-BAK-09-备份加密.md",
    "| 一句话标题 + 状态 | 「导出的备份包可以加密，免得放进网盘就等于公开」——**✅ 已通过（2026-10-13 用户拍板：选 B 全包加密、口令自选；§5.2 的五条政策一并接受）**，决策记在 `DECISIONS.md` `ADR-27` |",
    "| 一句话标题 + 状态 | 「导出的备份包可以加密，免得放进网盘就等于公开」——**✅ 已落地（2026-10-13 立项当天做完）**："
    "`PRD.md` 状态已改 ✅，决策记在 `DECISIONS.md` `ADR-27`，§5.2 的五条政策进了 `RULES.md` §12.5 与界面逐字文案，"
    "守门测试 `BackupEncryptionTest` / `SettingsBackupEncryptionUiTest` |",
    "提案 BAK-09 状态")

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
