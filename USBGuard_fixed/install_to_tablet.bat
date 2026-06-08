@echo off
cd /d %~dp0
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" -s R9RY1049GVX install -r app\build\outputs\apk\debug\app-debug.apk
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" -s R9RY1049GVX shell monkey -p com.christopher.usbguard 1
pause
