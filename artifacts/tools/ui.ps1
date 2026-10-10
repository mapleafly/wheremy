# 真机界面自动化的两个稳健工具函数（dot-source 进其它脚本用）。
#
# 为什么要有它：
# 1. `uiautomator dump` 失败时会**保留上一次的文件**，直接 cat 就会读到旧界面（踩过一次，误判"点击没生效"）；
#    所以先删文件、再 dump、再读，并核对文件是刚写的。
# 2. 真机输入法是微信输入法 + 小鹤双拼，`input text` 打不出英文（字母被转成汉字）——
#    要目录/文件先 `adb shell` 建好，再在界面里点选；本文件只提供"点/滑/看"，不提供打字。

$AdbExe = if ($env:WHERE_ADB) { $env:WHERE_ADB } else { "D:\Lib\Android\Sdk\platform-tools\adb.exe" }
$Serial = if ($env:WHERE_SERIAL) { $env:WHERE_SERIAL } else { "9aa4b43e" }
$DumpPath = "/sdcard/_ui.xml"
$LocalDump = Join-Path $env:TEMP "_wheremy_ui.xml"

function Get-Screen {
    <# 取当前界面 XML；失败/读到旧文件就重试，最多 4 次 #>
    param([int]$Retries = 4)
    for ($i = 1; $i -le $Retries; $i++) {
        & $AdbExe -s $Serial shell "rm -f $DumpPath" 2>&1 | Out-Null
        $dumpOut = & $AdbExe -s $Serial shell "uiautomator dump $DumpPath" 2>&1 | Out-String
        if ($dumpOut -match "dumped to") {
            Remove-Item $LocalDump -ErrorAction SilentlyContinue
            & $AdbExe -s $Serial pull $DumpPath $LocalDump 2>&1 | Out-Null
            if ((Test-Path $LocalDump) -and (Get-Item $LocalDump).Length -gt 0) {
                return (Get-Content $LocalDump -Raw -Encoding UTF8)
            }
        }
        Start-Sleep -Milliseconds 600
    }
    throw "取屏失败：$dumpOut"
}

function Get-NodeBounds {
    <# 按精确文本找节点，返回 @{x1;y1;x2;y2}；找不到返回 $null #>
    param([string]$Text, [string]$Screen = $null)
    if (-not $Screen) { $Screen = Get-Screen }
    $m = [regex]::Match($Screen, '<node[^>]*text="' + [regex]::Escape($Text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m.Success) { return $null }
    return @{
        x1 = [int]$m.Groups[1].Value; y1 = [int]$m.Groups[2].Value
        x2 = [int]$m.Groups[3].Value; y2 = [int]$m.Groups[4].Value
    }
}

function Tap-Text {
    <# 按精确文本点一下；返回是否点到 #>
    param([string]$Text, [switch]$Quiet)
    $b = Get-NodeBounds -Text $Text
    if (-not $b) { if (-not $Quiet) { Write-Output "找不到「$Text」" }; return $false }
    $cx = [int](($b.x1 + $b.x2) / 2); $cy = [int](($b.y1 + $b.y2) / 2)
    & $AdbExe -s $Serial shell input tap $cx $cy | Out-Null
    if (-not $Quiet) { Write-Output "点了「$Text」($cx,$cy)" }
    return $true
}

function Get-Texts {
    <# 当前界面上所有非空文本 #>
    param([string]$Screen = $null, [string]$Filter = $null)
    if (-not $Screen) { $Screen = Get-Screen }
    $all = [regex]::Matches($Screen, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_.Trim().Length -gt 0 }
    if ($Filter) { return $all | Where-Object { $_ -match $Filter } }
    return $all
}

function Save-Screenshot {
    <# 截屏存到工作区（默认 artifacts/screenshots） #>
    param([string]$Path)
    & $AdbExe -s $Serial shell screencap -p /sdcard/_shot.png | Out-Null
    & $AdbExe -s $Serial pull /sdcard/_shot.png $Path 2>&1 | Select-Object -Last 1
}
