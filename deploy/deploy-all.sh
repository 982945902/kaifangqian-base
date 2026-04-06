#!/bin/bash

# 开放签一键部署脚本
# 项目地址: /home/kaifangqian-base

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   开放签 (Kaifangqian) 一键部署${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo -e "${YELLOW}项目目录: ${PROJECT_DIR}${NC}"
echo -e "${YELLOW}部署目录: ${SCRIPT_DIR}${NC}"
echo ""

cd "$SCRIPT_DIR"

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker 未安装!${NC}"
    echo "请先安装 Docker:"
    echo "  curl -fsSL https://get.docker.com | sh"
    exit 1
fi

# 检查 Docker Compose 是否可用
if ! docker compose version &> /dev/null; then
    echo -e "${RED}错误: Docker Compose 不可用!${NC}"
    exit 1
fi

echo -e "${GREEN}[1/4] 检查环境... 通过${NC}"
echo ""

# 停止已运行的服务（如果有）
echo -e "${YELLOW}[2/4] 停止旧服务（如果有）...${NC}"
docker compose -f docker-compose.local.yml down 2>/dev/null || true
echo ""

# 构建 API 镜像（Web 使用预构建镜像）
echo -e "${GREEN}[3/4] 构建 API Docker 镜像...${NC}"
echo -e "${YELLOW}Web 使用预构建镜像，无需构建${NC}"
docker compose -f docker-compose.local.yml build api
echo ""

# 启动服务
echo -e "${GREEN}[4/4] 启动服务...${NC}"
docker compose -f docker-compose.local.yml up -d
echo ""

# 等待服务启动
echo -e "${YELLOW}等待服务启动 (30秒)...${NC}"
sleep 30

# 显示服务状态
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   部署完成!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}服务状态:${NC}"
docker compose -f docker-compose.local.yml ps
echo ""
echo -e "${YELLOW}访问地址:${NC}"
echo "  Web 界面:    http://localhost:8080"
echo "  API 健康检查: http://localhost:8899/resrun-paas/"
echo ""
echo -e "${YELLOW}默认凭据:${NC}"
echo "  MySQL root 密码: Root@123456"
echo "  Redis 密码:      opensign123"
echo ""
echo -e "${YELLOW}端口映射:${NC}"
echo "  MySQL: 3308 (host) -> 3306 (container)"
echo "  Redis: 6380 (host) -> 6379 (container)"
echo ""
echo -e "${YELLOW}常用命令:${NC}"
echo "  查看日志:    docker compose -f docker-compose.local.yml logs -f"
echo "  查看 API 日志: docker compose -f docker-compose.local.yml logs -f api"
echo "  查看 Job 日志: docker compose -f docker-compose.local.yml logs -f job"
echo "  停止服务:    docker compose -f docker-compose.local.yml down"
echo "  重启服务:    docker compose -f docker-compose.local.yml restart"
echo ""
echo -e "${GREEN}部署成功! 🎉${NC}"
