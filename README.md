# XingZhi Android Bootstrap

Shared signed-config startup for XingZhi applications: pinned OpenPGP trust
root, license-as-validator, fail-closed bootstrap, and UI-facing
`ProvisioningFailure` mapping.

Applications still own YAML schemas, preference writers, and Android time
storage adapters.

## Dependency

Coordinates: `com.xingzhi:android-bootstrap:1.2.0`

```kotlin
maven {
    url = uri("https://maven.pkg.github.com/OpenXingZhi/android-bootstrap")
    credentials(PasswordCredentials::class)
}

implementation("com.xingzhi:android-bootstrap:1.2.0")
```

`MonotonicDeviceTime` is the shared boot-id / elapsed-realtime adapter.
Applications still own `TimeAnchorStore` (SharedPreferences vs DataStore).

GitHub Packages requires a GitHub username and a token with `read:packages`
(local `~/.gradle/gradle.properties` `GitHubPackagesUsername` /
`GitHubPackagesPassword`, or `GITHUB_ACTOR` / `GITHUB_TOKEN` in CI).

Shared repository wiring lives in the settings plugin
`com.xingzhi.github-packages:1.2.0` from the same package repository:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://maven.pkg.github.com/OpenXingZhi/android-bootstrap")
            credentials(PasswordCredentials::class)
        }
    }
}
plugins {
    id("com.xingzhi.github-packages") version "1.2.0"
}
xingzhiGitHubPackages {
    modules.addAll(
        "remote-config",
        "trusted-time",
        "license-library",
        "rfid-codec",
        "android-bootstrap",
    )
}
```

## Usage

```kotlin
val coordinator = BootstrapCoordinator(
    client = remoteConfigClient,
    licensePolicy = BoundLicensePolicy(
        trustedTime = trustedClock,
        serialNumber = { deviceSerial },
        licenseOf = { it.license },
    ),
    applyConfig = { config -> /* write prefs / publish flow */ },
    revisionOf = { it.revision },
)

coordinator.bootstrap(deviceSerial)
```

`bootstrap` falls back to a last-known-good snapshot when refresh fails.
`refreshNow` does not. License eligibility runs after the snapshot is chosen
so a signed expiry or revocation is not replaced by an older local license.

## Diagnostics

Package `com.xingzhi.bootstrap.diagnostics` is a plain JVM helper (Java 11 bytecode,
no Android SDK) on Touchlab Kermit, which this artifact exposes as an `api`
dependency:

- `TelemetryConfig` — optional DSN and environment. `isActive` is true only when
  telemetry is enabled and `dsn` is non-blank.
- `LogRedactor` — redacts `Bearer` tokens, credential fields (`password`,
  `accessToken`, `encryptedAccessToken`, `refreshToken`, `token`,
  `authorization`, `secret`, `privateKey`) in JSON, YAML, and query strings, and
  SIP2 patron/login passwords (`|AD…|`, `|CO…|`).
- `RollingFileLogWriter` — thread-safe rolling files `<prefix>.log`,
  `<prefix>.1.log`, … `<prefix>.<maxFiles-1>.log`. `logFiles()` is
  oldest-to-newest. `exportZip` atomically writes a support bundle; text extras
  such as `device-summary.json` are redacted. `exportZipBytes` keeps the
  in-memory ZIP within `maxTotalBytes` for a Sentry attachment. `clear()`
  deletes this writer's `<prefix>*.log` files.

## Development

This repository publishes a plain JVM library through GitHub Actions
(`OpenXingZhi/AndroidCI/.github/workflows/gradle-library.yml`). Sibling XingZhi
artifacts come from GitHub Packages.

```bash
./gradlew :android-bootstrap:build
./gradlew :android-bootstrap:publishToMavenLocal   # optional local install
```

Tag releases as `v*.*.*` to trigger CI publish to GitHub Packages.
