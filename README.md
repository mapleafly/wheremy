# wheremy

**「那个东西放哪了？」——单机、本地优先的物品位置记录本。**

给家里那些「明明收好了却找不到」的东西记一条：叫什么、放在哪、长什么样、什么时候到期。
Android 应用，**没有账号、没有云、没有服务端**：数据只在这台手机的本地数据库与照片目录里，
唯一的迁移手段是**手动导出一个 zip 包**（可以加密，口令由你自己设）。

## 现在的状态

**0.1.0，已验收**（人工清单 7/7、TalkBack 焦点次序走查、迁移演练四条、加密演练四条；证据见
[`docs/RELEASE.md`](docs/RELEASE.md) §10）。日常门禁与发布流程都是绿的：

- 本地等价 CI：`pwsh -File artifacts/tools/ci-local.ps1`（四道门：静态检查 / 单元测试 / 截图回归 / 发布构建）
- 真 CI：[`.github/workflows/ci.yml`](.github/workflows/ci.yml)，测试合计 **47 份 XML / 300 例**

## 它能做什么

- **拍照 → 记位置**：拍一张、选个位置（家 → 卧室 → 衣柜），完事。物品可以有多张照片。
- **找得到**：按名称、类别、季节、颜色、材质、品牌、备注搜；「待归类」把所有还没放好的集中列出来。
- **到期提醒**：药品、食品这类可以设到期日，到点提醒。
- **AI 预填（可选）**：填自己的模型地址与 key，拍完照让它先把名称、类别、季节填一遍，你再改。
  不填也能用——它只是省打字。
- **备份**：导出成一个 zip（**可以加口令加密**），换手机时导进去；支持合并导入与覆盖导入。

## 下载安装

**发布包在这里：[Releases](https://github.com/mapleafly/wheremy/releases)**（APK + `SHA256SUMS.txt`）。
装之前可以自己核一遍：`sha256sum -c SHA256SUMS.txt`（Windows：`Get-FileHash .\wheremy-0.1.0-release.apk -Algorithm SHA256`）。

包是用发布者自己的 keystore 签的（证书 SHA-256 `8118b97d588e43c91a77540154fda366749e0111f1bafd2765ee7a47676b8369`）——
这意味着**它没法覆盖安装别人签名的同名应用**，也意味着**别人签的包盖不了它**。

## 怎么构建

需要 JDK 21 与 Android SDK（`compileSdk 37` / `targetSdk 36` / `minSdk 26`）：

```bash
./gradlew :app:assembleDebug          # 出调试包
./gradlew :app:testDebugUnitTest      # JVM + Robolectric 单元测试
./gradlew :app:verifyRoborazziDebug   # 截图回归
```

**发布包必须用你自己的 keystore 签**：`app/build.gradle.kts` 从**仓库外**读
`../keystore.properties`（或 `~/.wheremy/keystore.properties`）；找不到就退回调试签名。
步骤见 [`docs/KEYSTORE.md`](docs/KEYSTORE.md)。CI 出签名包的配置见同文 §13
（`.github/workflows/release.yml`，缺 secret 会**直接失败**，绝不产出"调试签名的发布包"）。

## 文档

这个仓库的文档是**当代码写的**——需求、规则、判据、验收证据都在里面，改代码前先看文档：

| 文件 | 讲什么 |
|---|---|
| [`docs/README.md`](docs/README.md) | **总索引**：先看这个 |
| [`docs/PRD.md`](docs/PRD.md) | 需求与验收标准（每条 FR 的状态与测试） |
| [`docs/RULES.md`](docs/RULES.md) | 行为规则、逐字文案、安全边界（🔒 不许改的那部分） |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | as-built 技术架构 |
| [`docs/PROCESS.md`](docs/PROCESS.md) | 开发流程与门禁 G0–G5（怎么算"绿"） |
| [`docs/RELEASE.md`](docs/RELEASE.md) | 发布清单与全部验收证据 |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | 每个取舍为什么这么定（ADR） |
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | 每轮改了什么、为什么 |

## 许可

**源码公开，但保留所有权利**（没有 LICENSE 文件＝默认状态）：你可以阅读、克隆、自己构建来用，
但**不得复制再发布**（包括改名后重新分发 APK）。想要别的授权方式，开个 issue 谈。

## 隐私

- 应用被显式设置为**不做云备份**（`allowBackup="false"`）——数据不会跟着系统备份飘走。
- 权限只有三个：`INTERNET`（调用你自己填的 AI 接口）、`POST_NOTIFICATIONS`（到期提醒）、`CAMERA`（拍照）。
  **没有存储权限**。
- 照片、物品、位置、备注**全部只在本机**。备份包默认是明文 zip（里面有照片与清单），
  想放进网盘就先在设置里给备份设个口令。
