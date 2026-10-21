# 智能图片管理网站
> 项目git日志存放在gitlog.txt文件中
## 项目简介

这是一个基于B/S架构的图片资产管理网站，提供图片上传、分类、检索和管理功能。项目使用 Spring Boot 模块化单体、React、MySQL、Redis 和 RabbitMQ。

## 功能特性

### 基本功能

1. **用户管理**
   - 用户注册（用户名、密码至少6字节，邮箱格式验证）
   - 用户登录（JWT认证）
   - 用户名和邮箱唯一性验证

2. **图片上传**
   - 支持PC和手机浏览器上传
   - 支持 JPEG、PNG、GIF
   - 单文件最大100MB（应用限制已实现；100MB 上传堆占用尚未实测）

3. **EXIF信息提取**
   - 提取图像尺寸和基本文件信息；API 不展示 GPS/位置字段

4. **标签管理**
   - 自动从EXIF信息创建标签
   - 支持自定义标签（可添加、编辑、删除）
   - 当前未配置视觉识别模型，不生成 AI 标签

5. **缩略图生成**
   - 自动生成300x300、600x600、1200x1200三种尺寸的缩略图

6. **图片搜索**
   - 支持关键词搜索
   - 支持时间范围搜索
   - 支持标签筛选

7. **图片展示**
   - 图片库浏览
   - 图片详情查看
   - 轮播展示（选择多张图片自动播放）
   - 响应式设计，适配移动端

8. **图片管理**
   - 图片编辑功能（裁剪、色调调整）
   - 图片删除功能
   - 图片信息查看
   - 标签编辑功能

9. **移动端适配**
   - 响应式设计
   - 支持手机浏览器和微信内置浏览器

### 增强功能

1. **自然语言规则检索**
   - 规则解析关键词和时间条件；该 REST API 不是 MCP 协议服务

## 技术栈

### 后端
- Spring Boot 4.1.1
- Java 17
- MySQL 8.0
- Redis（列表与检索缓存）
- RabbitMQ（缩略图异步任务与 Outbox 发布）
- Flyway（数据库版本迁移）
- Actuator + Micrometer Prometheus
- JWT认证
- Thumbnailator（缩略图生成）
- 规则化 EXIF 信息提取

### 前端
- React 18
- Vite
- Ant Design Mobile
- React Router
- Axios

### 部署
- Docker
- Docker Compose

## 项目结构

```
BS_design/
├── backend/                 # Spring Boot后端
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/zju/imagemanger/
│   │   │   │       ├── config/      # 配置类
│   │   │   │       ├── controller/  # 控制器
│   │   │   │       ├── service/     # 服务层
│   │   │   │       ├── repository/  # 数据访问层
│   │   │   │       ├── entity/      # 实体类
│   │   │   │       ├── dto/         # 数据传输对象
│   │   │   │       └── exception/   # 异常处理
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                # React前端
│   ├── src/
│   │   ├── pages/           # 页面组件
│   │   ├── context/         # Context
│   │   ├── utils/           # 工具函数
│   │   └── App.jsx
│   ├── package.json
│   ├── vite.config.js
│   └── Dockerfile
├── database/                # 数据库脚本
│   └── init.sql
├── docker-compose.yml       # Docker Compose配置
└── README.md
```

## 运行与测试

### 前置要求

- Docker Desktop和Docker Compose
- 或Java 17、Maven、Node.js 22+、MySQL 8.0

### 使用Docker Compose（推荐）

1. 复制 `.env.example` 为 `.env`，设置数据库、RabbitMQ 密码和 JWT 密钥。`JWT_SECRET` 至少32字节。

2. 启动所有服务
```bash
docker compose up -d --build
```
Windows 也可运行 `start.bat`。

