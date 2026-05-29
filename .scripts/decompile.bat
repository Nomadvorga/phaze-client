@echo off
"C:\Users\PC\AppData\Roaming\PrismLauncher\java\java-runtime-delta\bin\javap.exe" -p -c -v ".sodium-tmp\net\caffeinemc\mods\sodium\mixin\features\render\world\sky\ClientLevelMixin.class" > ".scripts\sodium-clientlevel.txt" 2>&1
"C:\Users\PC\AppData\Roaming\PrismLauncher\java\java-runtime-delta\bin\javap.exe" -p -c -v ".sodium-tmp\net\caffeinemc\mods\sodium\mixin\features\render\world\sky\LevelRendererMixin.class" > ".scripts\sodium-levelrenderer.txt" 2>&1
"C:\Users\PC\AppData\Roaming\PrismLauncher\java\java-runtime-delta\bin\javap.exe" -p -c -v ".sodium-tmp\net\caffeinemc\mods\sodium\mixin\features\render\world\sky\FogRendererMixin.class" > ".scripts\sodium-fogrenderer.txt" 2>&1
echo done
