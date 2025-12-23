# 智能图片管理网站

## 项目简介

这是一个基于B/S架构的智能图片管理网站，提供图片上传、分类、检索和管理功能。项目使用Spring Boot作为后端，React作为前端，MySQL作为数据库。

## 功能特性

### 基本功能

1. **用户管理**
   - 用户注册（用户名、密码至少6字节，邮箱格式验证）
   - 用户登录（JWT认证）
   - 用户名和邮箱唯一性验证

2. **图片上传**
   - 支持PC和手机浏览器上传
   - 支持多种图片格式（JPEG、PNG、GIF、WEBP等）
   - 单文件最大20MB

3. **EXIF信息提取**
   - 自动提取拍摄时间、相机信息、GPS位置等
   - 自动提取图片分辨率

4. **标签管理**
   - 自动从EXIF信息创建标签
   - 支持自定义标签
   - AI自动标签（增强功能）

5. **缩略图生成**
   - 自动生成300x300、600x600、1200x1200三种尺寸的缩略图

6. **图片搜索**
   - 支持关键词搜索
   - 支持时间范围搜索
   - 支持标签筛选

7. **图片展示**
   - 图片库浏览
   - 图片详情查看
   - 响应式设计，适配移动端

8. **图片管理**
   - 图片删除功能
   - 图片信息查看

9. **移动端适配**
   - 响应式设计
   - 支持手机浏览器和微信内置浏览器

### 增强功能

1. **AI图片分析**
   - 自动识别图片内容并生成标签（风景、人物、动物等）

2. **MCP接口**
   - 支持自然语言对话方式检索图片

## 技术栈

### 后端
- Spring Boot 3.2.0
- Java 17
- MySQL 8.0
- JWT认证
- Thumbnailator（缩略图生成）
- Metadata Extractor（EXIF信息提取）

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

## 快速开始

### 前置要求

- Docker和Docker Compose
- 或Java 17、Maven、Node.js 18+、MySQL 8.0

### 使用Docker Compose（推荐）

1. 克隆项目到本地

2. 启动所有服务
```bash
docker-compose up -d
```

3. 访问应用
   - 前端：http://localhost:3000
   - 后端API：http://localhost:8080/api

### 本地开发

#### 后端

1. 启动MySQL数据库
```bash
# 使用Docker启动MySQL
docker run -d \
  --name mysql \
  -e MYSQL_ROOT_PASSWORD=password \
  -e MYSQL_DATABASE=image_db \
  -p 3306:3306 \
  mysql:8.0
```

2. 执行数据库初始化脚本
```bash
mysql -u root -p image_db < database/init.sql
```

3. 配置`backend/src/main/resources/application.yml`中的数据库连接

4. 启动后端
```bash
cd backend
mvn spring-boot:run
```

#### 前端

1. 安装依赖
```bash
cd frontend
npm install
```

2. 启动开发服务器
```bash
npm run dev
```

3. 访问 http://localhost:3000

## API文档

### 认证接口

#### 用户注册
```
POST /api/auth/register
Content-Type: application/json

{
  "username": "string",
  "password": "string",
  "email": "string"
}
```

#### 用户登录
```
POST /api/auth/login
Content-Type: application/json

{
  "username": "string",
  "password": "string"
}
```

### 图片接口

#### 上传图片
```
POST /api/images/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: 图片文件
tags: 标签数组（可选）
```

#### 获取图片列表
```
GET /api/images?page=0&size=20
Authorization: Bearer {token}
```

#### 获取图片详情
```
GET /api/images/{id}
Authorization: Bearer {token}
```

#### 获取缩略图
```
GET /api/images/{id}/thumbnail?size=600
Authorization: Bearer {token}
```

#### 搜索图片
```
GET /api/images/search?keyword=xxx&startDate=xxx&endDate=xxx&page=0&size=20
Authorization: Bearer {token}
```

#### 删除图片
```
DELETE /api/images/{id}
Authorization: Bearer {token}
```

### MCP接口（增强功能）

#### 自然语言搜索
```
POST /api/mcp/search
Authorization: Bearer {token}
Content-Type: application/json

{
  "query": "找出上周拍的风景照片"
}
```

## 数据库设计

### 表结构

- `users`: 用户表
- `images`: 图片表
- `tags`: 标签表
- `image_tags`: 图片标签关联表

详细表结构见`database/init.sql`

## 开发说明

### 代码规范

- 后端使用Java标准命名规范
- 前端使用React函数组件和Hooks
- API响应统一使用`ApiResponse`格式

### 测试

```bash
# 后端测试
cd backend
mvn test

# 前端测试（如果配置了）
cd frontend
npm test
```

## 部署

### 生产环境部署

1. 修改`docker-compose.yml`中的环境变量
2. 构建并启动
```bash
docker-compose up -d --build
```

3. 查看日志
```bash
docker-compose logs -f
```

## 许可证

本项目为课程作业项目。

## 作者

- 姓名：李禹衡
- 学号：3230105685
- 专业：计算机科学与技术

## 更新日志

### v1.0.0 (2025-11-21)
- 初始版本
- 实现基本功能
- 实现增强功能（AI标签、MCP接口）

