# Build Tools

This directory collects project launch, build, and Docker helper entry points.

Some files intentionally stay in the repository root because external tools expect them there:

- `mvnw` and `mvnw.cmd`: Maven Wrapper convention.
- `.mvn/`: Maven Wrapper configuration.
- `pom.xml`: Maven project root.
- `Dockerfile`: the legacy all-in-one image entry point.

Docker Compose files live in `docker/compose/`. The scripts in this directory are convenience wrappers around those environment-specific Compose entry points.

## Maven

The Maven Wrapper is the project Maven entry point. It is pinned by `.mvn/wrapper/maven-wrapper.properties` to Apache Maven 3.9.16 and downloads that distribution when the wrapper cache is empty.

Build with the development profile:

```bash
./build/mvnw package -DskipTests -Pdev
```

On Windows:

```powershell
.\build\mvnw.cmd package -DskipTests -Pdev
```

Build with the production profile:

```bash
./build/mvnw package -DskipTests -Pprod
```

Local Maven builds use a project-scoped Maven repository by default:

```text
workspace/.m2/repository
```

This avoids permission problems from machine-level Maven installations and keeps dependency cache behavior consistent across `mvnw`, Docker, and local builds.

For a machine-wide fix, configure Maven's `localRepository` in `%USERPROFILE%\.m2\settings.xml` or Maven's `conf/settings.xml` to a writable user directory, for example:

```xml
<localRepository>C:\Users\your-name\.m2\repository</localRepository>
```

The Docker helper scripts reuse the host Maven repository when it exists. On Windows they set `MAVEN_LOCAL_REPO` to:

```text
%USERPROFILE%\.m2\repository
```

You can override it before starting Docker:

```powershell
$env:MAVEN_LOCAL_REPO = "D:\maven-repository"
.\build\docker-split.ps1
```

## Docker

Start local H2 mode:

```powershell
.\build\docker-h2.ps1
```

Start MySQL mode:

```powershell
.\build\docker-mysql.ps1
```

MySQL mode starts only MySQL and the API service. Optional middleware stays out of the local baseline unless a task needs it.

Start MySQL + MinIO mode:

```powershell
.\build\docker-mysql-minio.ps1
```

Start split frontend/backend mode:

```powershell
.\build\docker-split.ps1
```

Split mode runs Spring Boot as the API service, serves the exported Next.js frontend from a separate web container, and uses an Nginx gateway as the single browser entry point.
It starts MySQL, the API service, the frontend web container, and the gateway.

Kafka is the recommended message queue for JobClaw because the platform is event-heavy: Agent execution traces, LLM audit events, job crawling events, notifications, and future analytics all fit Kafka's event-stream model.

Start the single-server production baseline:

```powershell
Copy-Item .env.production.example .env.production
.\build\docker-prod.ps1
```

Production mode uses `docker/compose/compose.prod.yml`. It exposes only the gateway port, keeps MySQL/Redis/Kafka/Elasticsearch/MinIO internal, uses named volumes for persistent data, and enables health checks plus log rotation.

By default, the split gateway uses:

```text
http://localhost:8088/
```

Override the gateway port when needed:

```powershell
$env:JOBCLAW_GATEWAY_PORT = "18088"
.\build\docker-split.ps1
```

## Jar Launch

For non-Docker Linux deployments, use:

```bash
./build/launch.sh start
./build/launch.sh restart
./build/launch.sh stop
```
