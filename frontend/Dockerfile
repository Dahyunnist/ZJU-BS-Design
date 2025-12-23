FROM node:18-alpine AS build

WORKDIR /app

# 复制package文件
COPY package.json package-lock.json* ./
RUN npm install

# 复制源代码并构建
COPY . .
RUN npm run build

# 运行阶段
FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]

