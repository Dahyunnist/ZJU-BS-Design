@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
echo ==========================================
echo 修复手机访问问题
echo ==========================================
echo.

echo 步骤1: 检查Docker容器端口绑定...
docker ps --format "table {{.Names}}\t{{.Ports}}" | findstr "3000\|8080"
echo.

echo 步骤2: 检查Windows防火墙状态...
netsh advfirewall show allprofiles state 2>nul
echo.

echo 步骤3: 获取本机IP地址...
echo   所有IPv4地址:
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4" /c:"IPv4 地址"') do (
    set "TEMP_IP=%%a"
    set "TEMP_IP=!TEMP_IP:~1!"
    echo     !TEMP_IP!
    REM 排除VirtualBox虚拟适配器
    echo !TEMP_IP! | findstr "^192\.168\.56\." >nul
    if !errorlevel! neq 0 (
        echo !TEMP_IP! | findstr "^192\.168\.[01]\." >nul
        if !errorlevel! equ 0 (
            set "REAL_IP=!TEMP_IP!"
        )
    )
)
echo.
if defined REAL_IP (
    echo   推荐使用的WiFi IP: !REAL_IP!
    echo   手机访问地址: http://!REAL_IP!:3000
) else (
    echo   警告: 未找到有效的WiFi网络IP
    echo   请运行: get-real-ip.bat 来查找正确的IP地址
)
echo.

echo ==========================================
echo 解决方案:
echo ==========================================
echo.
echo 如果手机无法访问，请按顺序尝试:
echo.
echo 1. 确保使用正确的WiFi IP地址（最重要！）
echo    运行: get-real-ip.bat
echo    排除 192.168.56.x (VirtualBox虚拟适配器)
echo    应该使用 192.168.0.x 或 192.168.1.x
echo.
echo 2. 确保手机和电脑在同一WiFi网络
echo    手机WiFi设置中查看连接的WiFi名称
echo    电脑网络设置中查看连接的WiFi名称
echo    两者必须相同
echo.
echo 3. 检查Windows防火墙（如果已开启）
echo    添加端口规则: TCP 3000, 8080
echo.
echo 4. 验证Docker端口绑定
echo    运行: docker ps
echo    确保显示 0.0.0.0:3000->3000/tcp
echo.
pause

