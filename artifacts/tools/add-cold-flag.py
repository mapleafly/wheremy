# 给 ci-local.ps1 加 -Cold：禁构建缓存 + 强制重跑，最接近"CI 干净 runner"。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\artifacts\tools\ci-local.ps1"
t = io.open(PATH, encoding="utf-8").read()

t = t.replace(
    """#   pwsh -File artifacts/tools/ci-local.ps1            # 带缓存跑（快，日常用）
#   pwsh -File artifacts/tools/ci-local.ps1 -Clean     # 先 clean（模拟 CI runner，慢但可信）""",
    """#   pwsh -File artifacts/tools/ci-local.ps1            # 带缓存跑（快，日常用）
#   pwsh -File artifacts/tools/ci-local.ps1 -Clean     # 先 clean
#   pwsh -File artifacts/tools/ci-local.ps1 -Cold      # clean + 禁构建缓存 + 强制重跑（最接近 CI runner，最慢）""",
    1,
)

t = t.replace(
    """param(
    [switch]$Clean,
    [switch]$SkipRelease
)""",
    """param(
    [switch]$Clean,
    [switch]$Cold,
    [switch]$SkipRelease
)

# -Cold 隐含 clean；另外禁掉 Gradle 构建缓存并强制所有任务重跑
$flags = @()
if ($Cold) {
    $Clean = $true
    $flags += '--no-build-cache'
    $flags += '--rerun-tasks'
}""",
    1,
)

t = t.replace(
    "    $out = & .\\gradlew.bat @($s.args) --console=plain 2>&1 | Out-String",
    "    $out = & .\\gradlew.bat @($s.args) @flags --console=plain 2>&1 | Out-String",
    1,
)

t = t.replace(
    'Write-Output "== 本地 CI（$(if ($Clean) { \'clean 起\' } else { \'带缓存\' }))=="',
    'Write-Output "== 本地 CI（$(if ($Cold) { \'cold：clean + 禁缓存 + 强制重跑\' } elseif ($Clean) { \'clean 起\' } else { \'带缓存\' }))=="',
    1,
)

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("ci-local.ps1 已加 -Cold")
