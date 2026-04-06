
#!/bin/bash




# 2. 安装 Docker
if ! [ -x "$(command -v docker)" ]; then
    echo "检测到未安装 Docker，开始安装..."
    ./install-docker.sh --mirror Aliyun
    systemctl enable docker
    systemctl start docker
else
    echo "Docker 已安装，跳过。"
fi

# 3. 初始化 Swarm 模式
if [ "$(docker info --format '{{.Swarm.LocalNodeState}}')" != "active" ]; then
    echo "正在初始化 Docker Swarm..."
    docker swarm init
else
    echo "Swarm 已处于 active 状态。"
fi

# 4. 创建必要的目录
echo "创建数据持久化目录..."
mkdir -p /home/data/mysql /home/data/storage

# 5. 部署 MySQL & Redis
# 注意：这里自动将非法的堆栈名 _swarm_main 修改为 swarm-main
echo "正在部署基础服务 (MySQL & Redis)..."
docker stack deploy -c mysql-redis.yaml swarm-base

echo "等待 MySQL 启动 (30秒)..."
sleep 30

# 6. 自动导入数据库脚本
MYSQL_CONTAINER=$(docker ps -f "name=swarm-base_mysql" --format "{{.ID}}")
if [ -z "$MYSQL_CONTAINER" ]; then
    echo "错误：未找到 MySQL 容器，请检查 mysql-redis.yaml 配置及密码设置。"
    exit 1
fi

echo "正在导入 SQL 初始化脚本..."
# 这里假设你的 sql 文件在 config 目录下
docker cp /home/data/deploy/config/opensign.sql $MYSQL_CONTAINER:/home/
# 注意：此处的密码需与 mysql-redis.yaml 中设置的一致，脚本中建议手动输入一次或从 yaml 提取
echo "请输入你在 mysql-redis.yaml 中设置的 MySQL 密码进行导入："
docker exec -it $MYSQL_CONTAINER /bin/bash -c "mysql -uroot -p opensign < /home/opensign.sql"

# 7. 部署开放签应用服务
echo "正在部署开放签应用服务..."
# 同样修改堆栈名为合规名称
docker stack deploy -c opensign.yaml swarm-opensign

echo "------------------------------------------------"
echo "部署指令提交完成！"
echo "查看服务状态命令：docker service ls"
echo "查看具体容器状态：docker ps"
echo "------------------------------------------------"
