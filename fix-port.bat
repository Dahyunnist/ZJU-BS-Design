@echo off
chcp 65001 >nul
echo ==========================================
echo Fixing port 3306 conflict...
echo ==========================================
echo.

echo Stopping existing containers...
docker-compose down

echo.
echo Checking what's using port 3306...
netstat -ano | findstr :3306

echo.
echo If you see a process, you can stop it with:
echo taskkill /PID <PID> /F
echo.
echo Or we can change the MySQL port in docker-compose.yml
echo.
pause

