FROM openjdk:21-jdk-alpine

ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="-Dhttp.proxyHost=webproxy.nais -Dhttps.proxyHost=webproxy.nais -Dhttp.proxyPort=8088 -Dhttps.proxyPort=8088 -Dhttp.nonProxyHosts=localhost|127.0.0.1|10.254.0.1|*.local|*.adeo.no|*.nav.no|*.aetat.no|*.devillo.no|*.oera.no|*.nais.io|*.aivencloud.com|*.intern.dev.nav.no"

WORKDIR /app

# Copy built JAR from the server module
COPY server/build/libs/*.jar app.jar

EXPOSE 8080

# Create non-root user
RUN adduser -D appuser
USER appuser

CMD ["java", "-jar", "app.jar"]
