@echo off
set "JAVA_HOME=C:\Users\PC\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.1.8-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "D:\projects\Phaze Client"
java -version
call gradlew.bat build
