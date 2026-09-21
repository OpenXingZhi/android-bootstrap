# XingZhi Android Bootstrap

Shared signed-config startup for XingZhi applications: pinned OpenPGP trust
root, license-as-validator, fail-closed bootstrap, and UI-facing
`ProvisioningFailure` mapping.

Applications still own YAML schemas, preference writers, and Android time
storage adapters.

## Dependency

Coordinates: `com.xingzhi:android-bootstrap:1.0.0`

```kotlin
maven {
    url = uri("https://maven.pkg.github.com/OpenXingZhi/android-bootstrap")
    credentials(PasswordCredentials::class)
}

implementation("com.xingzhi:android-bootstrap:1.0.0")
```

GitHub Packages requires a GitHub username and a token with `read:packages`
(local `~/.gradle/gradle.properties` `GitHubPackagesUsername` /
`GitHubPackagesPassword`, or `GITHUB_ACTOR` / `GITHUB_TOKEN` in CI).

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

## Development

This repository publishes a plain JVM library through GitHub Actions
(`OpenXingZhi/AndroidCI/.github/workflows/gradle-library.yml`). Sibling XingZhi
artifacts come from GitHub Packages.

```bash
./gradlew :android-bootstrap:build
./gradlew :android-bootstrap:publishToMavenLocal   # optional local install
```

Tag releases as `v*.*.*` to trigger CI publish to GitHub Packages.
