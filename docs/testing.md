You must have an environment variable named JWT_SECRET, which you should set in your terminal before running the command. It must be at least 32 bytes long; the server refuses to start with anything shorter, and the signing algorithm (HS256) does not change with the length of what you set.

## Windows (cmd)
```bash
set JWT_SECRET=thisisaverylongstringitdoesntmatterwhatyouputhereatall
gradlew.bat run
```

set your env variable first
## Linux or MacOS
```bash
export JWT_SECRET=thisisaverylongstringitdoesntmatterwhatyouputhereatall
./gradlew run
```

This exposes the server on the default port (8080). To run on a specific port,
```bash
set PORT=9000 && gradlew.bat run
```

The other settings are listed in the README. Two are worth knowing while testing:

- `IUDEX_DB_URL` chooses the database. The default path is relative to the working directory, so through Gradle the file appears in `app/`. The url actually used is logged at startup.
- `ALLOWED_ORIGINS` lists the origins allowed to call the API from a browser, comma separated. If you leave it unset, a browser client on another origin will be blocked; `curl` is unaffected.

You can then check server health. Go to http://localhost:8080/api/health.
```json
{"status":"live"}
```

You can run all unit tests
```bash
gradlew.bat test
```

You can run all integration tests
```bash
gradlew.bat integrationTest
```

Or run both
```bash
gradlew.bat check
```

Currently, a fake user by the name of alice is injected into the database for testing purposes
You can test as follows:

login:
```bash
curl.exe -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\`"username\`":\`"alice\`", \`"password\`":\`"correct-password\`"}"
```
Response:
{"token":"YOUR_TOKEN_APPEARS_HERE"}


logout:
```bash
curl.exe -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer YOUR_TOKEN_GOES_HERE"
```

Response:
{"message":"Successfully logged out"}

If you try this a second time:
{"error":"Token has been revoked"}

## Errors

Every failure uses the same shape, so a client only ever parses one thing:
```json
{ "error": "some description" }
```

Worth checking by hand:

- No token on a protected route is `401`, and so is a junk or revoked one.
- A body that is not JSON, or is missing `username` or `password`, is `400` - not `500`.
- A route that does not exist is `404`, still as JSON.

## Rate limiting

Failed logins are counted. Five wrong passwords for one username, or twenty from one address, pause further attempts for fifteen minutes and answer `429` with a `Retry-After` header. A successful login clears the count. You will hit this if you script login attempts, which is the point.
