@echo off
chcp 65001 >nul
echo ==========================================
echo Pulling Docker images manually...
echo ==========================================
echo.

echo Pulling MySQL...
docker pull mysql:8.0

echo.
echo Pulling Maven...
docker pull maven:3.9-eclipse-temurin-17

echo.
echo Pulling Node...
docker pull node:18-alpine

echo.
echo Pulling Nginx...
docker pull nginx:alpine

echo.
echo Pulling Eclipse Temurin...
docker pull eclipse-temurin:17-jre-alpine

echo.
echo ==========================================
echo All images pulled!
echo ==========================================
echo.
pause

