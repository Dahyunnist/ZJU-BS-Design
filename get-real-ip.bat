@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
echo ==========================================
echo 查找真实WiFi网络IP地址
echo ==========================================
echo.

echo 正在分析网络适配器...
echo.

echo 所有IPv4地址及对应适配器:
echo --------------------------------------------
REM 获取适配器名称和IP的对应关系
set ADAPTER_NAME=
for /f "delims=" %%a in ('ipconfig /all') do (
    set "LINE=%%a"
    REM 检测适配器名称
    echo !LINE! | findstr /i "适配器\|adapter\|Wi-Fi\|WLAN\|无线" >nul
    if !errorlevel! equ 0 (
        set "ADAPTER_NAME=!LINE!"
    )
    REM 检测IP地址
    echo !LINE! | findstr /c:"IPv4" /c:"IPv4 地址" >nul
    if !errorlevel! equ 0 (
        for /f "tokens=2 delims=:" %%b in ("!LINE!") do (
            set "IP=%%b"
            set "IP=!IP:~1!"
            if defined ADAPTER_NAME (
                echo   适配器: !ADAPTER_NAME!
                echo   IP地址: !IP!
                echo.
            )
        )
    )
)

echo.
echo 详细分析每个IP地址:
echo --------------------------------------------
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4" /c:"IPv4 地址"') do (
    set "IP=%%a"
    set "IP=!IP:~1!"
    set "IS_VIRTUAL=0"
    
    echo   IP: !IP!
    
    REM 检查是否是虚拟适配器IP
    echo !IP! | findstr "^192\.168\.56\." >nul
    if !errorlevel! equ 0 (
        echo     类型: VirtualBox虚拟适配器 ^(忽略^)
        set "IS_VIRTUAL=1"
    )
    echo !IP! | findstr "^192\.168\.244\." >nul
    if !errorlevel! equ 0 (
        echo     类型: VMware VMnet8虚拟适配器 ^(忽略^)
        set "IS_VIRTUAL=1"
    )
    echo !IP! | findstr "^192\.168\.111\." >nul
    if !errorlevel! equ 0 (
        echo     类型: VMware VMnet1虚拟适配器 ^(忽略^)
        set "IS_VIRTUAL=1"
    )
    echo !IP! | findstr "^172\.17\." >nul
    if !errorlevel! equ 0 (
        echo     类型: Docker网络 ^(忽略^)
        set "IS_VIRTUAL=1"
    )
    echo !IP! | findstr "^169\.254\." >nul
    if !errorlevel! equ 0 (
        echo     类型: 自动配置地址 ^(忽略^)
        set "IS_VIRTUAL=1"
    )
    
    if !IS_VIRTUAL! equ 0 (
        echo     类型: 可能是真实网络IP ^(推荐测试^)
        if not defined REAL_IP (
            set "REAL_IP=!IP!"
        )
    )
    echo.
)

echo ==========================================
echo 推荐使用的IP地址（按优先级）:
echo ==========================================
echo.

REM 优先选择10.x.x.x（企业网络或某些路由器）
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4" /c:"IPv4 地址"') do (
    set "IP=%%a"
    set "IP=!IP:~1!"
    echo !IP! | findstr "^10\." >nul
    if !errorlevel! equ 0 (
        echo !IP! | findstr "^192\.168\.56\." >nul
        if !errorlevel! neq 0 (
            echo !IP! | findstr "^192\.168\.244\." >nul
            if !errorlevel! neq 0 (
                echo !IP! | findstr "^192\.168\.111\." >nul
                if !errorlevel! neq 0 (
                    echo   优先级1: http://!IP!:3000
                    echo     ^(10.x.x.x 通常是真实网络IP^)
                    echo.
                    if not defined PRIMARY_IP (
                        set "PRIMARY_IP=!IP!"
                    )
                )
            )
        )
    )
)

REM 其次选择192.168.0.x 或 192.168.1.x
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4" /c:"IPv4 地址"') do (
    set "IP=%%a"
    set "IP=!IP:~1!"
    echo !IP! | findstr "^192\.168\.[01]\." >nul
    if !errorlevel! equ 0 (
        echo   优先级2: http://!IP!:3000
        echo     ^(192.168.0.x 或 192.168.1.x 常见WiFi IP^)
        echo.
        if not defined PRIMARY_IP (
            set "PRIMARY_IP=!IP!"
        )
    )
)

if defined PRIMARY_IP (
    echo ==========================================
    echo 最终推荐:
    echo ==========================================
    echo   手机访问地址: http://!PRIMARY_IP!:3000
    echo.
    echo   请复制上面的地址到手机浏览器测试
    echo   如果无法访问，请尝试其他IP地址
) else (
    echo   警告: 无法自动确定最佳IP地址
    echo   请手动测试以下IP地址:
    for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4" /c:"IPv4 地址"') do (
        set "IP=%%a"
        set "IP=!IP:~1!"
        echo !IP! | findstr "^192\.168\.56\." >nul
        if !errorlevel! neq 0 (
            echo !IP! | findstr "^192\.168\.244\." >nul
            if !errorlevel! neq 0 (
                echo !IP! | findstr "^192\.168\.111\." >nul
                if !errorlevel! neq 0 (
                    echo !IP! | findstr "^172\.17\." >nul
                    if !errorlevel! neq 0 (
                        echo !IP! | findstr "^169\.254\." >nul
                        if !errorlevel! neq 0 (
                            echo     http://!IP!:3000
                        )
                    )
                )
            )
        )
    )
)

echo.
echo ==========================================
echo 测试建议:
echo ==========================================
echo.
echo 1. 在电脑浏览器中测试每个IP地址
echo    如果电脑能访问，手机也应该能访问
echo.
echo 2. 确保手机和电脑连接的是同一个WiFi
echo    查看WiFi名称（SSID）是否相同
echo.
echo 3. 如果所有IP都无法访问，检查:
echo    - Windows防火墙设置
echo    - Docker端口绑定（运行: docker ps）
echo    - 路由器是否允许设备间通信
echo.
pause

