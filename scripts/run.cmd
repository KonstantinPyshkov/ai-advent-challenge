@echo off
setlocal
cd /d "%~dp0.."

if "%~1"=="day1" (
  shift
  call mvn -q -Dmaven.repo.local="%~dp0..\.m2repo" -pl days/day_01 -am package
  if errorlevel 1 exit /b %errorlevel%
  java -jar "days\day_01\target\day01.jar" %*
  exit /b %errorlevel%
)

if "%~1"=="day2" (
  call mvn -q -Dmaven.repo.local="%~dp0..\.m2repo" -pl cli -am package
  if errorlevel 1 exit /b %errorlevel%
  java -jar "cli\target\lab.jar" day2 %2 %3 %4 %5 %6
  exit /b %errorlevel%
)

if "%~1"=="day3" (
  call mvn -q -Dmaven.repo.local="%~dp0..\.m2repo" -pl cli -am package
  if errorlevel 1 exit /b %errorlevel%
  java -jar "cli\target\lab.jar" day3 %2 %3 %4 %5 %6
  exit /b %errorlevel%
)

if "%~1"=="day4" (
  call mvn -q -Dmaven.repo.local="%~dp0..\.m2repo" -pl cli -am package
  if errorlevel 1 exit /b %errorlevel%
  java -jar "cli\target\lab.jar" day4 %2 %3 %4 %5 %6
  exit /b %errorlevel%
)

echo Usage: run.cmd day1 "question" ^| run.cmd day2^|day3^|day4 [--dry-run]
exit /b 2
