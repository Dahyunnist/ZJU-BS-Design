@echo off
chcp 65001 >nul
echo ==========================================
echo Checking service status...
echo ==========================================
echo.

echo Container status:
docker-compose ps

echo.
echo ==========================================
echo Backend logs (last 50 lines):
echo ==========================================
docker-compose logs --tail=50 backend

echo.
echo ==========================================
echo MySQL logs (last 20 lines):
echo ==========================================
docker-compose logs --tail=20 mysql

echo.
pause

