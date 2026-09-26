#!/usr/bin/env bash
set -euo pipefail

for attempt in {1..30}; do
   if mongosh --host mongo --quiet -u "${MONGO_USER}" -p "${MONGO_PASSWORD}" --authenticationDatabase admin \
      --eval 'try { rs.status().ok } catch (e) { if (e.codeName === "NotYetInitialized") { rs.initiate({_id: process.env.MONGO_REPLICA_SET, members: [{_id: 0, host: "mongo:27017"}]}).ok } else { throw e } }' | grep -q 1; then
      exit 0
   fi
   sleep 2
done
echo 'Mongo replica set did not initialize' >&2
exit 1
