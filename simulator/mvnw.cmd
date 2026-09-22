@REM Maven wrapper startup script for Windows
@if "%DEBUG%"=="" @echo off
@REM set MAVEN_BATCH_INTERACTIVE=false
@REM set MAVEN_BATCH_PAUSE=false

setlocal enabledelayedexpansion

set ERROR_CODE=0

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" (
    setlocal enableextensions enabledelayedexpansion
)

@REM ==== START VALIDATION ====
if not "%MAVEN_PROJECTBASEDIR%"=="" goto basename
goto error

:basename
set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR%"=="" set MAVEN_PROJECTBASEDIR=.

:end_basename

set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:"=%
set WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar
set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar

@REM Download wrapper jar if not present
if exist %WRAPPER_JAR% goto init
echo Downloading %WRAPPER_URL%
if not exist "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper" mkdir "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper"
powershell -Command "(New-Object Net.WebClient).DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"

:init
set MAVEN_JAVA_EXE="%JAVA_HOME%\bin\java.exe"
if not exist "%MAVEN_JAVA_EXE%" (
    set MAVEN_JAVA_EXE=java.exe
)
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@REM ==== STARTUP ====
"%MAVEN_JAVA_EXE%" ^
    %DEFAULT_JVM_OPTS% %JAVA_OPTS% %MAVEN_OPTS% ^
    "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
    -classpath "%WRAPPER_JAR%" ^
    "%WRAPPER_LAUNCHER%" %*

if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%

if not "%MAVEN_PROJECTBASEDIR%"=="" goto cleanup

:cleanup
cd "%WRAPPER_LAUNCHER%"

cmd /C exit /B %ERROR_CODE%
