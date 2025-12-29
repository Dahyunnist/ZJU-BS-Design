@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
echo ==========================================
echo 测试IP地址访问性
echo ==========================================
echo.

echo 请输入要测试的IP地址（直接回车使用10.193.89.62）:
set /p TEST_IP=
if "!TEST_IP!"=="" set TEST_IP=10.193.89.62

echo.
echo 正在测试 http://!TEST_IP!:3000 ...
echo.

REM 使用PowerShell测试连接
powershell -Command "try { $response = Invoke-WebRequest -Uri 'http://!TEST_IP!:3000' -TimeoutSec 5 -UseBasicParsing; Write-Host '✓ 可以访问！状态码:' $response.StatusCode } catch { Write-Host '✗ 无法访问:' $_.Exception.Message }"

echo.
echo 如果显示"可以访问"，说明这个IP地址是正确的
echo 如果显示"无法访问"，请尝试其他IP地址
echo.
pause

