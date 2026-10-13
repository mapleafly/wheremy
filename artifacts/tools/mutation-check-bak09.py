# FR-BAK-09（备份加密）这一批的变异验牙（G2）。
import io
import re
import subprocess
import sys

sys.stdout.reconfigure(errors="replace")
ROOT = r"D:\src\dsh\wheremy"

REPO = r"app\src\main\java\com\wheremy\app\data\backup\BackupRepository.kt"
CRYPTO = r"app\src\main\java\com\wheremy\app\data\backup\BackupCrypto.kt"
VM = r"app\src\main\java\com\wheremy\app\ui\settings\SettingsViewModel.kt"


def read_text(p):
    return io.open(p, encoding="utf-8", newline="").read()


def write_text(p, t):
    io.open(p, "w", encoding="utf-8", newline="").write(t)


CASES = [
    dict(
        label="M11 不验口令校验块",
        path=REPO,
        old="        if (!BackupCrypto.verifierOk(key, BackupCrypto.fromBase64(enc.verifierBase64))) throw BackupWrongPassword()",
        new="        if (false) throw BackupWrongPassword()",
        pattern="*BackupEncryptionTest*",
        expect="口令不对时立刻说是口令不对，且一个字节都不写",
    ),
    dict(
        label="M12 没口令报成口令不对",
        path=REPO,
        old="        if (password == null) throw BackupPasswordRequired()",
        new="        if (password == null) throw BackupWrongPassword()",
        pattern="*BackupEncryptionTest*",
        expect="没给口令时是",
    ),
    dict(
        label="M13 不拦更新的格式",
        path=REPO,
        old="""                if (manifest.formatVersion > BACKUP_FORMAT_VERSION) {
                    throw BackupFormatException("这个备份来自更新的版本，先升级应用再导入")
                }""",
        new="""                if (false) {
                    throw BackupFormatException("这个备份来自更新的版本，先升级应用再导入")
                }""",
        pattern="*BackupEncryptionTest*",
        expect="比能读的格式更新的包要被拒",
    ),
    dict(
        label="M14 密文与 IV 写反",
        path=CRYPTO,
        old="        return iv + cipher.doFinal(plain)",
        new="        return cipher.doFinal(plain) + iv",
        pattern="*BackupEncryptionTest*",
        expect="加密往返",
    ),
    dict(
        label="M15 照片条目明文写出去",
        path=REPO,
        old="            zip.write(BackupCrypto.seal(key, file.readBytes(), entryName))",
        new="            zip.write(file.readBytes())",
        pattern="*BackupEncryptionTest*",
        expect="加密往返",
    ),
    dict(
        label="M16 口令不对并进导入失败",
        path=VM,
        old="""            } catch (e: BackupWrongPassword) {
                // 口令不对：**对话框留着**让人重试，错误显示在框里（不是"导入失败"那句话）
                _state.update { it.copy(needImportPassword = true, importPasswordError = e.message) }
                null
            } catch (e: BackupFormatException) {""",
        new="""            } catch (e: BackupFormatException) {""",
        pattern="*SettingsBackupEncryptionUiTest*",
        expect="口令不对：说口令不对，对话框留着",
    ),
]

if len(sys.argv) > 1:
    CASES = [c for c in CASES if any(a in c["label"] for a in sys.argv[1:])]

print("== FR-BAK-09 变异验牙（%d 处）==" % len(CASES))
results = []
for c in CASES:
    full = ROOT + "\\" + c["path"]
    original = read_text(full)
    norm = original.replace("\r\n", "\n")
    if c["old"] not in norm:
        print("  !! %s：找不到锚点，跳过" % c["label"])
        results.append((c["label"], "找不到锚点"))
        continue
    write_text(full, norm.replace(c["old"], c["new"], 1).replace("\n", "\r\n"))
    try:
        p = subprocess.run(
            [ROOT + r"\gradlew.bat", ":app:testDebugUnitTest", "--tests", c["pattern"], "--console=plain"],
            cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=900,
        )
        out = (p.stdout or "") + (p.stderr or "")
        failed = [l.strip() for l in out.splitlines() if "FAILED" in l]
        hit = any(c["expect"] in l for l in failed)
        m = re.search(r"(\d+) tests completed(?:, (\d+) failed)?", out)
        detail = "%s 例，失败 %s" % (m.group(1), m.group(2) or "0") if m else "?"
        print("  %-24s %s  （%s）" % (c["label"], "红了 ✓" if hit else "没红 ✗", detail))
        if hit:
            print("      %s" % [l for l in failed if c["expect"] in l][0][:110])
        results.append((c["label"], "红了 ✓" if hit else "没红 ✗"))
    finally:
        write_text(full, original)

print()
print("== 汇总 ==")
for label, r in results:
    print("  %-24s %s" % (label, r))
