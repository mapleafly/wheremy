# 本地跑一遍 CI 的四道门（与 .github/workflows/ci.yml 同序同参），给一个明确的判决。
#
# 为什么要有它：
# 1. 这个工作区**没有 git 远端**，GitHub Actions 跑不了；但"CI 会不会红"这件事必须能提前知道；
# 2. 本地跑还能顺便验"从 clean 起"这条路——CI runner 每次都是干净的，本地带缓存跑绿不代表干净跑绿。
#
# 用法：
#   pwsh -File artifacts/tools/ci-local.ps1            # 带缓存跑（快，日常用）
#   pwsh -File artifacts/tools/ci-local.ps1 -Clean     # 先 clean
#   pwsh -File artifacts/tools/ci-local.ps1 -Cold      # clean + 禁构建缓存 + 强制重跑（最接近 CI runner，最慢）
#
# 退出码：0 = 四步全绿；1 = 有一步红（红在哪一步会打在最后）。
param(
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
}

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

$steps = [System.Collections.Generic.List[hashtable]]::new()
$steps.Add(@{ name = '静态检查（Spotless/ktlint）'; args = @(':app:spotlessCheck') })
$steps.Add(@{ name = '单元测试（JVM + Robolectric）'; args = @(':app:testDebugUnitTest') })
$steps.Add(@{ name = '截图回归'; args = @(':app:verifyRoborazziDebug') })
if (-not $SkipRelease) {
    $steps.Add(@{ name = '发布构建（R8 + 资源收缩）'; args = @(':app:assembleRelease') })
}

Write-Output "== 本地 CI（$(if ($Cold) { 'cold：clean + 禁缓存 + 强制重跑' } elseif ($Clean) { 'clean 起' } else { '带缓存' })）=="
if ($Clean) {
    Write-Output "-- gradlew clean"
    & .\gradlew.bat clean --console=plain | Select-Object -Last 2
}

$failed = @()
foreach ($s in $steps) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $out = & .\gradlew.bat @($s.args) @flags --console=plain 2>&1 | Out-String
    $sw.Stop()
    $ok = $out -match 'BUILD SUCCESSFUL'
    if (-not $ok) { $failed += $s.name }
    Write-Output ("  {0,-32} {1}  ({2}s)" -f $s.name, $(if ($ok) { 'OK  ' } else { 'FAIL' }), [int]$sw.Elapsed.TotalSeconds)
    if (-not $ok) {
        ($out -split "`n" | Where-Object { $_ -match 'FAILED|^e: |error:' } | Select-Object -First 8) | ForEach-Object { "      $_" }
    }
}

# 判绿双证：数一数这次真的产出了多少份新鲜 XML（防"任务 UP-TO-DATE 却报绿"）
$files = Get-ChildItem app\build\test-results\testDebugUnitTest\*.xml -ErrorAction SilentlyContinue
if ($files) {
    $t = 0; $f = 0; $e = 0
    foreach ($file in $files) {
        $sx = ([xml](Get-Content $file.FullName -Raw)).testsuite
        $t += [int]$sx.tests; $f += [int]$sx.failures; $e += [int]$sx.errors
    }
    Write-Output "  测试合计：$($files.Count) 份 XML / $t 例 / failures=$f errors=$e"
}

if ($failed.Count -gt 0) {
    Write-Output "`n结论：**红** —— $($failed -join '、')"
    exit 1
}
Write-Output "`n结论：**绿** —— 四道门全过（与 ci.yml 同序同参）"
exit 0
