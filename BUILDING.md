# Building Universal Missile Regeneration

UMR's public source is intended to be reviewable and reproducibly buildable, but Starsector and optional-library JARs are not redistributed in this repository.

## Target baseline

UMR 1.0.0 targets:

- Starsector `0.98a-RC8`;
- Starsector's Java 17 runtime;
- emitted UMR bytecode: Java 8 / class major 52;
- compiler mode: `javac --release 8`.

The 1.0.0 release keeps the already validated Java-8-bytecode target. This is a release-baseline decision, not a claim that new Starsector projects should target Java 8.

## Required compile references

Provide local copies of:

- `starfarer.api.jar`
- `log4j-1.2.9.jar`
- `json.jar`
- LunaLib 2.0.5 `LunaLib.jar`

LunaLib is only a compile reference for UMR's isolated optional bridge. It is not a required runtime dependency.

Validated 1.0.0 promotion-reference hashes:

| Reference | SHA-256 |
| --- | --- |
| `starfarer.api.jar` | `a7ba18f3476ffe704729bd0a7a47443f035fea98a32ac2930eae8b391d013c2a` |
| `log4j-1.2.9.jar` | `d2b9dfb297bcaa7be1fcdd702642a9c9713d7847dca8704e9c15bd829f0ab1bf` |
| `json.jar` | `63c3541f323f3dfdd595da9257a2099b6a6c39f35a6b3909d86c48a8aa456911` |
| LunaLib 2.0.5 `LunaLib.jar` | `d20304b9404f03392482703a55e655cadb0a1735d78c9b2da6b209e1217bbbfd` |

Use the JARs from your own Starsector/LunaLib installations or authorized downloads. They are not redistributed with UMR.

## Build command

From the UMR source root:

```bash
STARSECTOR_API=/path/to/starfarer.api.jar \
LOG4J_JAR=/path/to/log4j-1.2.9.jar \
JSON_JAR=/path/to/json.jar \
LUNALIB_JAR=/path/to/LunaLib.jar \
bash build.sh
```

The build script:

1. clears prior build output;
2. compiles production Java with `javac --release 8`;
3. packages `jars/UniversalMissileRegeneration.jar`;
4. normalizes JAR timestamps for deterministic output.

## Windows

A Windows build script is also included where provided by the release source.

Make sure the same exact dependency versions are used.

## Do not bundle dependencies

A UMR runtime package should not include:

- Starsector API/core JARs;
- LunaLib.jar;
- compiler/runtime JARs used only for building.

Players provide Starsector itself, and LunaLib remains optional.

## Release builds

The authoritative 1.0.0 release is built and validated by the project before publication.

A locally rebuilt JAR may differ if you:

- use different source;
- use different dependency versions;
- use a different compiler mode;
- modify timestamps/packaging.

See the public release's recorded hashes when comparing against the official artifact.
