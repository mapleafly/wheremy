# CHANGELOG：FR-BAK-09 备份加密落地（先红后绿 + 6/6 验牙）
import io
import sys

sys.stdout.reconfigure(errors="replace")
C = r"D:\src\dsh\wheremy\docs\CHANGELOG.md"
section = """## 2026-10-13 · FR-BAK-09 落地：备份包可以加密了（先红后绿 + 变异验牙 6/6 都红）

按 `PROCESS.md` §3.1 的顺序走：**文档 → 红测试 → 实现 → 判绿 → 验牙**。

**文档先行**：`RULES.md` 新增 **§12.5 备份加密**（容器表、算法/迭代数/AAD/verifier 的取舍、
口令规则 📐 ≥ 8 位、**逐字文案**、兼容承诺）；`UI.md` 新增 **§7.10**（导出前的口令对话框、导入时的口令框、
状态行两句）；`RELEASE.md` §5.2 补**第二条闸门**（`formatVersion > 2` 就拒）；`GLOSSARY.md` 加三个词。

**红测试**（`BackupEncryptionTest`，用手工造的包，不需要新 API 就能红）：
「加密包没给口令时说清楚是要口令」当时会得到**「少了 data.json」**（驴唇不对马嘴）；
「更新的格式要被拒」当时**会被静默导入**（最危险的一条）。第三条「明文 v1 包永远能导入」当时就绿——它是兼容承诺。

**实现**：
- 新文件 `data/backup/BackupCrypto.kt`：PBKDF2-HMAC-SHA256 派生 + AES-256-GCM 按条目加解密 +
  verifier（条目内容 = 12 字节 IV + 密文与标签；**AAD = 条目名**，换个名字粘过去验不过）；
- `BackupModels.kt`：`BACKUP_FORMAT_VERSION = 2`（**能读到的最高**）与 `PLAIN_FORMAT_VERSION = 1`（明文包写的）、
  `BackupEncryption`、`BackupPasswordRequired` / `BackupWrongPassword` 两个专用异常；
- `BackupRepository`：`exportTo(stream, at, password)`（null = 明文 v1，非空 = 加密 v2）、
  `importFrom(stream, mode, password)` 改成**两阶段**——先把整包落到 `stagingDir/pack.zip`
  （SAF 的流不能回头），再用 `ZipFile` **先读 manifest** 才知道要不要口令，然后才逐条目解密。
  **没口令/口令不对时一张表都还没写过**；
- `SettingsRepository`：`AppSettings.backupPassword`（**只在本机**，不进包不同步）+ `setBackupPassword`；
- `AutoExporter`：用本机记住的口令加密，回执那句也写明是「明文」还是「已加密」；
- 设置页：点「导出到文件」**先问口令**（「加密导出」/「不加密导出」两步；两次不一致/不足 8 位停在框里），
  导入加密包弹「这个备份包加密了」，输错显示 🗣「口令不对」并**留着对话框**；自动导出那一行两句状态 + 「改用明文」。

**测试**（+14 例，总 300）：`BackupEncryptionTest` 8 条（往返、错口令、要口令、密文被改一个字节就拒、
包里搜不到物品名、包里没有口令、v1 兼容、更新的格式拒收）；`SettingsBackupEncryptionUiTest` 6 条
（导出先问口令 / 两次不一致 / 状态行 / 导入三种收场）。

**变异验牙（G2）6 处，6/6 都红**：M11 不验 verifier、M12 没口令报成"口令不对"、M13 不拦更新的格式、
M14 IV 与密文写反、M15 照片条目明文写出去、M16 口令不对并进"导入失败"。改坏后全部还原，CI 再跑仍全绿。

**顺手修一处文档失真（`PLAN.md` F51）**：`ARCHITECTURE.md` §10「备份链路」原先照**参照实现**写
（`BackupFormat.kt` / `BackupIo.kt` 这两个文件本实现里根本不存在），已加**更正块**列出本实现的实际文件与入口，
并新增 **§10.4 加密容器 v2**（逐条对得上代码）；§10.1–§10.3 正文的逐条重写留作后续（规则在 `RULES.md` §12 已核对过）。

**判绿**：本地四道门全绿，**47 份 XML / 300 例**，对账零不一致；`PRD.md` 该条 → ✅、`DELTA.md` 同步。

---

"""
t = io.open(C, encoding="utf-8", newline="").read()
j = t.index("---\n\n") + len("---\n\n")
io.open(C, "w", encoding="utf-8", newline="").write(t[:j] + section + t[j:])
print("CHANGELOG 已插入")
