FROM eclipse-temurin@sha256:b3054a20e1a851574cdb1692dd0db5310c647e62214a2a7abecbec1b9e3ae8f1
RUN groupadd --gid 10001 dragon && useradd --uid 10001 --gid dragon --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin dragon
WORKDIR /app
COPY --chown=root:root app.jar /app/app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
