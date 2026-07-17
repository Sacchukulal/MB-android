@echo off
cd /d C:\Data_Drive\MagicBill\MB-android
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
call .\gradlew.bat :app:assembleRelease --no-daemon > build-log.txt 2>&1
echo EXITCODE:%ERRORLEVEL% >> build-log.txt
