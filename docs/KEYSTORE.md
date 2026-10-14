# 生成与保管 keystore（`⭐FR-RLS-01` 的操作手册）

> 这份文档解决一件事：**怎么给 Wheremy 生成一把自己的签名密钥，并且不让它丢、不让它泄漏**。
> 照着从 §1 敲到 §8 就算做完；§9 是检查清单，§10 是踩过的坑，§11 是本次在你这台机器上的实测记录。
>
> 相关：`RELEASE.md` §3.2（为什么这条是 ⭐）、§6.3（怎么装机）、§7.1（哪些能退哪些退不了）、
> `DECISIONS.md`（为什么不做自动更新/商店分发）。

---

## 0. 一句话说清它是什么

**keystore 是一把你自己生成的密钥（一个 `.jks` 文件 + 一个口令）**，Android 用它证明
"这个 APK 是同一个作者发的"。

- **它不是账号、不是证书、不用买、不用联网**——`keytool` 一条命令就生成，纯本地。
- **它只影响"能不能覆盖安装"这一件事**：同一个 `applicationId`（本应用是 `com.wheremy.app`）的包，
  只有**同一个密钥签名**才能覆盖安装；换了密钥，系统只允许先卸载再装，而卸载会**清掉应用数据**。
- **它跟代码、跟备份包都没有关系**：备份 zip 里没有它，也不需要它。

🔒 两条硬规矩，先记住再往下看：

1. **口令不进仓库、不进文档、不进聊天**（包括不要发给我）。它只进你的密码管理器。
2. **`.jks` 文件不进 git**。`.gitignore` 只是"不会误提交"，不等于"放这儿安全"。

---

## 1. 前置检查（30 秒）

本机（Windows 11）上这三样都齐，实测过：

| 工具 | 在哪 | 用途 |
|---|---|---|
| `keytool` | `C:\Users\lifxu\.version-fox\cache\java\current\bin\keytool.exe`（Temurin JDK 21.0.8） | 生成与查看 keystore |
| `apksigner` | `D:\Lib\Android\Sdk\build-tools\36.1.0\apksigner.bat` | 验证 APK 的签名者 |
| `./gradlew.bat` | 仓库根 | 出包与 `signingReport` |

核一遍（PowerShell，复制粘贴即可）：

```powershell
& 'C:\Users\lifxu\.version-fox\cache\java\current\bin\keytool.exe' -help | Select-Object -First 3
Test-Path 'D:\Lib\Android\Sdk\build-tools\36.1.0\apksigner.bat'
```

两条都该有输出/`True`。`keytool` 不在 PATH 上没关系，用上面的绝对路径。

---

## 2. 生成 keystore（一条命令）

**先决定两件事**：

- **放哪**：`D:\keys\wheremy-release.jks`（示例；**放仓库外面**，最好不在 C 盘、不在同步盘里）。
  仓库是 `D:\src\dsh\wheremy`，所以 `D:\keys\` 或 `D:\src\dsh\keys\` 都在仓库外。
- **别名（alias）**：`wheremy-release`（示例）。别名不是秘密，但**要记住**，写配置时要用。

建目录 → 生成：

```powershell
New-Item -ItemType Directory -Force -Path 'D:\keys' | Out-Null

& 'C:\Users\lifxu\.version-fox\cache\java\current\bin\keytool.exe' `
  -genkeypair -v `
  -keystore 'D:\keys\wheremy-release.jks' `
  -storetype PKCS12 `
  -alias wheremy-release `
  -keyalg RSA -keysize 4096 `
  -validity 10000 `
  -dname "CN=Wheremy, OU=Personal, O=Wheremy, L=Beijing, ST=Beijing, C=CN"
```

它会**交互式问两次口令**（"输入密钥库口令" 与 "再次输入新口令"；PKCS12 下 key 口令与库口令是同一个，
所以只问这一组）。口令至少 6 位，建议 16 位以上随机串，**存进密码管理器**。

### 每个参数为什么是这个值

