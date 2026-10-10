# Dump-then-tap discipline: find a node by its exact text (or content-desc), tap its bounds centre, then re-dump to confirm.
param(
  [Parameter(Mandatory=$true)][string]$Text,
  [ValidateSet('text','desc')][string]$By = 'text',
  [string]$Serial = '9aa4b43e',
  [int]$WaitMs = 1500
)

$adb = 'D:\Lib\Android\Sdk\platform-tools\adb.exe'

function Get-Xml {
  for ($r = 0; $r -lt 4; $r++) {
    & $adb -s $Serial shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
    $x = (& $adb -s $Serial exec-out cat /sdcard/ui.xml 2>&1 | Out-String)
    if ($x -match '<hierarchy') { return $x }
    Start-Sleep -Milliseconds 500
  }
  return $x
}

$xml = Get-Xml
$attr = if ($By -eq 'text') { 'text' } else { 'content-desc' }
$pattern = '<node[^>]*' + $attr + '="' + [regex]::Escape($Text) + '"[^>]*>'
$node = [regex]::Matches($xml, '<node[^>]*>') | Where-Object { $_.Value -match $pattern } | Select-Object -First 1

if (-not $node) {
  Write-Output "TAP-FAIL: no node with $attr='$Text'"
  Write-Output "visible texts: $(([regex]::Matches($xml,'text=""([^""]+)""') | ForEach-Object { $_.Groups[1].Value }) -join ' | ')"
  exit 1
}

$m = [regex]::Match($node.Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
$x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
$y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
Write-Output "TAP: $attr='$Text' bounds=$($m.Groups[0].Value -replace 'bounds=','') -> tap ($x,$y)"
& $adb -s $Serial shell input tap $x $y | Out-Null
Start-Sleep -Milliseconds $WaitMs

$xml2 = Get-Xml
$texts2 = [regex]::Matches($xml2, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '' } | Select-Object -Unique
$focus = (& $adb -s $Serial shell dumpsys window 2>&1 | Select-String -Pattern 'mCurrentFocus' | Select-Object -First 1)
Write-Output "AFTER-TAP focus: $($focus -replace '\s+',' ')"
Write-Output "AFTER-TAP texts: $($texts2 -join ' | ')"
