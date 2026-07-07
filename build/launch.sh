#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
APP_DIR="${ROOT_DIR}/backend"
JAR_NAME="backend-0.0.1-SNAPSHOT.jar"
JAR_PATH="${APP_DIR}/target/${JAR_NAME}"
PID_FILE="${ROOT_DIR}/pid.log"
JAVA_BIN="${JAVA_HOME:-}/bin/java"

if [[ ! -x "${JAVA_BIN}" ]]; then
  JAVA_BIN="java"
fi

build() {
  cd "${ROOT_DIR}"
  ./mvnw -B -ntp -Pprod -pl backend -am package -DskipTests
}

run() {
  if [[ ! -f "${JAR_PATH}" ]]; then
    echo "Jar not found: ${JAR_PATH}"
    echo "Run: ./build/launch.sh build"
    exit 1
  fi

  if [[ -f "${PID_FILE}" ]] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
    echo "Application is already running, pid=$(cat "${PID_FILE}")"
    exit 0
  fi

  cd "${ROOT_DIR}"
  nohup "${JAVA_BIN}" \
    -server \
    -Dspring.devtools.restart.enabled=false \
    -Duser.timezone=Asia/Shanghai \
    -Xms1g -Xmx1g -Xmn256m \
    -jar "${JAR_PATH}" >/dev/null 2>&1 &

  echo $! > "${PID_FILE}"
  echo "Started JobClaw, pid=$(cat "${PID_FILE}")"
}

stop() {
  if [[ ! -f "${PID_FILE}" ]]; then
    echo "No pid file found."
    return
  fi

  pid="$(cat "${PID_FILE}")"
  if kill -0 "${pid}" 2>/dev/null; then
    kill "${pid}"
    echo "Stopped JobClaw, pid=${pid}"
  else
    echo "Process not running, stale pid=${pid}"
  fi
  rm -f "${PID_FILE}"
}

case "${1:-}" in
  build)
    build
    ;;
  start)
    build
    run
    ;;
  run)
    run
    ;;
  stop)
    stop
    ;;
  restart)
    stop
    build
    run
    ;;
  *)
    echo "Usage: $0 {build|start|run|stop|restart}"
    exit 1
    ;;
esac
