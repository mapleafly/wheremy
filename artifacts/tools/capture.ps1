# Capture one screen: UI dump (proof of which screen) + PNG screenshot.
# Uses `screencap` to /sdcard then `pull` so binary data never round-trips through PowerShell text redirection.
param(
  [Parameter(Mandatory=$true)][string]$Name,
  [string]$Serial = '9aa4b43e'
)

$adb = 'D:\Lib\Android\Sdk\platform-tools\adb.exe'
$ref = 'D:\src\dsh\wheremy\artifacts\screenshots\REF'
New-Item -ItemType Directory -Path $ref -Force | Out-Null

# --- UI dump: retry until we actually get XML ---
$xml = ''
for ($r = 0; $r -lt 4; $r++) {
  & $adb -s $Serial shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
  $xml = (& $adb -s $Serial exec-out cat /sdcard/ui.xml 2>&1 | Out-String)
  if ($xml -match '<hierarchy') { break }
  Start-Sleep -Milliseconds 500
}
$xmlPath = Join-Path $ref "$Name.xml"
$xml | Set-Content -Path $xmlPath -Encoding UTF8

$texts = [regex]::Matches($xml, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '' } | Select-Object -Unique
$descs = [regex]::Matches($xml, 'content-desc="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '' } | Select-Object -Unique

# --- screenshot ---
$remote = "/sdcard/cap_$Name.png"
& $adb -s $Serial shell screencap -p $remote 2>&1 | Out-Null
$png = Join-Path $ref "$Name.png"
& $adb -s $Serial pull $remote $png 2>&1 | Out-Null
& $adb -s $Serial shell rm -f $remote 2>&1 | Out-Null

$len = 0; $w = 0; $h = 0
if (Test-Path $png) {
  $len = (Get-Item $png).Length
  $fs = [System.IO.File]::OpenRead($png)
  $buf = New-Object byte[] 26
  $fs.Read($buf, 0, 26) | Out-Null
  $fs.Close()
  $w = [int]$buf[16] * 16777216 + [int]$buf[17] * 65536 + [int]$buf[18] * 256 + [int]$buf[19]
  $h = [int]$buf[20] * 16777216 + [int]$buf[21] * 65536 + [int]$buf[22] * 256 + [int]$buf[23]
}
$orient = if ($h -gt $w) { 'PORTRAIT' } elseif ($w -gt $h) { 'LANDSCAPE' } else { 'SQUARE' }

$focus = (& $adb -s $Serial shell dumpsys window 2>&1 | Select-String -Pattern 'mCurrentFocus' | Select-Object -First 1)
$rot = (& $adb -s $Serial shell dumpsys display 2>&1 | Select-String -Pattern 'rotation' | Select-Object -First 1)

Write-Output "CAPTURE: $Name"
Write-Output "  png    : $png ($len bytes, ${w}x${h}, $orient)"
Write-Output "  xml    : $xmlPath"
Write-Output "  focus  : $($focus -replace '\s+',' ')"
Write-Output "  rot    : $($rot -replace '\s+',' ')"
Write-Output "  texts  : $($texts -join ' | ')"
if ($descs) { Write-Output "  descs  : $($descs -join ' | ')" }
