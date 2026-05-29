$jar = 'C:\Users\PC\AppData\Roaming\PrismLauncher\instances\Моя\minecraft\mods\sodium-fabric-0.6.13+mc1.21.4.jar'
$tmp = 'D:\projects\Phaze Client\.sodium-tmp'
if (-not (Test-Path $tmp)) { New-Item -ItemType Directory $tmp | Out-Null }
Copy-Item $jar (Join-Path $tmp 'sodium.zip') -Force
Expand-Archive -Path (Join-Path $tmp 'sodium.zip') -DestinationPath $tmp -Force
Get-ChildItem $tmp -Recurse -Filter '*.json' | Where-Object { $_.Name -like '*mixin*' -or $_.Name -like '*sodium*' } | Select-Object -ExpandProperty FullName
