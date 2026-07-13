# syntax=docker/dockerfile:1.7

FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .

# In split deployment the frontend is served by jobclaw-web/nginx, not by Spring Boot.
RUN rm -rf backend/src/main/resources/static && mkdir -p backend/src/main/resources/static

ARG MAVEN_PROFILE=prod
# AI-GENERATED: 与 plugins/playwright/pom.xml 中 playwright.version 保持一致
ARG PLAYWRIGHT_VERSION=1.52.0

RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=bind,from=maven-local-repo,source=.,target=/host-m2,ro \
    mkdir -p /root/.m2/repository; \
    cp -an /host-m2/. /root/.m2/repository/ 2>/dev/null || true; \
    mvn -B -ntp -P${MAVEN_PROFILE} -pl backend -am package -Dmaven.test.skip=true; \
    mkdir -p /playwright-cli; \
    find /root/.m2/repository/com/microsoft/playwright -name '*.jar' -exec cp -t /playwright-cli {} +

FROM eclipse-temurin:21-jre-noble

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Shanghai"
# AI-GENERATED: 固定浏览器安装路径，避免依赖容器用户主目录
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

RUN mkdir -p /app/workspace/storage

# AIDEV-NOTE: 预装 headless Chromium 及系统依赖；未开启 Playwright 时不影响启动
COPY --from=build /playwright-cli /playwright-cli
RUN java -cp '/playwright-cli/*' com.microsoft.playwright.CLI install --with-deps chromium \
    && rm -rf /playwright-cli \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/backend/target/backend-*.jar /app/app.jar

EXPOSE 8087 8099

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
