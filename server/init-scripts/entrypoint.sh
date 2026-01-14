#!/bin/sh
set -e

# Export App ENV
[ -f /init-scripts/export_app_envs.sh ] && . /init-scripts/export_app_envs.sh

# Export serviceuser secrets
DIR="/var/run/secrets/nais.io/srvuser"
if [ -d "$DIR" ]; then
  for FILE in "$DIR"/*; do
    VAR=$(basename "$FILE" | tr '[:lower:]' '[:upper:]')
    VALUE=$(cat "$FILE")
    export "$VAR=$VALUE"
    echo "- exporting $VAR"
  done
fi

# Start Java
exec java $JAVA_OPTS -jar /app/app.jar
