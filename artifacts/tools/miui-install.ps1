# MIUI USB-install dialog workaround.
# adb install is blocked by the "USB安装提示 ... 是否继续?" dialog whose 拒绝 button has a 3s countdown.
# Strategy: start `adb install` in the background -> dump UI -> tap the 继续安装 button by its bounds.
param(
  [string]$Serial   = '9aa4b43e',
  [string]$Apk      = 'D:\src\dsh\wheremy\.reference\wheremy\app\build\outputs\apk\debug\app-debug.apk',
  [string]$OutDir   = 'D:\src\dsh\wheremy\artifacts'
)

$adb = 'D:\Lib\Android\Sdk\platform-tools\adb.exe'
$stdout = Join-Path $OutDir 'install-stdout.txt'
$stderr = Join-Path $OutDir 'install-stderr.txt'
Remove-Item $stdout,$stderr -ErrorAction SilentlyContinue

function Dump-Ui {
  param([int]$Retries = 3)
  for ($r = 0; $r -lt $Retries; $r++) {
    & $adb -s $Serial shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
    $xml = (& $adb -s $Serial exec-out cat /sdcard/ui.xml 2>&1 | Out-String)
    if ($xml -match '<hierarchy') { return $xml }
    Start-Sleep -Milliseconds 400
  }
  return $xml
}

function Get-VisibleTexts {
  param([string]$Xml)
  $texts = [regex]::Matches($Xml, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '' } | Select-Object -Unique
  $descs = [regex]::Matches($Xml, 'content-desc="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '' } | Select-Object -Unique
  return @{ texts = $texts; descs = $descs }
}

function Tap-ByText {
  param([string]$Xml, [string]$Needle)
  $node = [regex]::Matches($Xml, '<node[^>]*>') |
          Where-Object { $_.Value -match ('text="' + [regex]::Escape($Needle) + '"') } |
          Select-Object -First 1
  if (-not $node) { return $null }
  $m = [regex]::Match($node.Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
  if (-not $m.Success) { return $null }
  $x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
  $y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
  & $adb -s $Serial shell input tap $x $y | Out-Null
  return "tapped '$Needle' at ($x,$y) bounds=[$($m.Groups[1].Value),$($m.Groups[2].Value)][$($m.Groups[3].Value),$($m.Groups[4].Value)]"
}

# --- wake + unlock so the dialog is actually interactive ---
& $adb -s $Serial shell input keyevent KEYCODE_WAKEUP | Out-Null
& $adb -s $Serial shell wm dismiss-keyguard | Out-Null
Start-Sleep -Milliseconds 600

Write-Output "=== launching install (background) ==="
$proc = Start-Process -FilePath $adb `
  -ArgumentList @('-s', $Serial, 'install', '-r', $Apk) `
  -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -NoNewWindow
Write-Output "adb install pid=$($proc.Id)"

$taps = @()
$sawDialog = $false
for ($i = 0; $i -lt 40; $i++) {
  Start-Sleep -Milliseconds 500
  if ($proc.HasExited) { Write-Output "install process exited at iteration $i"; break }

  if ($i -lt 3) { continue }   # give the dialog ~1.5s to appear
  $xml = Dump-Ui
  if ($xml -notmatch '<hierarchy') { continue }

  if ($xml -match '继续安装') {
    if (-not $sawDialog) {
      $sawDialog = $true
      Write-Output "=== MIUI install dialog detected (iteration $i) ==="
      $vis = Get-VisibleTexts -Xml $xml
      Write-Output ("dialog texts: " + ($vis.texts -join ' | '))
      $xml | Set-Content -Path (Join-Path $OutDir 'miui-install-dialog.xml') -Encoding UTF8
    }
    $r = Tap-ByText -Xml $xml -Needle '继续安装'
    if ($r) { Write-Output $r; $taps += $r; Start-Sleep -Milliseconds 800 }
  }
}

if (-not $proc.HasExited) {
  Write-Output "waiting for install to settle..."
  $proc | Wait-Process -Timeout 90 -ErrorAction SilentlyContinue
}
if (-not $proc.HasExited) { Write-Output "STILL RUNNING after wait" } else { Write-Output "install exit code: $($proc.ExitCode)" }

Write-Output "=== install stdout ==="
if (Test-Path $stdout) { Get-Content $stdout }
Write-Output "=== install stderr ==="
if (Test-Path $stderr) { Get-Content $stderr }
Write-Output "=== taps performed ==="
$taps | ForEach-Object { Write-Output $_ }
