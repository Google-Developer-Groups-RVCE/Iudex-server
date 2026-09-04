Note, you must have an environment variable named JWT_SECRET, which you should set in your terminal before running the command.

## Windows (cmd)
```bash
set JWT_SECRET=thisisaverylongstringitdoesntmatterwhatyouputhere
gradlew.bat run
```

set your env variable first
## Linux or MacOS
```bash
./gradle run
```

This exposes the server on the default port (8080). To run on a specific port,
```bash
set PORT=9000 && gradlew.bat run
```

You can then check server health. Go to http://localhost:8080/api/health.
```json
{ "status": "live" }
```

Alternatively, run the test for the health endpoint
```bash
gradlew.bat test --tests "gdg.iudex.TestHealth"
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
{ "message": "Successfully logged out" }

If you try this a second time:
{"error":"Token has been revoked"}
