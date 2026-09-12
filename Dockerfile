# 构建阶段：使用 BuildKit 缓存挂载持久化 Maven 本地仓库，
# 更新代码重新构建镜像时无需重新下载依赖。
# 可通过 --build-arg BUILDER_IMAGE=... 替换基础构建镜像。
ARG BUILDER_IMAGE=maven:3.9.9-eclipse-temurin-21-alpine
FROM ${BUILDER_IMAGE} AS build
WORKDIR /workspace

# 先复制全部 POM 与 Lombok 配置解析依赖，配合缓存挂载实现依赖层缓存
COPY pom.xml ./
COPY lombok.config ./
COPY knowledge-agent-common/pom.xml knowledge-agent-common/pom.xml
COPY knowledge-agent-ai/pom.xml knowledge-agent-ai/pom.xml
COPY knowledge-agent-knowledge/pom.xml knowledge-agent-knowledge/pom.xml
COPY knowledge-agent-conversation/pom.xml knowledge-agent-conversation/pom.xml
COPY knowledge-agent-mcpserver/pom.xml knowledge-agent-mcpserver/pom.xml
COPY knowledge-agent-app/pom.xml knowledge-agent-app/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl knowledge-agent-app -am dependency:go-offline -DskipTests || true

# 再复制源码编译打包，源码变更时依赖层仍然命中缓存
COPY knowledge-agent-common/src knowledge-agent-common/src
COPY knowledge-agent-ai/src knowledge-agent-ai/src
COPY knowledge-agent-knowledge/src knowledge-agent-knowledge/src
COPY knowledge-agent-conversation/src knowledge-agent-conversation/src
COPY knowledge-agent-mcpserver/src knowledge-agent-mcpserver/src
COPY knowledge-agent-app/src knowledge-agent-app/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl knowledge-agent-app -am clean package -DskipTests

# 运行阶段：仅携带 JRE 与可执行 Jar
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/storage
COPY --from=build /workspace/knowledge-agent-app/target/knowledge-agent-app-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
