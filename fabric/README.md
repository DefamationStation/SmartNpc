# Fabric 26.4 Snapshot 2 port

Work in progress. Do not install this branch into an existing world until its
runtime validation is recorded here.

This module targets launcher version `26.4-snapshot-2` (internal version
`26.4-alpha.2`), Java 25 and Fabric Loader 0.19.5. The root project continues to
target NeoForge 26.1.2 without changes to its source or build configuration.

Build from this directory with `./gradlew build` (Windows: `gradlew.bat build`).
The module shares the upstream Java/resources and provides explicit replacement
files only for platform or snapshot API differences. `prepareSources` merges
them in the build directory without modifying the upstream files.

Upstream: https://github.com/PlaIsMe/SmartNpc (26.1.2 branch).
Port: https://github.com/DefamationStation/SmartNpc/tree/fabric-26.4-snapshot-2

The original author is pla_is_me. GPLv3 and the upstream third-party notices apply.
