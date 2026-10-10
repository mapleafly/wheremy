# 把 KEYSTORE.md §11 的实测记录改成"最终代码下再跑一遍"的数字。
import io

PATH = r"D:\src\dsh\wheremy\docs\KEYSTORE.md"
t = io.open(PATH, encoding="utf-8").read()

old = "| 验签 | `apksigner verify --print-certs app\\build\\outputs\\apk\\release\\app-release.apk` | `Signer #1 certificate DN: CN=Wheremy Test, OU=Dev, O=Wheremy, L=Beijing, ST=Beijing, C=CN`；`SHA-256: c54d7af0…` |"
new = ("| 验签 | `apksigner verify --print-certs app\\build\\outputs\\apk\\release\\app-release.apk` | "
       "`Signer #1 certificate DN: CN=Wheremy Test, OU=Dev, O=Wheremy, L=Beijing, ST=Beijing, C=CN`；"
       "`SHA-256: c54d7af0…` |\n"
       "| 复跑 | 代码经 Spotless 格式化之后，用**同一套命令**又跑了一遍（换了一把一次性密钥） | "
       "`assembleRelease` 出的包验签为 `CN=Wheremy Test…`、`SHA-256: 41cc6474…`——说明格式化没动到签名逻辑 |")

assert old in t, "§11 的验签那一行没匹配"
t = t.replace(old, new, 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("KEYSTORE.md §11 已更新")
