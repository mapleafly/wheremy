# 量冷启动：对每个包 force-stop 后 `am start -W`，取 N 次的中位数。
#
# 为什么要脚本而不是手敲：这个数字会写进文档当基线，手敲容易"挑一次好看的"。
# 固定做法 = 每次先 force-stop（保证是冷启动）、连续 N 次、报全部值 + 中位数，
# 谁都能复跑出同一量级。
#
# 用法：pwsh -File artifacts/tools/measure-startup.ps1 [-Runs 5] [-Package com.wheremy.app.dsh]
param(
    [int]$Runs = 5,
    [string[]]$Packages = @("com.wheremy.app.dsh", "com.wheremy.app"),
    [string]$Serial = "9aa4b43e",
    [string]$Adb = "D:\Lib\Android\Sdk\platform-tools\adb.exe",
    [string]$Activity = "com.wheremy.app.MainActivity"
)

function Get-Median([int[]]$Values) {
    $sorted = $Values | Sort-Object
    $n = $sorted.Count
    if ($n -eq 0) { return 0 }
    if ($n % 2 -eq 1) { return $sorted[[int](($n - 1) / 2)] }
    return [int](($sorted[$n / 2 - 1] + $sorted[$n / 2]) / 2)
}

foreach ($pkg in $Packages) {
    Write-Output "== $pkg（$Runs 次，每次先 force-stop）=="
    $times = @()
    for ($i = 1; $i -le $Runs; $i++) {
        & $Adb -s $Serial shell am force-stop $pkg | Out-Null
        Start-Sleep -Milliseconds 800
        $raw = & $Adb -s $Serial shell am start -W -n "$pkg/$Activity" 2>&1 | Out-String
        $m = [regex]::Match($raw, 'TotalTime:\s*(\d+)')
        if ($m.Success) {
            $t = [int]$m.Groups[1].Value
            $times += $t
            Write-Output ("  第 {0} 次：TotalTime {1} ms" -f $i, $t)
        } else {
            Write-Output ("  第 {0} 次：没解析到 TotalTime —— {1}" -f $i, ($raw -replace "`n", ' ').Trim())
        }
        Start-Sleep -Milliseconds 600
    }
    if ($times.Count -gt 0) {
        $sorted = $times | Sort-Object
        Write-Output ("  全部：{0}" -f ($times -join ' / '))
        Write-Output ("  中位数：{0} ms   最小 {1} ms   最大 {2} ms" -f (Get-Median $times), $sorted[0], $sorted[-1])
    }
    Write-Output ""
}
