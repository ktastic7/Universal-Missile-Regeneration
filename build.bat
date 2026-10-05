@echo off
setlocal
if "%STARSECTOR_API%"=="" (
  echo Set STARSECTOR_API to the exact RC8 starfarer.api.jar
  exit /b 1
)
if "%LOG4J_JAR%"=="" (
  echo Set LOG4J_JAR to the exact RC8 log4j-1.2.9.jar
  exit /b 1
)
if "%JSON_JAR%"=="" (
  echo Set JSON_JAR to the exact RC8 json.jar
  exit /b 1
)
if "%LUNALIB_JAR%"=="" (
  echo Set LUNALIB_JAR to the exact LunaLib 2.0.5 LunaLib.jar
  exit /b 1
)
set ROOT=%~dp0
if exist "%ROOT%build" rmdir /s /q "%ROOT%build"
mkdir "%ROOT%build\classes"
if not exist "%ROOT%jars" mkdir "%ROOT%jars"
dir /s /b "%ROOT%src\*.java" > "%ROOT%build\sources.txt"
javac --release 8 -Xlint:-options -cp "%STARSECTOR_API%;%LOG4J_JAR%;%JSON_JAR%;%LUNALIB_JAR%" -d "%ROOT%build\classes" @"%ROOT%build\sources.txt"
if errorlevel 1 exit /b 1
jar --create --file "%ROOT%jars\UniversalMissileRegeneration.jar" --date=2000-01-01T00:00:00Z -C "%ROOT%build\classes" .
endlocal
