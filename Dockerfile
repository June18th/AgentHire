# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS ui-build

WORKDIR /workspace/ui-react
ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"

COPY ui-react/package.json ui-react/pnpm-lock.yaml ./
RUN corepack enable && corepack prepare pnpm@10.14.0 --activate && pnpm install --frozen-lockfile

COPY ui-react/ ./
ARG NEXT_PUBLIC_API_BASE_URL=
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
RUN pnpm run build:static

FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .
RUN rm -rf backend/src/main/resources/static
COPY --from=ui-build /workspace/ui-react/.next-build ./backend/src/main/resources/static

ARG MAVEN_PROFILE=dev
# AI-GENERATED: 与 plugins/playwright/pom.xml 中 playwright.version 保持一致
ARG PLAYWRIGHT_VERSION=1.52.0

RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=bind,from=maven-local-repo,source=.,target=/host-m2,ro \
    mkdir -p /root/.m2/repository; \
    cp -an /host-m2/. /root/.m2/repository/ 2>/dev/null || true; \
    mvn -B -ntp -P${MAVEN_PROFILE} -pl backend -am package -DskipTests; \
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
