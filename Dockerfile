FROM openjdk:8-jdk

WORKDIR /source
COPY . /source

RUN ./gradlew shadowJar

FROM openjdk:8-jdk
WORKDIR /app
COPY --from=0 /source/summarybox-service/summarybox.jar /source/local.yaml /source/vectors-phrase.bin ./

CMD java -jar summarybox.jar server local.yaml
EXPOSE 8080
