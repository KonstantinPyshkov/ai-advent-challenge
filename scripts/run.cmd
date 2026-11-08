@echo off
setlocal
cd /d "%~dp0.."

call mvn -q -Dmaven.repo.local="%~dp0..\.m2repo" -pl days/day_01 -am package
if errorlevel 1 exit /b %errorlevel%

java -jar "days\day_01\target\day01.jar" %*
exit /b %errorlevel%