可选 MinIO：在 `.env` 设置 `STORAGE_PROVIDER=minio`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`，再运行 `docker compose --profile minio up -d --build`。Compose 默认使用 `public.ecr.aws/y9w4h2u9/minio:RELEASE.2025-09-07T16-13-09Z`；可用 `MINIO_IMAGE` 指向可访问的兼容镜像。该镜像由第三方维护，仅用于隔离开发测试环境。先启动 MinIO profile，再启动后端；首次运行需在 MinIO Console 创建 `.env` 中配置同名的 `MINIO_BUCKET` bucket，并给应用凭据授予该 bucket 的读取、写入、删除和列举权限。应用每周扫描本地或 MinIO 孤儿对象，仅删除超过 24 小时、符合应用对象键格式且数据库无引用的对象。可用 `ORPHAN_MIN_AGE`、`ORPHAN_SCAN_INITIAL_DELAY` 和 `ORPHAN_SCAN_DELAY` 配置最小对象年龄、首次扫描延迟和扫描间隔；两个延迟默认值分别是 600000 ms 与 604800000 ms。默认仍使用本地存储。

可选监控：在 `.env` 设置 `GRAFANA_ADMIN_PASSWORD`，运行 `docker compose --profile monitoring up -d`。Grafana 地址为 `http://localhost:3001`，Prometheus 默认地址为 `http://localhost:9091`（可用 `PROMETHEUS_PORT` 改端口）；两者只绑定本机回环。账号为 `admin`，密码取自 `.env`。

数据库备份：运行 `powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1`。隔离恢复演练：运行 `powershell -ExecutionPolicy Bypass -File .\scripts\restore-to-test-db.ps1 -BackupFile .\backups\<备份文件名>.sql.gz`。恢复脚本只写入新建的测试库，不覆盖 `image_db`。

3. 访问应用
   - 前端：http://localhost:3000
   - 后端API：http://localhost:8080/api

服务端口默认只绑定本机回环地址。手机或其他设备访问时，应在受控网络中配置反向代理和 TLS；不要直接将数据库或应用端口暴露到公网。

图片原图、缩略图和自然语言检索现在都要求登录，并按图片所有者隔离。自然语言检索是规则解析 REST API，不是 MCP 协议服务。当前未配置视觉识别模型，系统不会伪造 AI 标签。

上传只接受有效 JPEG、PNG、GIF 文件，最大100MB、2000万像素。服务器端按实际文件签名识别类型；请求中的 MIME 和文件名不可信。

### API 与运维

- OpenAPI JSON：`http://localhost:8080/api/v3/api-docs`
- Swagger UI：`http://localhost:8080/api/swagger-ui/index.html`
- 健康状态：`http://localhost:8080/api/actuator/health`
- Prometheus 指标：`http://localhost:8080/api/actuator/prometheus`（需登录）
- 图片上传先写入数据库和 Outbox，再由 RabbitMQ Worker 生成缩略图；处理状态可在图片详情中查看。
- Compose 启动 API、独立 Worker、前端、MySQL、Redis 与 RabbitMQ；Worker 无宿主端口。仅前端、API 和 MySQL 发布到本机回环。
- API 容器负责 HTTP 与 Outbox 发布；Worker 容器只消费 RabbitMQ 并生成缩略图。二者共享上传目录、缩略图目录和配置，可分别扩容。
- 上传、详情、列表 API 均要求登录。列表支持 `/api/images/cursor?size=20` 游标分页。
- k6 压测脚本位于 `docs/performance/k6.js`。运行前配置 `BASE_URL`、专用测试账号 `K6_USERNAME`、`K6_PASSWORD`；脚本在 k6 内登录获取短期 JWT，不需要从外部传入 JWT。结果必须连同机器与数据规模记录；仓库不声明未实测的性能提升。
- Compose 默认启用 Redis 缓存；设置 `APP_CACHE_ENABLED=false` 并重建后端即可关闭缓存。k6 样本及限制见 `docs/performance/k6-results-2026-10-21.md`。

开发检查：`mvn -B -f backend/pom.xml verify` 和 `npm --prefix frontend ci && npm --prefix frontend run build`。Node.js 需 22+；`package-lock.json` 锁定前端依赖。数据库迁移由 `backend/src/main/resources/db/migration` 管理；不要用 `docker compose down -v`，它会删除数据库数据卷。

GitHub Actions 会运行后端测试、前端审计与构建、容器构建，并独立拉取 MinIO 镜像检查健康端点。仓库需在 **Settings → Secrets and variables → Actions** 配置 `NVD_API_KEY`，CI 才会运行 OWASP Java 依赖扫描；密钥只放 GitHub Secret，不要提交到仓库。

升级验收状态和未完成项见 `升级实施记录.md` 与 `docs/architecture/upgrade-status.md`。
