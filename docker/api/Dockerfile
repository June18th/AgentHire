# syntax=docker/dockerfile:1.7

FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .

# In split deployment the frontend is served by jobclaw-web/nginx, not by Spring Boot.
RUN rm -rf backend/src/main/resources/static && mkdir -p backend/src/main/resources/static

ARG MAVEN_PROFILE=prod

RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=bind,from=maven-local-repo,source=.,target=/host-m2,ro \
    mkdir -p /root/.m2/repository; \
    cp -an /host-m2/. /root/.m2/repository/ 2>/dev/null || true; \
    mvn -B -ntp -P${MAVEN_PROFILE} -pl backend -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Shanghai"

RUN mkdir -p /app/workspace/storage

COPY --from=build /workspace/backend/target/backend-*.jar /app/app.jar

EXPOSE 8087 8099

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
