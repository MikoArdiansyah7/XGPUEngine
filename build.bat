@echo off
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot
set ANDROID_HOME=C:\Users\E15\AppData\Local\Android\Sdk
cd /d C:\XGPUEngine
echo Building APK...
gradlew.bat assembleDebug
echo.
echo APK ada di: app\build\outputs\apk\debug\app-debug.apk
pause
