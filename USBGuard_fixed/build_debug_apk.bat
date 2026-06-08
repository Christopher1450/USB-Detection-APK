@echo off
cd /d %~dp0
set "JAVA_HOME=C:\Program Files\Android\Android Studio2\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version
gradlew.bat --stop
gradlew.bat :app:assembleDebug
pause
