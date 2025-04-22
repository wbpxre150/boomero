# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build commands
- Build app: `./gradlew build`
- Install app: `./gradlew installDebug`
- Run unit tests: `./gradlew test`
- Run single test: `./gradlew test --tests "com.wbpxre150.boomero.TestClassName.testMethodName"`
- Run instrumented tests: `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`

## Code style guidelines
- **Kotlin conventions**: Follow standard Kotlin style guide with 4-space indentation
- **Imports**: Group by package, no wildcards, alphabetical order
- **Naming**: camelCase for variables/methods, PascalCase for classes, UPPER_SNAKE for constants
- **Types**: Use explicit types for public APIs, inferred otherwise
- **Error handling**: Use try-catch blocks with specific exceptions, prefer coroutines for async operations
- **Architecture**: Follow MVVM pattern with ViewModel, UI state management with StateFlow
- **Dependencies**: Jetpack Compose for UI, Hilt for DI, Coroutines/Flow for async operations
- **Logging**: Use TAG constant and Log.d/e/i for debug information