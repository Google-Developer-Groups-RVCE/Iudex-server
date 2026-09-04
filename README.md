# Iudex
Iudex is an open-source competitive programming judge which is intended to allow for locally hosted competitions with minimal compute resources.

This repository covers the server side of things; if you're looking for the client, go to [this repository](https://github.com/Google-Developer-Groups-RVCE/Iudex-client).

If you're organizing a competitive programming competition, you're in the right place.

Follow the contents of this file to get started. This will cover the basics. For an understanding of how you can fully utilize the server, read the docs.

## If you're a contributer

To get started with contributing, follow the steps below.

This project uses Java 23.

You will need `gradle` to build the project. Install it using a package manager or by following the instructions [here](https://docs.gradle.org/current/userguide/installation.html)

### Configuration

The server reads its settings from the environment.

| Variable | Required | Default | Meaning |
| --- | --- | --- | --- |
| `JWT_SECRET` | **yes** | none | Secret used to sign tokens. Must be at least 32 bytes; the server refuses to start otherwise. |
| `PORT` | no | `8080` | Port to listen on. |
| `IUDEX_DB_URL` | no | `jdbc:h2:file:./iudex_db` | JDBC url. The default is relative to the working directory, so launched through Gradle it lands in `app/`. The resolved url is logged at startup. |
| `ALLOWED_ORIGINS` | no | none | Comma separated origins allowed to call the API from a browser, e.g. `http://localhost:5173`. If unset, cross-origin browser requests are refused. |

There is a `.env.example` template listing all four. Copy it and fill it in:

```bash
cp .env.example .env
```

Nothing reads `.env` on its own, so load it into your shell first.

```bash
# macOS / Linux
set -a; source .env; set +a

# Windows PowerShell
Get-Content .env | Where-Object { $_ -match '^\s*[^#\s]' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "env:$name" -Value $value
}
```

To build and run:
### Windows
```
> set JWT_SECRET=replace-this-with-at-least-32-bytes-of-secret
> gradlew.bat run
```

### Linux or MacOS
```
> export JWT_SECRET=replace-this-with-at-least-32-bytes-of-secret
> ./gradlew run
```

This project uses Javalin 7. To learn more about it, visit their [documentation page](https://javalin.io/documentation).

To run a clean build (compiles, runs the tests, and builds the distribution):
### Windows
```
> gradlew.bat clean build
```

### Linux or MacOS
```
> ./gradlew clean build
```

To compile without running the tests, use `assemble` in place of `build`.

## Errors

Every error, whatever caused it, comes back with the same shape:
```json
{ "error": "Invalid credentials" }
```