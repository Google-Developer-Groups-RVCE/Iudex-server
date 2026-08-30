## Windows
```bash
gradlew.bat run
```

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