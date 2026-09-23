# Iudex Server

Contest server for the Iudex judge: contests, problems, registrations, and
submission grading.

## Running it

```bash
./gradlew bootRun
```

No configuration is required for a local run. Any secret left unset is
generated for that process, and the server logs a warning saying so.

On first start, when the database holds no administrator, one is created and
its password is logged **once**:

```
Created the first administrator, because none existed.
  username: admin
  password: sPJwMH22fwiYH1wKVJ2RxdvxzuUcjucv
```

Registration through `/auth/register` always produces a contestant, so this
administrator is the only way to make the first contestmaster:

```bash
curl -X PATCH localhost:8080/api/users/{userId}/role \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"role":"CONTESTMASTER"}'
```

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | in-memory H2 | Point at a real database for anything that must survive a restart. |
| `DB_USERNAME` / `DB_PASSWORD` | `sa` / empty | |
| `JWT_SECRET` | generated per run | Base64, at least 32 bytes. Tokens stop working on restart while generated. |
| `JWT_EXPIRATION_MS` | `86400000` | Token lifetime. |
| `IUDEX_TESTCASE_SECRET` | generated per run | Base64, 32 bytes. **Must match the judging client.** |
| `IUDEX_STORAGE_ROOT` | `./storage` | Where problem content and test cases live. |
| `IUDEX_ADMIN_USERNAME` | `admin` | Bootstrap administrator, used only when no administrator exists. |
| `IUDEX_ADMIN_PASSWORD` | generated and logged | As above. |

Generate a secret with:

```bash
openssl rand -base64 32
```

A deployment must set `JWT_SECRET` and `IUDEX_TESTCASE_SECRET`. A malformed or
too-short value fails at startup rather than at the first request.

## How judging works

The server never runs contestant code, and never takes a score from the client.

1. `GET /api/problems/{id}/tests` returns every test case input, sample and
   hidden alike, encrypted with AES-256-GCM under `IUDEX_TESTCASE_SECRET`.
   Expected outputs are not in the response type at all.
2. The client decrypts the inputs, runs the contestant's program, and encrypts
   what the program printed.
3. `POST /api/submissions` sends those outputs. The server decrypts them,
   compares each against the expected output it holds in file storage, and
   derives `passedTestCaseCount` itself.

Comparison ignores trailing whitespace, line ending style, and trailing blank
lines. Leading whitespace is significant.

The response reports how many test cases passed, never which ones — naming the
failures would describe the shape of the hidden data.

**What the encryption does and does not do.** The client must hold the key to
run the tests, so this stops test data being read straight off the API, and
stops it being harvested from the wire — a correct client's outputs are the
answer key. It does not stop a contestant who extracts the key from the client.
The defence that does not depend on the client is that expected outputs never
leave the server.

`clientDurationMs` on a submission is untrusted telemetry. It is stored for
display and never influences a verdict or a ranking; `receivedAt` comes from
the server clock.

## Storage layout

The database holds identity and ordering only. Problem content lives on disk:

```
{IUDEX_STORAGE_ROOT}/{contestId}/{problemId}/
    problem.json           title, time and memory limits, score
    statement.txt
    template.txt
    sample_testcases.json
    hidden_testcases.json
```

## Tests

```bash
./gradlew test
```

## Development profile

The defaults are safe to run anywhere: an in-memory database and no console.
Nothing is written to disk, so no database file is left holding credentials.

For local work you usually want persistence and a database shell:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

That profile, and only that profile, switches to a file-backed database at
`./data/devdb` and enables the H2 console at `/h2-console`. The console is an
unauthenticated database shell, so the server logs a warning whenever it is on.

**Never activate `dev` on a deployed server.** With the console disabled the
filter chain carries no exemption for its path at all, and requests to it are
rejected like any other unauthenticated request.

## Validation

`POST /auth/register` requires a username of 3-50 characters made of letters,
digits, dot, underscore or hyphen, and a password of 8-72 characters (BCrypt
reads no further than 72 bytes). Failures return `400` naming each offending
field.

`POST /auth/login` checks only that both fields are present. It deliberately
does not apply the registration rules: an account created under earlier rules
must still be able to sign in, and rejecting a password for being too short
would describe the policy to whoever is guessing.
