# Binary provenance

Discord Rich Presence is implemented as Java source in
`src/main/java/vorga/phazeclient/api/system/discord/DiscordIpcClient.java`.
The project does not contain or ship `discord-rpc.dll`.

## Included third-party artifacts

- `io.github.imurx:arboard:1.1.2`
  - Artifact: https://repo1.maven.org/maven2/io/github/imurx/arboard/1.1.2/arboard-1.1.2.jar
  - Source: https://github.com/ImUrX/arboard-java
  - Published sources: https://repo1.maven.org/maven2/io/github/imurx/arboard/1.1.2/arboard-1.1.2-sources.jar
  - Licenses: Apache-2.0 and MIT
  - SHA-256: `d8df9ffa2e29a9c2a895f5e22d2a8993c1c9562ed828bd1bd77caf3295a53e67`
- `com.github.ramanrajarathinam:native-utils:1.0.0` (transitive)
  - Artifact: https://repo1.maven.org/maven2/com/github/ramanrajarathinam/native-utils/1.0.0/native-utils-1.0.0.jar
  - Source: https://github.com/RamanRajarathinam/native-utils/tree/1.0.0
  - License: MIT
  - SHA-256: `d0012c3e3b098d4a988bd13dacab408ec9a070889aaa029c9e42645f23148f32`

Native files copied unchanged from `arboard-1.1.2.jar`:

| File | SHA-256 |
| --- | --- |
| `aarch64_arboard_java.dll` | `4e2c5866dd0cd0338b29493dd182623e2b9ae58ff0995dd31d44394bb21e39c4` |
| `aarch64_libarboard_java.dylib` | `a0bf722c1574c13b3fa3bf94adcee65033867d00e8a243c1b8119351862f6573` |
| `aarch64_libarboard_java.so` | `f98c313dbb0e1716826c1c5e86f2520071b62a6c09a18b6f272acb65ee468a19` |
| `x86_64_arboard_java.dll` | `3acf5a88e298daec73f8dbb09b9ec5119042fc672abb51f962749670db0a06e4` |
| `x86_64_libarboard_java.dylib` | `08bbc88e53cef24f24aa93b4bf6093c1bbe0939955d93cf9d062c3615d4d7489` |
| `x86_64_libarboard_java.so` | `d9a5ba14e2c9d085d8b1df95a68c2cfea956a074b5b218b6a36405156c04d162` |

## Compile-only artifacts

These public artifacts are not included in the published jar:

- Iris `1.8.8+1.21.4-fabric`, Modrinth version `Ca054sTe`
- Reese's Sodium Options `mc1.21.4-1.8.3+fabric`, version `KoUrx3jJ`
- Sodium `mc1.21.4-0.6.13-fabric`

The Gradle 8.14 distribution URL and SHA-256 are pinned in
`gradle/wrapper/gradle-wrapper.properties`. GitHub Actions builds from a clean
checkout and uploads `build/libs/PhazeClient-1.0.1.jar`.
