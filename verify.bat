@echo off
setlocal enabledelayedexpansion

echo.
echo =========================================================
echo  AI Learning OS — Vibe Coding 验收脚本
echo  每次 AI 生成代码结束后运行，不通过禁止提交！
echo =========================================================
echo.

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend-spring"
set PASS=0

:: ─── Step 1: 编译检查 ─────────────────────────────────────────────────────
echo [1/3] 编译 backend...
cd /d "%BACKEND%"
call mvnw.cmd compile -q 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo [FAIL] ❌ 编译失败！修复所有错误后再运行此脚本。
    echo        常见原因：方法签名修改后调用处未同步更新。
    exit /b 1
)
echo [OK]  编译通过。

:: ─── Step 2: 纯逻辑单元测试（无 DB 依赖） ────────────────────────────────
echo [2/3] 运行节点状态机单元测试（NodeFsmTest）...
call mvnw.cmd test -Dtest=NodeFsmTest -q 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo [FAIL] ❌ NodeFsmTest 失败！节点状态机逻辑有回归，请检查规则。
    exit /b 1
)
echo [OK]  节点状态机测试通过。

:: ─── Step 3: Spring 上下文 Smoke Test（需要 Docker 已启动） ─────────────
echo [3/3] 运行 Spring 上下文 Smoke Test...
echo       ^(使用 H2 内嵌数据库，不依赖 Docker^)
call mvnw.cmd test -Dtest=SmokeTest -Dspring.profiles.active=test -q 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo [FAIL] ❌ Spring 上下文启动失败！可能原因：
    echo        - Bean 注入错误或循环依赖
    echo        - 新增配置项未在 application-test.yml 中提供默认值
    echo        - 新增 Entity 与 H2 不兼容（检查 SQL 方言）
    exit /b 1
)
echo [OK]  Spring 上下文 Smoke Test 通过。

:: ─── 全部通过 ─────────────────────────────────────────────────────────────
echo.
echo =========================================================
echo  [PASS] ✅ 所有验收检查通过，可以提交代码！
echo =========================================================
echo.
exit /b 0
