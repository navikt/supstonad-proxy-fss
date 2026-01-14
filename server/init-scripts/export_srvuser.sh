#!/usr/bin/env bash

DIR=/var/run/secrets/nais.io/srvuser

echo "Attempting to export serviceuser from $DIR if it exists"

if [ -d "$DIR" ]; then
    for FILE in "$DIR"/*; do
        BASENAME=$(basename "$FILE")
        VALUE=$(cat "$FILE")
        export "$BASENAME=$VALUE"
        echo "- exporting $BASENAME"
        echo "   -> value: '$VALUE'"
        env | grep -i "$BASENAME"
    done
fi