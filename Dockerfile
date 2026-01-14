FROM eclipse-temurin:21-jre

ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="-Dhttp.proxyHost=webproxy.nais -Dhttps.proxyHost=webproxy.nais -Dhttp.proxyPort=8088 -Dhttps.proxyPort=8088 -Dhttp.nonProxyHosts=localhost|127.0.0.1|10.254.0.1|*.local|*.adeo.no|*.nav.no|*.aetat.no|*.devillo.no|*.oera.no|*.nais.io|*.aivencloud.com|*.intern.dev.nav.no"

WORKDIR /app

# Copy the built server JAR
COPY build/libs/*-*.jar ./
COPY build/libs/*.jar ./
COPY init-scripts/* /init-scripts/
RUN chmod +x /init-scripts/*.sh

RUN apt-get update && apt-get install -y bash
EXPOSE 8080



ENTRYPOINT ["/bin/sh", "/init-scripts/entrypoint.sh"]