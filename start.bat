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

docker compose version >nul 2>&1
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
docker compose up -d --build

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

:: 定义要查找的目标网卡名称
set "target_adapter=Wireless LAN adapter WLAN 2"
:: 初始化IPv4地址变量
set "ipv4_address="
:: 标记：0=未找到目标网卡，1=已找到目标网卡，2=已提取到IPv4
set "flag=0"

:: 一次性遍历ipconfig所有输出行，解决跨网卡提取问题
for /f "tokens=* delims=" %%a in ('ipconfig') do (
    set "line=%%a"

    :: 状态0：未找到目标网卡，匹配网卡名称
    if !flag! equ 0 (
        echo !line! | findstr /i /c:"!target_adapter!" > nul 2>&1
        if not errorlevel 1 (
            set "flag=1"
        )
    )

    :: 状态1：已找到目标网卡，提取IPv4地址
    if !flag! equ 1 (
        echo !line! | findstr /i /c:"IPv4 Address" > nul 2>&1
        if not errorlevel 1 (
            :: 分割冒号并去除多余空格，提取纯净IPv4
            for /f "tokens=2 delims=:" %%c in ("!line!") do (
                for /f "tokens=* delims= " %%d in ("%%c") do (
                    set "ipv4_address=%%d"
                    set "flag=2"  :: 提取成功，标记状态并终止后续判断
                )
            )
        )
    )

    :: 状态2：已提取到IPv4，提前退出循环（提升效率）
    if !flag! equ 2 (
        goto :exit_for_loop
    )
)
:exit_for_loop  :: 循环终止标签，避免括号嵌套冲突

:: 输出IP查找异常提示
if not "!ipv4_address!"=="" (
    REM 找到IP不额外输出（如需可取消注释）
    :: echo 目标网卡：%target_adapter%
    :: echo 对应的IPv4地址：!ipv4_address!
) else (
    echo 错误：未找到网卡【%target_adapter%】或该网卡未分配有效IPv4地址！
)

echo PC访问地址:
echo   Frontend: http://localhost:3000
echo   Backend API: http://localhost:8080/api
echo.
echo 局域网访问默认关闭，服务端口仅绑定本机回环地址。
echo.
echo ==========================================
echo 其他命令:
echo ==========================================
echo   View logs: docker compose logs -f
echo   Stop services: docker compose down
echo.

endlocal
pause