| 参数 | 值 | 为什么 |
|---|---|---|
| `-storetype PKCS12` | PKCS12 | JDK 9 之后的默认与推荐格式；JKS 是旧格式。**写进配置时也要一致**（本仓库的 `keystore.properties` 不需要写 storeType，Gradle 按扩展名/内容识别；但你若手动 `keytool -list` 要带 `-storetype PKCS12`） |
| `-keyalg RSA -keysize 4096` | RSA 4096 | 兼容性最好（Android 全版本都认 RSA），4096 位是当下的稳妥值 |
| `-validity 10000` | ≈27 年 | **过期之后就不能再用它签新包了**（老包还能装）。Google Play 要求有效期到 2033 年之后；自用侧载也别选 25 年以下 |
| `-alias` | `wheremy-release` | 后面 `keyAlias` 要写同一个字符串，打错会在出包时报 `Keystore was tampered with, or password was incorrect` |
| `-dname` | `CN=Wheremy, ...` | 证书里的"发给谁"。自用随便填，但**填了就别再改**（改了就是另一张证书） |
| `-v` | 详细输出 | 能看见 `Generating 4,096 bit RSA key pair ...`，确认真的生成了 |

⚠️ **`keyPassword`**：PKCS12 下与 `storePassword` 相同；本仓库的配置允许你只写 `storePassword`
（`keyPassword` 缺省时自动用它）。若你坚持分开，生成时加 `-keypass <另一个口令>`。

---

## 3. 生成之后**立刻**做三件事

1. **备份三份**（这一步比生成更重要——**密钥丢了就再也签不出能覆盖安装的包**）：
   - 一份在**离线**介质（U 盘 / 移动硬盘，别一直插着）；
   - 一份在**另一台设备**或加密云盘（**先加密再上传**：7-Zip 打包 + 强口令，或 `gpg`）；
   - 一份在**本机另一个目录**（例如 `D:\keys\backup\`）。
   三份都要包含 `.jks` **和**口令（口令在密码管理器里，别写在同一张纸上放一起）。
2. **记进密码管理器**：条目名建议 `wheremy release keystore`，字段写：文件路径、别名、库口令、生成日期。
3. **确认它没进 git**：

```powershell
cd D:\src\dsh\wheremy
git status --short          # 不该出现 .jks / keystore.properties
git check-ignore -v D:\keys\wheremy-release.jks   # 仓库外的文件本来就不在版本控制里
```

---

## 4. 写 `keystore.properties`（**仓库外**）

本仓库的 `app/build.gradle.kts` 已经写好了读取逻辑：它会按顺序找这两处，找到就用，找不到就
**退回调试签名**并在出 release 包时喊一声。

1. `<仓库父目录>/keystore.properties` → 即 **`D:\src\dsh\keystore.properties`**（推荐，好找好备份）
2. `C:\Users\lifxu\.wheremy\keystore.properties`

推荐用第 1 处，内容四行（**口令换成你自己的**）：

```properties
storeFile=D:/keys/wheremy-release.jks
storePassword=<你的库口令>
keyAlias=wheremy-release
keyPassword=<你的库口令>
```

### ⚠️ 这里有一个**实测踩到的**坑

`.properties` 文件里**反斜杠是转义符**：写 `D:\keys\wheremy-release.jks` 会被读成
`D:keyswheremy-release.jks`（反斜杠连同后面那个字母一起被吃掉），
`signingReport` 里会显示一个怪路径。两种写法都对：

- **正斜杠**（推荐）：`storeFile=D:/keys/wheremy-release.jks`
- 或者**双反斜杠**：`storeFile=D:\\keys\\wheremy-release.jks`

路径也可以写**相对** `keystore.properties` 所在目录的相对路径（例如 `keys/wheremy-release.jks`
放在 `D:\src\dsh\` 下时写 `storeFile=keys/wheremy-release.jks`），Gradle 会按 properties 文件的目录解析。

---

## 5. 接到构建里（**不需要改代码**）

配置已经就绪，你只要按 §4 放好文件。行为是：

| 情况 | release 变体用什么签名 | 你会看到 |
|---|---|---|
| `keystore.properties` 存在且四项齐全 | 你的 keystore | `signingReport` 里 `Variant: release` → `Config: release`，`Store` 指向你的 `.jks` |
| 文件不存在 | **调试签名**（`D:\Lib\Android\debug.keystore`） | 出 release 包时打印一行：`⚠️ release 用的是**调试签名**：没找到 keystore.properties。正式交付前请按 docs/KEYSTORE.md 生成自己的 keystore。` |

代码位置（想自己核对就看这里）：`app/build.gradle.kts` 顶部的 `keystoreProperties`、
`android.signingConfigs`、`buildTypes.release.signingConfig`，以及文件末尾那句警告。

---

## 6. 验证（三条命令，逐条看输出）

```powershell
cd D:\src\dsh\wheremy

