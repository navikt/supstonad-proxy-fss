# Supstonad-proxy

Proxy for å tillate kommunikasjon med mellom GCP og on-prem tjenester
Denne skal sikre tilgang for supstonad i GCP mot Tilbakekreving og Simuleringtjenestene i oppdrag/økonomi.



Bygge dockerimage lokalt:
docker build -f Dockerfile -t supstonad-proxy-fss:local ./server
men kjør: ./gradlew build først
kjøre image og sjekk output:
docker run -it -p 8080:8080 --name supstonad-local supstonad-proxy-fss:local
Får du `docker: Error response from daemon: client version 1.52 is too new. Maximum supported API version is 1.43`
set #export DOCKER_API_VERSION=1.43
Kjører den allerede kjør:
docker rm supstonad-local