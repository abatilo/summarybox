FROM openjdk:8-jdk as builder

WORKDIR /source
COPY . /source

RUN ./gradlew shadowJar

FROM openjdk:8-jdk
WORKDIR /app
COPY --from=builder /source/summarybox-service/summarybox.jar /source/local.yaml /source/vectors-phrase.bin ./

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "summarybox.jar", "server", "local.yaml"]
