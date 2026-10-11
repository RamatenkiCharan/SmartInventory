@echo off
REM Compile and run (needs JDK 17+).  Usage: run.bat [--reset] [--console]
cd /d %~dp0
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -cp lib\sqlite-jdbc.jar -d out @sources.txt
del sources.txt
java -cp "out;lib\sqlite-jdbc.jar" com.inventory.Main %*
