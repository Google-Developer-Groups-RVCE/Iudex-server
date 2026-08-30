# Iudex
Iudex is an open-source competitive programming judge which is intended to allow for locally hosted competitions with minimal compute resources.

This repository covers the server side of things; if you're looking for the client, go to [this repository](https://github.com/Google-Developer-Groups-RVCE/Iudex-client).

If you're organizing a competitive programming competition, you're in the right place.

Follow the contents of this file to get started. This will cover the basics. For an understanding of how you can fully utilize the server, read the docs.

## If you're a contributer

To get started with contributing, follow the steps below.

This project uses Java 23.

You will need `gradle` to build the project. Install it using a package manager or by following the instructions [here](https://docs.gradle.org/current/userguide/installation.html)

To build and run:
### Windows
```
> gradlew.bat run
```

### Linux or MacOS
```
> ./gradle run
```

This project uses Javalin 7. To learn more about it, visit their [documentation page](https://javalin.io/documentation).

To run a clean build,
```bash
> gradlew.bat clean build
```