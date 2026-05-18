# Public Release Checklist

Use a separate public branch or a separate public repository. Do not make the private repository public while it still contains server history.

## Must Be Excluded

- `license-server/`
- `license-admin/`
- private deployment and VPS notes
- real server data, TSV databases, backups, logs, and dumps
- `figma.properties`
- `.env` and local environment files
- `account.properties`
- `license.properties`
- SSH keys and certificates
- any real account, license, admin, or API secrets

## Public-Safe Content

- root Minecraft client mod code under `src/main/`
- public assets and shaders
- Gradle wrapper and client build files
- public README, LICENSE, SECURITY, and client-only documentation

## Recommended Workflow

1. Create a fresh public branch or a fresh repository from a clean export.
2. Copy only public-safe files into that tree.
3. Confirm `license-server/` and `license-admin/` are absent.
4. Confirm `settings.gradle.kts` builds without server modules.
5. Run a full client build.
6. Scan for secrets before pushing.

## Verification Commands

```powershell
.\gradlew.bat "-PhypnosiaBuildDir=build-check-public-release" build --no-daemon --no-parallel
```

Use ripgrep or an equivalent scanner before publishing:

```powershell
rg -n "accountKey|licenseKey|FIGMA_TOKEN|FIGMA_PAT|PRIVATE KEY|password|account\.properties|license\.properties|\.env" .
```

Review every match manually. Some code references are expected, but real secret values must not be present.
