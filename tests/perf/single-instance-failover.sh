#!/usr/bin/env bash
# =====================================================================
# 阶段6 - TC-009-02 自动化: 单实例停掉后仍可用
# 注意: 这是验证脚本占位; 实际执行以测试组为主, 后端仅提供依据。
# =====================================================================
set -e

echo "=== 当前两个后端实例的 instance 分布 ==="
for i in $(seq 1 6); do
  curl -s http://localhost/api/health \
    | python -c "import sys,json; print(json.load(sys.stdin)['data']['instance'])"
done

echo
echo "=== 停掉 backend-app-1 ==="
docker stop backend-app-1 || true
sleep 3

echo "=== 停掉后 6 次请求应全部返回 backend-app-2 ==="
for i in $(seq 1 6); do
  curl -s http://localhost/api/health \
    | python -c "import sys,json; print(json.load(sys.stdin)['data']['instance'])"
done

echo
echo "=== 恢复 backend-app-1 ==="
docker start backend-app-1
sleep 5
echo "验证完成"
