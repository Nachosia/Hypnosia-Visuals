# Console License Server

This is a local/VPS-ready license server for Hypnosia roles. It has no web panel. Management happens only through the process console, usually over SSH.

## Local Run

Windows:

```powershell
.\gradlew.bat :license-server:run
```

Linux/VPS:

```bash
./gradlew :license-server:run
```

Optional environment:

```bash
HYPNOSIA_LICENSE_HOST=127.0.0.1
HYPNOSIA_LICENSE_PORT=8080
HYPNOSIA_LICENSE_DATA=/opt/hypnosia/licenses.tsv
```

The public API is:

```text
POST /api/license/check
```

## Console Commands

Interactive mode:

```text
help
list
show <key>
create <role> [YYYY-MM-DD|never] [custom-32-char-key]
role <key> <role>
expires <key> <YYYY-MM-DD|never>
disable <key>
enable <key>
reset-hwid <key>
delete <key>
exit
```

Daemon/server mode can be managed with one-shot commands:

```bash
/opt/hypnosia/server/bin/license-server create QA never
/opt/hypnosia/server/bin/license-server list
/opt/hypnosia/server/bin/license-server reset-hwid <key>
```

Roles:

```text
USER
PREMIUM
QA
ADMIN
OWNER
```

Examples:

```text
create QA 2026-12-31
create PREMIUM never
list
show ABCDEFGHJKLMNPQRSTUVWXYZ234567
reset-hwid ABCDEFGHJKLMNPQRSTUVWXYZ234567
disable ABCDEFGHJKLMNPQRSTUVWXYZ234567
```

## Binding Logic

- User stores only `license.key` in `config/hypnosia/license.properties`.
- If the key has no HWID on the server, the first valid launch binds it.
- Next launches must match the same HWID hash.
- If the key is blank or missing locally, the mod does not call the server.
- The client checks once during Minecraft startup and stores the role for the session.

## Storage

The server writes a tab-separated file:

```text
licenseKey	role	hwidHash	createdAt	boundAt	expiresAt	disabled
```

Do not commit `licenses.tsv` or backups.
