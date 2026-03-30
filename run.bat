@echo off
title Drone Delivery Optimizer
set JAVA_HOME=C:\Program Files\Java\jdk-17
set MVN=C:\tools\apache-maven-3.9.14\bin\mvn.cmd

echo ==============================================
echo   Drone Delivery Optimizer - TSP Solver
echo ==============================================
echo.
echo Starting application...
%MVN% javafx:run -f "%~dp0pom.xml" -q
pause