# ① 变体签名配置：release 那一段必须是 Config: release
.\gradlew.bat :app:signingReport --console=plain

# ② 出包（R8 + 资源收缩）
.\gradlew.bat :app:assembleRelease --console=plain

# ③ 验签名者：DN 与摘要应当是你那张证书
& 'D:\Lib\Android\Sdk\build-tools\36.1.0\apksigner.bat' verify --print-certs `
  app\build\outputs\apk\release\app-release.apk
```

`①` 期望看到（本次实测的真实输出形态）：

```
Variant: release
Config: release
Store:  C:\...\wheremy-release.jks
Alias:  wheremy-release
```

`③` 期望看到 `Signer #1 certificate DN: CN=Wheremy, ...` 与一条 `SHA-256 digest`。
**把那条 SHA-256 记进发布记录**（`RELEASE.md` §8.2）：它是"这个包确实出自这把密钥"的证据。

---

## 7. 出包与装机（**这一段有两个坑，先读完再动手**）

```powershell
# 装到手机（序列号 9aa4b43e 是你那台 Xiaomi 13）
& 'D:\Lib\Android\Sdk\platform-tools\adb.exe' -s 9aa4b43e install -r `
  app\build\outputs\apk\release\app-release.apk
```

### 坑 1：release 的包名与**参照实现那个应用**同名

release 的 `applicationId` 是 `com.wheremy.app`，而你手机上那个参照应用**也是** `com.wheremy.app`。
两个包名相同但签名不同 → 覆盖安装会失败（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），
**强行卸载再装会把参照应用的数据删掉**。所以：

- 想两个并存 → 先改 release 的 `applicationIdSuffix`（例如 `.mine`）再出包；
- 想用 release 顶掉参照应用 → **先**在参照应用里「导出到文件」留一份 zip，再卸载。

⚠️ 上面这条不是理论：本工作区为此专门有一个 `.r8smoke` 变体（规则同 release、只多一个包名后缀），
就是用来做"R8 之后还跑得起来"的真机冒烟的——**别拿 release 直装**。

### 坑 3：第一次 `install` 失败之后**别急着再按一次**

第一次失败会是 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（同包名、不同签名）。**如果第二次同样一条命令成功了**，那说明中间有人（或 MIUI 的安装对话框）把旧包**卸载**了——而卸载会**清掉旧包的全部数据**。

所以看到第一次失败时，先决定走哪条路，再动手：

- **要并存** → 给 release 加 `applicationIdSuffix`（例如 `.mine`）重新出包，两个应用各过各的；
- **要替换** → **先**在旧包里「设置 → 备份 → 导出到文件」，把 zip 存到 `Download` 之外（例如电脑上），再卸载旧包、装新包、用「覆盖导入」把数据灌回来。

⚠️ 本条是 2026-10-10 的真实教训：`com.wheremy.app` 被替换之后，旧应用的数据不在手机上了，好在 `/sdcard/Download` 里还留着之前导出的 zip，数据是可恢复的——**导出这一步是唯一的退路**。

### 坑 2：`versionCode` 只能涨，不能降

同一把密钥下，`versionCode` 更小的包**装不上去**（`INSTALL_FAILED_VERSION_DOWNGRADE`）。
出包前把 `app/build.gradle.kts` 的 `versionCode` +1、`versionName` 按语义化改（`RELEASE.md` §2.2）。

---

## 8. 换密钥 = 换应用（不可逆，读一遍）

- 同一个 `applicationId` **换密钥之后**：老用户（或你自己手机）**不能覆盖安装**，必须先卸载，
  **应用数据（库 + 照片 + key）全部丢失**。
- 能救的只有**导出过的备份 zip**：新包装好后用「设置 → 备份 → 覆盖导入」把数据灌回去
  （照片也在包里，会一起回来）。
- 所以：**在第一次正式交付之前把密钥定下来**。以后要换，就得接受"用户（你）要重装 + 重新导入"。
- 万一密钥真的丢了：只能**换一个 `applicationId`**（例如 `com.wheremy.app2`）当成新应用发布，
  老包再也签不出更新——所以 §3 的三份备份不是形式主义。

---

## 9. 检查清单（照着打勾）

- [ ] `keytool -genkeypair` 跑完，看到 `Generating 4,096 bit RSA key pair`
- [ ] `.jks` 放在**仓库外**（`D:\keys\` 之类）
- [ ] **三份备份**（离线一份、异机/加密云一份、本机另一目录一份）
- [ ] 口令与别名进了**密码管理器**；**没有**写进任何文档、聊天、仓库
- [ ] `D:\src\dsh\keystore.properties` 写好，`storeFile` 用**正斜杠**
- [ ] `git status --short` 干净（没有 `.jks` / `keystore.properties`）
- [ ] `:app:signingReport` 里 release 是 `Config: release` + 你的 `Store` / `Alias`
- [ ] `assembleRelease` 后 `apksigner verify --print-certs` 认出你的证书
- [ ] 证书 **SHA-256** 记进发布记录
- [ ] 想清楚 §7 的两个坑（包名冲突、versionCode）
- [ ] `versionCode` +1 / `versionName` 已改

---

## 10. 常见坑（都是会真卡住人的那种）

| 症状 | 原因 | 怎么办 |
|---|---|---|
| `signingReport` 里 `Store` 是一个怪路径（字母被吃掉） | `.properties` 里用了单反斜杠 | 改成正斜杠 `D:/keys/x.jks` 或双反斜杠 `D:\\keys\\x.jks`（§4） |
| `Keystore was tampered with, or password was incorrect` | 口令或 **alias** 打错；或 `-storetype` 不对 | 用 `keytool -list -v -keystore <文件> -storetype PKCS12` 核对别名与指纹（会问口令） |
| `keytool` 提示"输入密钥库口令"时敲错 | 口令里有特殊字符被 shell 吃掉 | 用交互输入（别用 `-storepass` 写在命令行里，它会进历史记录） |
| 出包报 `SigningConfig "release" not found` | `keystore.properties` 里的键名写错（必须是 `storeFile`/`storePassword`/`keyAlias`/`keyPassword`） | 逐字对照 §4 |
| 改了 `keystore.properties` 但 Gradle 还用旧值 | **配置缓存**（configuration cache）记住了上一次的输入 | 看到 `configuration cache cannot be reused because ... keystore.properties` 属正常；顽固时 `.\gradlew.bat --stop` 后重跑，或加 `--no-configuration-cache` |
| 装机报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 同包名、不同签名（§7 坑 1） | 改 `applicationIdSuffix` 并存，或先导出再卸载参照应用 |
| 装机报 `INSTALL_FAILED_VERSION_DOWNGRADE` | `versionCode` 比已装的低（§7 坑 2） | 把 `versionCode` 调高 |
| 不小心把 `.jks` 提交了 | — | 立刻**换一把新密钥**（历史里的文件删不干净），并按 §8 处理数据迁移 |
| 忘记口令 | — | 无法找回；只能换密钥 + 换 `applicationId`（§8） |

---

## 11. 本次在这台机器上的实测记录（2026-10-10）

为了让上面的步骤**不是抄来的**，我在本机用一把**一次性**密钥把整条链跑通了
（密钥与口令都是临时的，放在 `%TEMP%` 下，验证完已删除；**没有**在仓库里留任何密钥文件）：

| 步骤 | 命令 | 结果 |
|---|---|---|
| 生成 | `keytool -genkeypair -keystore %TEMP%\wheremy-keytest\wheremy-test.jks -storetype PKCS12 -alias wheremy-test -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Wheremy Test,..."` | 生成了 4,420 字节的 `.jks` |
| 配置 | 写 `D:\src\dsh\keystore.properties`（**先用了反斜杠，路径被吃**；改正斜杠后正常） | 这条坑就是这么发现的 |
| 核对 | `./gradlew.bat :app:signingReport` | `Variant: release` → `Config: release`，`Store` 指向 TEMP 那把，`Alias: wheremy-test` |
| 出包 | `./gradlew.bat :app:assembleRelease` | `BUILD SUCCESSFUL`（R8 + 资源收缩） |
| 验签 | `apksigner verify --print-certs app\build\outputs\apk\release\app-release.apk` | `Signer #1 certificate DN: CN=Wheremy Test, OU=Dev, O=Wheremy, L=Beijing, ST=Beijing, C=CN`；`SHA-256: c54d7af0…` |
| 复跑 | 代码经 Spotless 格式化之后，用**同一套命令**又跑了一遍（换了一把一次性密钥） | `assembleRelease` 出的包验签为 `CN=Wheremy Test…`、`SHA-256: 41cc6474…`——说明格式化没动到签名逻辑 |
| 回落 | 删掉 `keystore.properties` 再 `assembleRelease` | 打印 `⚠️ release 用的是**调试签名**：没找到 keystore.properties。…`，`BUILD SUCCESSFUL` |
| 清理 | 删除 TEMP 里的 `.jks` 与 `D:\src\dsh\keystore.properties` | 仓库 `git status` 干净 |

