@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

rem Locate the Android SDK installed by Android Studio.
if defined ANDROID_SDK_ROOT (
  set "SDK=%ANDROID_SDK_ROOT%"
) else if defined ANDROID_HOME (
  set "SDK=%ANDROID_HOME%"
) else (
  set "SDK=%LOCALAPPDATA%\Android\Sdk"
)
if not exist "%SDK%\platforms\android-36\android.jar" (
  echo Android SDK Platform 36 was not found.
  echo Open Android Studio ^> Tools ^> SDK Manager and install:
  echo   - Android SDK Platform 36
  echo   - Android SDK Build-Tools 36.0.0
  echo Then run this file again.
  pause
  exit /b 1
)
set "ANDROID_SDK_ROOT=%SDK%"
set "ANDROID_HOME=%SDK%"

rem Use a compatible Gradle 9.6.0 installation if it is already on PATH.
set "GRADLE_CMD="
where gradle >nul 2>nul
if not errorlevel 1 (
  gradle --version | findstr /c:"Gradle 9.6.0" >nul
  if not errorlevel 1 set "GRADLE_CMD=gradle"
)

rem Otherwise download the version required by Android Gradle Plugin 9.4.0.
if not defined GRADLE_CMD (
  set "TOOLS=%LOCALAPPDATA%\ClassicKeyboardBuildTools"
  set "GRADLE_HOME=!TOOLS!\gradle-9.6.0"
  if not exist "!GRADLE_HOME!\bin\gradle.bat" (
    if not exist "!TOOLS!" mkdir "!TOOLS!"
    echo Downloading Gradle 9.6.0. This is a one-time download.
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=Join-Path $env:LOCALAPPDATA 'ClassicKeyboardBuildTools\gradle-9.6.0-bin.zip'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-9.6.0-bin.zip' -OutFile $zip; Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $env:LOCALAPPDATA 'ClassicKeyboardBuildTools') -Force; Remove-Item $zip -Force"
    if errorlevel 1 (
      echo Gradle could not be downloaded. Install Gradle 9.6.0 manually or use Android Studio.
      pause
      exit /b 1
    )
  )
  set "GRADLE_CMD=!GRADLE_HOME!\bin\gradle.bat"
)

echo.
echo Building Classic Keyboard...
call "%GRADLE_CMD%" --no-daemon assembleDebug
if errorlevel 1 (
  echo.
  echo Build failed. Check the Gradle and Android SDK configuration above.
  pause
  exit /b 1
)
echo.
echo APK created:
echo %CD%\app\build\outputs\apk\debug\app-debug.apk
pause
