#!/usr/bin/env bash
set -euo pipefail

printf '%s' "${MONGO_KEYFILE}" | base64 > /data/configdb/keyfile
chmod 400 /data/configdb/keyfile
chown mongodb:mongodb /data/configdb/keyfile
exec docker-entrypoint.sh mongod --replSet "${MONGO_REPLICA_SET}" --bind_ip_all --auth --keyFile /data/configdb/keyfile