---

## 13. 用 CI 出签名包（`FR-RLS-02` 的一半：对外分发用）

**能不能用 CI 生成签名包**：能。`.github/workflows/release.yml` 已经写好，**但默认关着**——
它只在「手动 dispatch」或「推 `v*` 标签」时跑，日常门禁还是 `ci.yml`，互不干扰。

**为什么要四个 secret 才能跑**：Gradle 找不到 `keystore.properties` 时会**退回调试签名**
（`app/build.gradle.kts` 的注释写着），那样产出的"发布包"是**假货**——装到别人手机上，
将来用真 keystore 的升级包**覆盖不了**（`KEYSTORE.md` §8：换密钥＝换应用）。
所以那个工作流**缺 secret 就直接红**，宁可不出包，也不出假包。

### 13.1 要配的四个 secret（仓库 → Settings → Secrets and variables → Actions）

| Secret 名 | 值从哪来 |
|---|---|
| `KEYSTORE_BASE64` | 把 `D:\keys\wheremy-release.jks` 转成一行 base64（命令见下） |
| `KEYSTORE_STORE_PASSWORD` | `keystore.properties` 里的 `storePassword` |
| `KEYSTORE_KEY_PASSWORD` | 同上的 `keyPassword`（PKCS12 里与 store 口令相同） |
| （别名不设 secret） | 工作流里写死 `wheremy-release`，与 `KEYSTORE.md` §4 一致 |

