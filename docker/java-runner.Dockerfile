FROM amazoncorretto:17-alpine-jdk

WORKDIR /usr/src/app

COPY runners/lib/jackson-core.jar .
COPY runners/lib/jackson-annotations.jar .
COPY runners/lib/jackson-databind.jar .

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser