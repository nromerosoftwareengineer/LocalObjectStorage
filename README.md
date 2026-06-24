# Local Object Storage

Micronaut Java service for staged file uploads backed by PostgreSQL.

**Endpoints**

- `POST /v1/files/actions/createUpload`
- `PUT /v1/files/{fileId}?offset=0`
- `GET /v1/files/{fileId}?offset=0`
- `GET /v1/files`

**Run**

Set these environment variables if you are not using the defaults:

```bash
export JDBC_URL=jdbc:postgresql://localhost:5433/local_object_storage
export JDBC_USER=postgres
export JDBC_PASSWORD=postgres
```

The defaults assume your local PostgreSQL instance has a `postgres` role and a `local_object_storage` database. If your local setup uses a different user, set `JDBC_USER` and `JDBC_PASSWORD` before starting the app.

Start PostgreSQL with Docker Compose if you do not already have a local instance:

```bash
docker-compose up -d
```

That starts PostgreSQL on `localhost:5433` with:

- database: `local_object_storage`
- user: `postgres`
- password: `postgres`

Start the service:

```bash
mvn compile exec:java
```

Open `http://localhost:8080/` for a lightweight drag-and-drop upload client. It uses the same `createUpload` and `upload` APIs exposed by the service.

**Monitoring**

The app exposes Prometheus metrics at:

```bash
curl http://localhost:8080/prometheus
```

Start PostgreSQL, Prometheus, and Grafana:

```bash
docker-compose up -d
```

Then start the API:

```bash
mvn compile exec:java
```

Open:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

Grafana login:

- user: `admin`
- password: `admin`

The provisioned dashboard is named `FileController API` and tracks the `/v1/files` routes plus custom `file_controller_*` metrics for request counts, upload/download bytes, payload sizes, CPU, JVM memory, and threads.

**IDE import**

Open the repository root (`local-object-storage`), not the `src/` directory, and import `pom.xml` as a Maven project with JDK 17. The repo includes [.mvn/maven.config](/Users/noeromer/personal/local-object-storage/.mvn/maven.config:1), which points Maven at the project-local [maven-settings.xml](/Users/noeromer/personal/local-object-storage/maven-settings.xml:1) and `.m2/repository` cache so dependency resolution does not depend on a global corporate mirror configuration. If IntelliJ previously opened `src/` as its own project, remove the old IDE metadata and reimport from the root so Maven can own the module layout.

**Create upload**

```bash
curl -i \
  -X POST http://localhost:8080/v1/files/actions/createUpload \
  -H 'Content-Type: application/json' \
  -d '{
    "fileName": "hello.txt",
    "fileSize": 11,
    "fileType": "text/plain"
  }'
```

The response includes a `Location` header such as `/v1/files/{fileId}`.

**Upload bytes**

```bash
curl -i \
  -X PUT 'http://localhost:8080/v1/files/{fileId}?offset=0' \
  -H 'Content-Type: application/octet-stream' \
  --data-binary 'hello world'
```

**Download bytes**

```bash
curl -i http://localhost:8080/v1/files/{fileId}
```

**List files**

```bash
curl -s http://localhost:8080/v1/files
```