Windows 上转 base64（**输出是一行**，别带换行）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('D:\keys\wheremy-release.jks')) | Set-Clipboard
# 然后粘进 GitHub 的 secret 输入框
```

### 13.2 跑一次

```
Actions → Release APK → Run workflow（tag 留空 = 只出包不发 Release）
# 或者：git tag v0.1.0 && git push origin v0.1.0   ← 顺手发一个 Release
```

工作流会做五件事：还原 keystore（写在**仓库外**，与本地同一套查找路径）→ `assembleRelease` →
**验签并断言证书指纹**（对不上就红：`8118b97d…` 写在 `EXPECTED_CERT` 里，指纹是公开信息）→
算 SHA-256 写 `SHA256SUMS.txt` → 上传产物（打标签时再 `gh release create`）。

**第一次跑会是红的**——那是在告诉你"secret 还没配"。这不是故障，是保险丝（见上）。

### 13.3 安全上的取舍（看明白再决定）

- 配了 secret，**keystore 就不再只在你机器上**：它以加密形式存在 GitHub 的 secret 里，
  仓库管理员可见、workflow 里能取到。仓库现在是**私有**的，风险可控，但这条要说清。
- 另一条路是**CI 只出未签名包、你本地签名**：更安全，但每次发版都要你在场（不符合"自动推进"）。
- 两条路都**必须用同一张 keystore**：换密钥＝换应用（§8）。
- 我（助手）**永远不会**读、写、打印口令与 keystore 内容；上面那条 base64 命令由你自己跑。

## 13. 用 CI 出签名包（`FR-RLS-02` 的一半：对外分发用）

**能不能用 CI 生成签名包**：能。`.github/workflows/release.yml` 已经写好，**但默认关着**——
它只在「手动 dispatch」或「推 `v*` 标签」时跑，日常门禁还是 `ci.yml`，互不干扰。

**为什么要四个 secret 才能跑**：Gradle 找不到 `keystore.properties` 时会**退回调试签名**
（`app/build.gradle.kts` 的注释写着），那样产出的"发布包"是**假货**——装到别人手机上，
将来用真 keystore 的升级包**覆盖不了**（`KEYSTORE.md` §8：换密钥＝换应用）。
所以那个工作流**缺 secret 就直接红**，宁可不出包，也不出假包。

### 13.1 要配的四个 secret（仓库 → Settings → Secrets and variables → Actions）

| Secret 名 | 值从哪来 |
|---|---|
| `KEYSTORE_BASE64` | 把 `D:\keys\wheremy-release.jks` 转成一行 base64（命令见下） |
| `KEYSTORE_STORE_PASSWORD` | `keystore.properties` 里的 `storePassword` |
| `KEYSTORE_KEY_PASSWORD` | 同上的 `keyPassword`（PKCS12 里与 store 口令相同） |
| （别名不设 secret） | 工作流里写死 `wheremy-release`，与 `KEYSTORE.md` §4 一致 |

Windows 上转 base64（**输出是一行**，别带换行）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('D:\keys\wheremy-release.jks')) | Set-Clipboard
# 然后粘进 GitHub 的 secret 输入框
```

