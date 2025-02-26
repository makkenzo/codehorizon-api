FROM amazoncorretto:21-alpine-jdk

WORKDIR /app

COPY . .
RUN ./gradlew clean build -x test

EXPOSE 8080

CMD ["java", "-jar", "-Dspring.profiles.active=prod", "build/libs/codehorizon-0.0.1-SNAPSHOT.jar"]