#!/bin/bash
# WeChatMem 本地开发启动脚本

BACKEND_PORT=9721
FRONTEND_PORT=9722
BACKEND_DIR="$(cd "$(dirname "$0")/backend" && pwd)"
FRONTEND_DIR="$(cd "$(dirname "$0")/frontend" && pwd)"

echo "=== WeChatMem 本地开发环境 ==="
echo "后端: http://localhost:$BACKEND_PORT"
echo "前端: http://localhost:$FRONTEND_PORT"
echo ""

# 安装后端依赖
echo "[1/4] 检查后端依赖..."
cd "$BACKEND_DIR"
pip install -q -r requirements.txt 2>/dev/null

# 启动后端
echo "[2/4] 启动后端服务 (端口 $BACKEND_PORT)..."
uvicorn app.main:app --host 0.0.0.0 --port $BACKEND_PORT --reload &
BACKEND_PID=$!
sleep 3

# 验证后端
echo "[3/4] 验证后端..."
HEALTH=$(curl -s http://localhost:$BACKEND_PORT/api/v1/health)
if [ $? -eq 0 ]; then
    echo "  后端启动成功: $HEALTH"
else
    echo "  后端启动失败!"
    kill $BACKEND_PID 2>/dev/null
    exit 1
fi

# 测试注册接口
echo "[4/4] 验证认证接口..."
REG=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:$BACKEND_PORT/api/v1/auth/register \
    -H "Content-Type: application/json" \
    -d '{"username":"dev","password":"dev123"}')
if [ "$REG" = "201" ]; then
    echo "  注册接口正常 (已创建测试账号 dev/dev123)"
elif [ "$REG" = "409" ]; then
    echo "  注册接口正常 (测试账号 dev 已存在)"
else
    echo "  注册接口返回: $REG"
fi

echo ""
echo "=== 后端已在后台运行 (PID: $BACKEND_PID) ==="
echo "停止服务: kill $BACKEND_PID"
echo ""

# 保持前台运行
wait $BACKEND_PID
