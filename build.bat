@echo off

call "%~dp0\gradlew" assembleRelease --no-daemon

call "%~dp0\jar\genJar.bat" %1

copy "%~dp0\jar\custom_spider.jar" "%~dp0\jat\lefty.jar"

copy "%~dp0\jar\lefty.jar" "D:\filestation\config\tvbox\jar\"

pause