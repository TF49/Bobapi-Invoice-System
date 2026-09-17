# 后端 Dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制 pom.xml 并下载依赖（利用 Docker 缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装 wget 用于健康检查
RUN apk add --no-cache wget

# 复制构建产物
COPY --from=builder /app/target/*.jar app.jar

# 创建上传目录
RUN mkdir -p /app/uploads

# 暴露端口
EXPOSE 9090

# 健康检查（使用 Spring Boot Actuator 健康端点）
HEALTHCHECK --interval=30s --timeout=3s --start-period=120s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:9090/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
