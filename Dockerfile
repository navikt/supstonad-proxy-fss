FROM ghcr.io/navikt/baseimages/temurin:21

ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="-Dhttp.proxyHost=webproxy.nais -Dhttps.proxyHost=webproxy.nais -Dhttp.proxyPort=8088 -Dhttps.proxyPort=8088 -Dhttp.nonProxyHosts=localhost|127.0.0.1|10.254.0.1|*.local|*.adeo.no|*.nav.no|*.aetat.no|*.devillo.no|*.oera.no|*.nais.io|*.aivencloud.com|*.intern.dev.nav.no"

WORKDIR /app

# Copy the built server JAR
COPY server/build/libs/*.jar app.jar

EXPOSE 8080
USER nonroot
# Run the JAR
CMD ["java", "-jar", "app.jar"]