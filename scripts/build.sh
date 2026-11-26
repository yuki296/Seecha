#!/bin/sh
set -eu
cd "$(dirname "$0")/.."

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    export JAVA_HOME
fi

if [ -n "${MAVEN_BIN:-}" ]; then
    exec "$MAVEN_BIN" package "$@"
fi
if command -v mvn >/dev/null 2>&1; then
    exec mvn package "$@"
fi
bundled_maven='/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn'
if [ -x "$bundled_maven" ]; then
    exec "$bundled_maven" package "$@"
fi
echo 'Maven을 설치하거나 MAVEN_BIN에 Maven 실행 파일 경로를 지정하세요.' >&2
exit 1
