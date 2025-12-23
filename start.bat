@echo off
chcp 65001 >nul
echo ==========================================
echo 智能图片管理网站 - 启动脚本
echo ==========================================
echo.

REM 检查Docker是否安装
where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未安装Docker，请先安装Docker
    pause
    exit /b 1
)

where docker-compose >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未安装Docker Compose，请先安装Docker Compose
    pause
    exit /b 1
)

REM 创建必要的目录
echo Creating necessary directories...
if not exist uploads mkdir uploads
if not exist thumbnails mkdir thumbnails

REM 启动服务
echo Starting Docker containers...
docker-compose up -d --build

echo.
echo ==========================================
echo Services are starting, please wait...
echo ==========================================
echo.
echo Waiting for services to start (about 30 seconds)...
timeout /t 30 /nobreak >nul

echo.
echo ==========================================
echo Services started!
echo ==========================================
echo.
echo Frontend: http://localhost:3000
echo Backend API: http://localhost:8080/api
echo.
echo View logs: docker-compose logs -f
echo Stop services: docker-compose down
echo.
pause

