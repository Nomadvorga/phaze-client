@echo off
"C:\Users\PC\AppData\Roaming\PrismLauncher\java\java-runtime-delta\bin\jar.exe" tf build\libs\PhazeClient-3.0.0-beta.jar | findstr /I sky > .scripts\jar-sky.txt
echo done
