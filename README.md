# 智能图片管理网站
> 项目git日志存放在gitlog.txt文件中
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
   - 支持自定义标签（可添加、编辑、删除）
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

## 运行与测试

### 前置要求

- Docker和Docker Compose
- 或Java 17、Maven、Node.js 18+、MySQL 8.0

### 使用Docker Compose（推荐）

1. 克隆项目到本地

2. 启动所有服务
```bash
docker-compose up -d
```
或运行`start.bat`脚本

3. 访问应用
   - 前端：http://localhost:3000
   - 后端API：http://localhost:8080/api