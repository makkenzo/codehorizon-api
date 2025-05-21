FROM amazoncorretto:17-alpine-jdk

WORKDIR /usr/src/app

COPY src/main/resources/runners/lib/jackson-core.jar .
COPY src/main/resources/runners/lib/jackson-annotations.jar .
COPY src/main/resources/runners/lib/jackson-databind.jar .

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser