FROM maven:3.6.0-jdk-11
WORKDIR /usr/src/app
COPY pom.xml .
RUN mvn \
  --batch-mode \
  --errors \
  --strict-checksums \
  --threads 1C \
  org.apache.maven.plugins:maven-dependency-plugin:3.0.2:go-offline

COPY src ./src

RUN find .

RUN mvn \
  --batch-mode \
  --errors \
  --strict-checksums \
  --threads 1C \
  verify

FROM openjdk:11.0.2-jre
COPY --from=0 /usr/src/app/target/*.jar /jars/

CMD ["java", "-classpath", "/jars/*", "org.cru.globalreg.renotifier.App"]
