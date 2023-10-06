[![JetBrains Research](https://jb.gg/badges/research.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

# REDCLIFF-Java

## Refactoring Data Clumps Innovative Flexible Framework - Java

An IntelliJ plugin designed to parse projects into AST files, focusing on class hierarchies, attributes, and methods. These AST files serve as the foundation for subsequent tools to detect and address data clumps in codebases. The plugin is compatible with various build tools, including Gradle, Maven, and Ant.


To learn more, explore
the [IntelliJ Platform SDK documentation](https://plugins.jetbrains.com/docs/intellij/welcome.html) and check out code
samples in the [IntelliJ Platform SDK Code Samples repository](https://github.com/JetBrains/intellij-sdk-code-samples).

## Running in Docker-Compose:
- Adapt the folders in ``docker-compose.yml`` to your needs

``./build_image.sh && docker-compose up``

## Running in CLI:

``./gradlew :demo-plugin:buildPlugin``

```runDemoCLI.sh <path-to-project> <path-to-output>```


