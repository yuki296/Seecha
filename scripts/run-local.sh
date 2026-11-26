#!/bin/sh
set -eu
cd "$(dirname "$0")/.."

CATALINA_HOME="${CATALINA_HOME:-/Applications/apache-tomcat-11.0.25}"
if [ ! -r "$CATALINA_HOME/bin/catalina.sh" ]; then
    echo 'CATALINA_HOME에 Tomcat 11 설치 경로를 지정하세요.' >&2
    exit 1
fi

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    export JAVA_HOME
fi

./scripts/build.sh "$@"
CATALINA_BASE="$PWD/target/local-tomcat"
export CATALINA_HOME CATALINA_BASE
mkdir -p "$CATALINA_BASE"/conf "$CATALINA_BASE"/logs "$CATALINA_BASE"/temp \
    "$CATALINA_BASE"/webapps "$CATALINA_BASE"/work
cp -R "$CATALINA_HOME/conf/." "$CATALINA_BASE/conf/"
cat > "$CATALINA_BASE/conf/server.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<Server port="-1">
    <Service name="Catalina">
        <Connector address="127.0.0.1" port="8081" protocol="HTTP/1.1" />
        <Engine name="Catalina" defaultHost="localhost">
            <Host name="localhost" appBase="webapps" unpackWARs="false" autoDeploy="false" />
        </Engine>
    </Service>
</Server>
XML
cp target/seecha.war "$CATALINA_BASE/webapps/seecha.war"
exec sh "$CATALINA_HOME/bin/catalina.sh" run
