# Core reference app

Build with Java 21 and Maven, then start the standalone core profile:

```sh
mvn -pl qqq-all-app -am verify
java -jar qqq-all-app/target/qqq-all-app-4.1.0-SNAPSHOT.jar
```

The core app binds to `127.0.0.1` and serves the Next dashboard at `http://127.0.0.1:8080/` and health at `/health`. Set `QQQ_ALL_PORT` to change the port and `QQQ_ALL_DATA_DIR` to change the default `./data` directory. On first start it seeds customers and orders in H2, order lines in SQLite, and products as JSON files. Artemis runs in the same JVM; inserts and updates to `order` publish to the `orderEvents` topic and trigger `syncOrder`.

Core uses mock authentication. The `sessionId` cookie selects the two fixed demo identities: `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa` for Admin and `dddddddd-dddd-4ddd-8ddd-dddddddddddd` for Demo User. Any other session ID uses Demo User. These identities are for local demonstrations; role-based permissions are wired in task C5.
