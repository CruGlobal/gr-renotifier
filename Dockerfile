FROM maven:3.6.0-jdk-11
WORKDIR /usr/src/app

# pull deps from cruglobal.jfrog.io
#COPY .m2/settings.xml /root/.m2/settings.xml

# cache basic maven dependencies
COPY .m2/empty.pom.xml ./pom.xml
RUN mvn \
  --batch-mode \
  --errors \
  --strict-checksums \
  --threads 1C \
  org.apache.maven.plugins:maven-dependency-plugin:3.0.2:go-offline

# cache all maven dependencies
COPY pom.xml .
RUN mvn \
  --batch-mode \
  --errors \
  --strict-checksums \
  --threads 1C \
  org.apache.maven.plugins:maven-dependency-plugin:3.0.2:go-offline

COPY src ./src

# build
RUN mvn \
  --batch-mode \
  --errors \
  --strict-checksums \
  --threads 1C \
  verify

FROM openjdk:11.0.2-jre
COPY --from=0 /usr/src/app/target/*.jar /jars/

ENTRYPOINT ["java", "-classpath", "/jars/*", "org.cru.globalreg.renotifier.Main"]
