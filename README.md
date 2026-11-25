# Voicenotes

Backend for turning screen + voice recordings into structured notes
(Kazakh, Russian, English and mixed kk/ru speech).

Status: week 1. The REST API for recording sessions and chunk metadata works.
Transcription, Kafka, auth and deployment come in the following weeks.

## Stack

Java 21 · Spring Boot 4.2 · Spring Data JPA / Hibernate · PostgreSQL 16 · Flyway · springdoc-openapi (Swagger UI)

## Run

```bash
docker compose up -d          # PostgreSQL on localhost:5436
./mvnw spring-boot:run        # API on localhost:8080
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

DB settings can be overridden with `DB_URL`, `DB_USER`, `DB_PASSWORD`.

## API

| Method | Path | What it does |
|---|---|---|
| POST | `/api/sessions` | Create a session (`title`, `language`: KK, RU, EN, MIXED, AUTO) |
| GET | `/api/sessions?status=&page=&size=` | List sessions, newest first, with chunk count and total duration |
| GET | `/api/sessions/{id}` | One session |
| PATCH | `/api/sessions/{id}` | Change title and/or language |
| POST | `/api/sessions/{id}/finish` | Upload finished: UPLOADING → PROCESSING |
| DELETE | `/api/sessions/{id}` | Delete the session and its chunks |
| POST | `/api/sessions/{id}/chunks` | Register a chunk. Requires the `Idempotency-Key` header |
| GET | `/api/sessions/{id}/chunks` | Chunks in recording order |

Errors use RFC 9457 `application/problem+json`: 400 validation (with a list of fields), 404, 409.

### Session status

```
CREATED --first chunk--> UPLOADING --finish--> PROCESSING --> COMPLETED / FAILED
```

Transitions live only in `RecordingSession`, so an invalid one is rejected with 409 wherever it comes from.

### Idempotent chunk upload

The client generates one key per chunk and reuses it on retry:

- first request → `201 Created`
- same key, same body → `200 OK` with the chunk saved the first time (no duplicate)
- same key, different body → `409`
- new key, sequence number already taken → `409`

Final protection is in the database: `UNIQUE (session_id, idempotency_key)` and `UNIQUE (session_id, sequence_number)`.

## Tests

```bash
./mvnw test     # needs Docker running: Testcontainers starts PostgreSQL 16 itself
```

| Level | Class | What it checks |
|---|---|---|
| Unit | `RecordingSessionTest` | Status transitions |
| Unit + Mockito | `ChunkServiceTest` | Idempotency rules: new, retry, different body, taken sequence number |
| Web slice (`@WebMvcTest`) | `SessionControllerTest`, `ChunkControllerTest` | HTTP contract: statuses, `Location`, problem+json errors with field lists |
| JPA slice + Testcontainers | `ChunkRepositoryTest` | GROUP BY stats, UNIQUE/CHECK constraints, `ON DELETE CASCADE` on real Postgres |
| Full app + Testcontainers | `ApiIntegrationTest` | End-to-end lifecycle over HTTP; 8 parallel retries of one chunk create exactly one row; N+1 guard (≤ 3 SQL statements for a page of 20 sessions) |

CI (`.github/workflows/ci.yml`) runs `./mvnw verify` on every push and pull request.

Known limitation, covered by a test: when parallel retries of the same chunk race, one gets 201 and
the others get 409 instead of 200. No duplicate is ever stored.

## Design notes

- The schema is owned by Flyway (`db/migration`). Hibernate runs with `ddl-auto=validate`.
- `open-in-view=false`: lazy loading outside a transaction fails loudly instead of firing hidden queries.
- `Chunk → RecordingSession` is `LAZY`. There is no `@OneToMany` collection on the session.
- The session list fetches chunk statistics for the whole page with one `GROUP BY` query, which avoids N+1.
- `@Version` on the session: concurrent updates get 409 instead of a lost update.
