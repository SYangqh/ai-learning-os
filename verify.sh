#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND="$ROOT/backend-spring"

echo ""
echo "========================================================="
echo " AI Learning OS — Vibe Coding 验收脚本"
echo " 每次 AI 生成代码结束后运行，不通过禁止提交！"
echo "========================================================="
echo ""

fail() {
    echo ""
    echo "[FAIL] ❌ $1"
    exit 1
}

# ─── Step 1: 编译检查 ────────────────────────────────────────────────────────
echo "[1/3] 编译 backend..."
cd "$BACKEND"
./mvnw compile -q || fail "编译失败！修复所有错误后再运行此脚本。常见原因：方法签名修改后调用处未同步更新。"
echo "[OK]  编译通过。"

# ─── Step 2: 纯逻辑单元测试 ─────────────────────────────────────────────────
echo "[2/3] 运行节点状态机单元测试（NodeFsmTest）..."
./mvnw test -Dtest=NodeFsmTest -q || fail "NodeFsmTest 失败！节点状态机逻辑有回归，请检查规则。"
echo "[OK]  节点状态机测试通过。"

# ─── Step 3: Spring 上下文 Smoke Test ───────────────────────────────────────
echo "[3/3] 运行 Spring 上下文 Smoke Test（H2 内嵌数据库）..."
./mvnw test -Dtest=SmokeTest -Dspring.profiles.active=test -q || fail "Spring 上下文启动失败！检查 Bean 注入、配置项、Entity 兼容性。"
echo "[OK]  Spring 上下文 Smoke Test 通过。"

# ─── 全部通过 ────────────────────────────────────────────────────────────────
echo ""
echo "========================================================="
echo " [PASS] ✅ 所有验收检查通过，可以提交代码！"
echo "========================================================="
echo ""
