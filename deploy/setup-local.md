# OpenSign local setup on macOS

This setup is meant for Docker Desktop on macOS and does not require Docker Swarm.
It also avoids all `registry.cn-beijing.aliyuncs.com/kaifangqian/*` images.

## 1. Build and start services

```bash
cd deploy
docker compose -f docker-compose.local.yml build
docker compose -f docker-compose.local.yml up -d
```

The first build can take a while because:

- the backend image is built from `../kaifangqian-parent`
- the frontend image is built from `../kaifangqian-web`
- MySQL needs to import `../kaifangqian-parent/sql/opensign.sql`

## 2. Check service status

```bash
docker compose -f docker-compose.local.yml ps
docker compose -f docker-compose.local.yml logs -f mysql
docker compose -f docker-compose.local.yml logs -f api
docker compose -f docker-compose.local.yml logs -f web
```

## 3. Access URLs

- Web: `http://localhost:8080`
- API health check: `http://localhost:8899/resrun-paas/`
- MySQL: `127.0.0.1:3307`
- Redis: `127.0.0.1:6379`

## 4. Default local credentials

- MySQL root password: `opensign123`
- Redis password: `opensign123`

## 5. Stop services

```bash
cd deploy
docker compose -f docker-compose.local.yml down
```

## 6. Reset data and rebuild

```bash
cd deploy
docker compose -f docker-compose.local.yml down -v
rm -rf data/mysql data/redis data/storage
docker compose -f docker-compose.local.yml build --no-cache
docker compose -f docker-compose.local.yml up -d
```

## Notes

- `sys_app_info.app_address` is rewritten to `http://localhost:8080` on first
  database initialization so the built-in links match the local web port.
- The backend image disables PowerJob worker registration by setting
  `POWERJOB_WORKER_ENABLED=false`, so it does not depend on the broken
  `kaifangqian/job` image.
- If you want to change passwords or ports, edit
  `deploy/docker-compose.local.yml` before the first startup.
