# JobClaw 真实生产上线部署清单

本文档用于之后将 JobClaw 部署到公网服务器时执行。目标不是本地演示，而是达到个人项目可真实上线运用的生产级别：公网可访问、数据可恢复、端口收口、安全边界清楚、问题可排查。

工程侧完整说明见 `docs/production-deployment.md`。本文偏个人执行清单。

## 目标架构

```text
公网用户 / IM 回调
  |
  v
域名 + HTTPS
  |
  v
jobclaw-prod-gateway
  |-- /                -> jobclaw-prod-web
  |-- /api/**          -> jobclaw-prod-api
  |-- /oc/img/**       -> jobclaw-prod-api
  |-- /actuator/health -> 健康检查

Docker 内网服务：
  MySQL / Redis / Kafka / Elasticsearch / MinIO / API / Web
```

生产环境只允许 gateway 对外暴露。数据库、中间件、API 端口不直接暴露公网。

## 上线前准备

服务器建议：

```text
最低：2 核 4G
推荐：2 核 8G 或更高
系统：Ubuntu 22.04 LTS / Debian 12 / CentOS Stream 均可
必备：Docker、Docker Compose、Git
```

域名和 HTTPS：

```text
1. 准备一个域名，例如 job.example.com
2. 域名 A 记录指向服务器公网 IP
3. HTTPS 推荐用云厂商证书/CDN/Caddy/宿主机 Nginx 终止
4. 如果直接让项目内 Nginx 管证书，参考 docker/nginx/prod-tls.conf.example
```

安全组/防火墙：

```text
允许公网：80、443
允许 SSH：22，仅自己的公网 IP
禁止公网：3306、6379、8087、8099、9000、9001、9092、9200
```

## 生产配置

在服务器项目根目录执行：

```bash
cp .env.production.example .env.production
```

必须修改：

```text
JOBCLAW_SITE_WEB_SITE_URL=https://你的域名
MYSQL_ROOT_PASSWORD=强密码
REDIS_PASSWORD=强密码
MINIO_ACCESS_KEY=强随机值
MINIO_SECRET_KEY=强随机值
JOBCLAW_JWT_SECRET=至少 32 位随机密钥
```

如果生产机器的 80 端口由项目 gateway 直接占用：

```env
JOBCLAW_PUBLIC_PORT=80
```

如果前面还有 Caddy、宿主机 Nginx、云负载均衡等反向代理：

```env
JOBCLAW_PUBLIC_PORT=18080
```

然后让前置代理转发到：

```text
http://127.0.0.1:18080
```

## 启动生产环境

推荐使用脚本：

```powershell
.\build\docker-prod.ps1
```

Linux 服务器上可直接使用 Docker Compose：

```bash
docker compose --env-file .env.production -f docker/compose/compose.prod.yml up --build -d
```

查看状态：

```bash
docker compose --env-file .env.production -f docker/compose/compose.prod.yml ps
```

正常情况下，只有 `jobclaw-prod-gateway` 有宿主机端口映射；MySQL、Redis、Kafka、ES、MinIO、API 都应只在 Docker 内网。

## 上线验收

浏览器访问：

```text
https://你的域名
```

必须验证：

```text
首页可以打开
岗位列表可以打开
登录流程可用
后台管理未登录不可访问
管理员登录后后台可访问
图片访问正常
IM 回调地址正常
/actuator/health 正常
/actuator/metrics 通过公网返回 404
```

命令检查：

```bash
curl -I https://你的域名/actuator/health
curl -I https://你的域名/actuator/metrics
curl -I https://你的域名/api/admin/users
```

预期：

```text
/actuator/health 返回 200
/actuator/metrics 返回 404
/api/admin/users 未登录返回 401 或 403
```

## 备份

首次上线后立刻跑一次备份，确认备份链路可用。

Windows：

```powershell
.\ops\backup-prod.ps1
```

Linux：

```bash
sh ./ops/backup-prod.sh
```

备份目录：

```text
backups/jobclaw-prod/<时间戳>/
```

至少应包含：

```text
mysql.sql
minio.tgz
workspace.tgz
elasticsearch.tgz
redis.tgz
kafka.tgz
README.txt
```

上线后建议：

```text
每天自动备份一次
升级前手动备份一次
至少保留 14 天
定期下载一份到本地或对象存储
```

## 日常运维

查看服务：

```bash
docker compose --env-file .env.production -f docker/compose/compose.prod.yml ps
```

查看日志：

```bash
docker logs --tail 200 jobclaw-prod-api
docker logs --tail 200 jobclaw-prod-gateway
docker logs --tail 200 jobclaw-prod-mysql
```

重启服务：

```bash
docker compose --env-file .env.production -f docker/compose/compose.prod.yml restart
```

更新部署：

```bash
git pull
sh ./ops/backup-prod.sh
docker compose --env-file .env.production -f docker/compose/compose.prod.yml up --build -d
docker compose --env-file .env.production -f docker/compose/compose.prod.yml ps
```

不要在生产环境随意执行：

```bash
docker compose -f docker/compose/compose.prod.yml down -v
```

`-v` 会删除 Docker volume，等同于删除生产数据。

## 故障排查

入口打不开：

```text
1. 检查域名解析是否指向服务器 IP
2. 检查云安全组是否开放 80/443
3. 检查 HTTPS 前置代理是否正常
4. 检查 jobclaw-prod-gateway 日志
```

后端 API 异常：

```text
1. docker logs --tail 200 jobclaw-prod-api
2. 检查 MySQL 是否 healthy
3. 检查 .env.production 中数据库密码是否一致
4. 检查 Liquibase 启动日志
```

图片异常：

```text
1. 检查 MinIO 是否 healthy
2. 检查 MINIO_ACCESS_KEY / MINIO_SECRET_KEY
3. 检查 JOBCLAW_IMG_STORAGE_TYPE=minio
4. 检查 /oc/img/ 是否能经 gateway 访问
```

LLM 不响应：

```text
1. 后台 LLM 供应商页面确认 API Key
2. 检查供应商 Base URL 和模型名
3. 查看 jobclaw-prod-api 日志
4. 检查服务器是否能访问模型供应商网络
```

## 最终上线判定

可以正式对外使用的最低标准：

```text
域名 HTTPS 正常
只有 80/443 对公网开放
后台和管理 API 有鉴权
生产数据在 Docker volume 或云服务中持久化
备份脚本跑通
恢复流程有文档
日志能定位 API / 网关 / 数据库问题
/actuator 只暴露 health
```

满足以上条件后，JobClaw 可以作为个人秋招项目真实上线运用。
