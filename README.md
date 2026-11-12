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

## Contest master page

Open the server's root URL (e.g. `http://localhost:8080/`) in a browser and log
in as a contestmaster (or administrator). The page lists the contests you
created and lets you create, edit and delete contests, add or update problems
by uploading a `.json` file (statement, limits, templates, sample and hidden
test cases, reference solution -- the format is shown on the page), and see
participants, submissions and each contestant's latest source.

It is a single static file, `src/main/resources/static/index.html`, and uses
only the public API with the logged-in user's token.

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
| `IUDEX_SOURCE_MAX_BYTES` | `65536` | Largest submission, template, or reference solution (UTF-8 bytes, at most 65536). |

Generate a secret with:

```bash
openssl rand -base64 32
```

A deployment must set `JWT_SECRET` and `IUDEX_TESTCASE_SECRET`. A malformed or
too-short value fails at startup rather than at the first request.

## How judging works

The server never runs contestant code, and never takes a score from the client.

1. `GET /api/contests/{contestId}/problems/{problemNum}/tests` returns every test case input, sample and
   hidden alike, encrypted with AES-256-GCM under `IUDEX_TESTCASE_SECRET`.
   Expected outputs are not in the response type at all.
2. The client decrypts the inputs, runs the contestant's program, and encrypts
   what the program printed.
3. `POST /api/submissions` sends the language, the source code, the
   client's compile result, and for every test case its encrypted output and
   how the run ended (`SUCCESS`, `RUNTIME_ERROR`, `TIME_LIMIT_EXCEEDED`,
   `MEMORY_LIMIT_EXCEEDED`, `OUTPUT_LIMIT_EXCEEDED`). The server decrypts the
   outputs, compares each against the expected output it holds in file
   storage, and derives the verdict and `passedTestCaseCount` itself.

Comparison ignores trailing whitespace, line ending style, and trailing blank
lines. Leading whitespace is significant.

The verdict is one of `ACCEPTED`, `WRONG_ANSWER`, `COMPILATION_ERROR`,
`RUNTIME_ERROR`, `TIME_LIMIT_EXCEEDED`, `MEMORY_LIMIT_EXCEEDED`,
`OUTPUT_LIMIT_EXCEEDED` or `SYSTEM_ERROR`:

- A reported compile failure is a compilation error; its diagnostics are kept.
- A test case reported twice, or one the problem does not have, is a system
  error, so a modified client cannot manufacture a count.
- Otherwise the first failing test case, in order, decides. A reported failure
  status can only fail a test, never pass it.
- A run that passes everything it reported but left test cases out is a
  system error. Only a complete, matching run is accepted.

The message names a failing test only when it is a sample (public anyway);
a hidden failure is reported as "on a hidden test case".

Source code is stored with each attempt, but only a contestant's latest attempt
at each problem keeps it. `GET /api/submissions/{id}/source` returns it to its
author or the contestmaster.

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
{IUDEX_STORAGE_ROOT}/{contestId}/{problemNum}/
    problem.json           title, time and memory limits, score
    statement.txt
    templates.json         starting code per language: {"cpp": "...", "python": "..."}
    solution.json          the contestmaster's reference solution, if uploaded
    sample_testcases.json
    hidden_testcases.json
```

## Templates and reference solutions

A problem's `solutionTemplates` maps a language id (`cpp`, `java`, `python`) to
the starting code contestants get in that language -- typically the program
that reads the input and calls an empty function for them to complete. On
update the map is merged into what is stored; a blank value removes a language.

`PUT`, `GET` and `DELETE /api/contests/{id}/problems/{n}/solution` manage the
contestmaster's reference solution, `{"language": "...", "sourceCode": "..."}`.
Only the contest's contestmaster and administrators can reach it, at any point
in the contest. It is never part of a contestant-facing response and plays no
part in grading; it is kept for reference and post-contest analysis.

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

## Logging out

`POST /auth/logout` with a valid Bearer token returns `204` and revokes
**every** token issued to that account so far, on every device, not just the
one presented. Each user has a token version that is copied into the tokens
they are issued; logout increments it, and the JWT filter refuses any token
whose version no longer matches. Logging in again issues a working token.

Tokens issued before this mechanism existed carry no version and are refused,
so upgrading a running deployment signs everyone out once.
