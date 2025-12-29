@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
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

REM 获取本机IP地址（排除虚拟适配器）
set IP=
REM 优先查找10.x.x.x（企业网络或某些路由器配置，通常是真实WiFi IP）
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4" /c:"IPv4 地址"') do (
    set "TEMP_IP=%%a"
    set "TEMP_IP=!TEMP_IP:~1!"
    REM 排除所有虚拟适配器IP
    echo !TEMP_IP! | findstr "^192\.168\.56\." >nul
    if !errorlevel! neq 0 (
        echo !TEMP_IP! | findstr "^192\.168\.244\." >nul
        if !errorlevel! neq 0 (
            echo !TEMP_IP! | findstr "^192\.168\.111\." >nul
            if !errorlevel! neq 0 (
                echo !TEMP_IP! | findstr "^172\.17\." >nul
                if !errorlevel! neq 0 (
                    echo !TEMP_IP! | findstr "^169\.254\." >nul
                    if !errorlevel! neq 0 (
                        REM 优先选择10.x.x.x
                        echo !TEMP_IP! | findstr "^10\." >nul
                        if !errorlevel! equ 0 (
                            set "IP=!TEMP_IP!"
                            goto :found_ip
                        )
                    )
                )
            )
        )
    )
)
:found_ip

REM 方法1: 如果没找到10.x.x.x，查找192.168.0.x 或 192.168.1.x（常见WiFi IP）
if not defined IP (
    for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4" /c:"IPv4 地址"') do (
        set "TEMP_IP=%%a"
        set "TEMP_IP=!TEMP_IP:~1!"
        REM 排除所有虚拟适配器IP
        echo !TEMP_IP! | findstr "^192\.168\.56\." >nul
        if !errorlevel! neq 0 (
            echo !TEMP_IP! | findstr "^192\.168\.244\." >nul
            if !errorlevel! neq 0 (
                echo !TEMP_IP! | findstr "^192\.168\.111\." >nul
                if !errorlevel! neq 0 (
                    REM 检查是否是192.168.0.x 或 192.168.1.x（常见WiFi IP）
                    echo !TEMP_IP! | findstr "^192\.168\.[01]\." >nul
                    if !errorlevel! equ 0 (
                        set "IP=!TEMP_IP!"
                        goto :found_ip2
                    )
                )
            )
        )
    )
)
:found_ip2

REM 方法2: 如果没找到，尝试中文系统
if not defined IP (
    for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4 地址"') do (
        set "TEMP_IP=%%a"
        set "TEMP_IP=!TEMP_IP:~1!"
        echo !TEMP_IP! | findstr "^192\.168\.56\." >nul
        if !errorlevel! neq 0 (
            echo !TEMP_IP! | findstr "^192\.168\.[01]\." >nul
            if !errorlevel! equ 0 (
                set "IP=!TEMP_IP!"
                goto :found_ip2
            )
            echo !TEMP_IP! | findstr "^192\.168\." >nul
            if !errorlevel! equ 0 (
                if not defined IP (
                    set "IP=!TEMP_IP!"
                )
            )
            echo !TEMP_IP! | findstr "^10\." >nul
            if !errorlevel! equ 0 (
                if not defined IP (
                    set "IP=!TEMP_IP!"
                )
            )
        )
    )
)
:found_ip2

REM 如果还是没找到，尝试10.x.x.x（可能是企业网络）
if not defined IP (
    for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4" /c:"IPv4 地址"') do (
        set "TEMP_IP=%%a"
        set "TEMP_IP=!TEMP_IP:~1!"
        REM 排除虚拟适配器
        echo !TEMP_IP! | findstr "^192\.168\.56\." >nul
        if !errorlevel! neq 0 (
            echo !TEMP_IP! | findstr "^192\.168\.244\." >nul
            if !errorlevel! neq 0 (
                echo !TEMP_IP! | findstr "^192\.168\.111\." >nul
                if !errorlevel! neq 0 (
                    echo !TEMP_IP! | findstr "^10\." >nul
                    if !errorlevel! equ 0 (
                        set "IP=!TEMP_IP!"
                        goto :found_ip3
                    )
                )
            )
        )
    )
)
:found_ip3

REM 如果还是没找到，提示用户手动查看
if not defined IP (
    echo   警告: 无法自动获取WiFi网络IP地址
    echo   请运行: get-real-ip.bat 来查找正确的IP地址
    echo   或手动运行: ipconfig ^| findstr IPv4
    echo   注意: 排除虚拟适配器IP (192.168.56.x, 192.168.244.x, 192.168.111.x)
    echo.
    set IP=[请运行get-real-ip.bat查找正确IP]
)

echo PC访问地址:
echo   Frontend: http://localhost:3000
echo   Backend API: http://localhost:8080/api
echo.
echo ==========================================
echo 手机访问地址（请确保手机和电脑在同一WiFi网络）:
echo ==========================================
echo.
echo   ===========================================
echo   http://%IP%:3000
echo   ===========================================
echo.
echo   请复制上面的地址到手机浏览器打开
echo.
echo   如果无法访问，请检查:
echo   1. 确保使用的是WiFi网络IP（不是192.168.56.x虚拟适配器）
echo      运行: get-real-ip.bat 查找正确的IP地址
echo   2. 手机和电脑是否在同一WiFi网络
echo   3. Windows防火墙是否允许3000端口
echo   4. 运行: docker ps 检查端口是否绑定到0.0.0.0
echo.
echo ==========================================
echo 其他命令:
echo ==========================================
echo   View logs: docker-compose logs -f
echo   Stop services: docker-compose down
echo.
pause