### 13.2 跑一次

```
Actions → Release APK → Run workflow（tag 留空 = 只出包不发 Release）
# 或者：git tag v0.1.0 && git push origin v0.1.0   ← 顺手发一个 Release
```

工作流会做五件事：还原 keystore（写在**仓库外**，与本地同一套查找路径）→ `assembleRelease` →
**验签并断言证书指纹**（对不上就红：`8118b97d…` 写在 `EXPECTED_CERT` 里，指纹是公开信息）→
算 SHA-256 写 `SHA256SUMS.txt` → 上传产物（打标签时再 `gh release create`）。

**第一次跑会是红的**——那是在告诉你"secret 还没配"。这不是故障，是保险丝（见上）。

### 13.3 安全上的取舍（看明白再决定）

- 配了 secret，**keystore 就不再只在你机器上**：它以加密形式存在 GitHub 的 secret 里，
  仓库管理员可见、workflow 里能取到。仓库现在是**私有**的，风险可控，但这条要说清。
- 另一条路是**CI 只出未签名包、你本地签名**：更安全，但每次发版都要你在场（不符合"自动推进"）。
- 两条路都**必须用同一张 keystore**：换密钥＝换应用（§8）。
- 我（助手）**永远不会**读、写、打印口令与 keystore 内容；上面那条 base64 命令由你自己跑。

## 12. 我要从你这拿到的三样（口令**不要**发我）

等你要正式出包时，告诉我这三样就够了：

1. **`.jks` 的路径**（例如 `D:\keys\wheremy-release.jks`）；
2. **别名**（例如 `wheremy-release`）；
3. **口令放在哪**（例如"在 1Password 里，条目名 X"）——**别把口令本身发给我**。

我这边只会做两件事：把 `keystore.properties` 指过去（或确认你自己已经写好），
然后跑 §6 的三条验证命令并把输出记进发布记录。**我不会读、不会存、不会打印你的口令。**
