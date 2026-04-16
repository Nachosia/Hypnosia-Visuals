# License Server Security Checklist

The public mod must never contain anything that can grant server access.

## Client Mod Rules

- Store only the 32-character `license.key` on the user's machine.
- Do not store admin tokens in the client.
- Do not store database credentials in the client.
- Do not store VPS IP credentials, SSH keys, or passwords in the client.
- Do not let users override the license API endpoint from `license.properties`.
- Accept roles only from the client-side allow-list enum.
- Query the license server once per Minecraft session and reuse `LicenseManager.state`.

## Server Rules

- Keep `HYPNOSIA_ADMIN_TOKEN` only in the server `.env`.
- Keep the database on localhost or in a private network.
- Do not expose SQLite/PostgreSQL ports to the internet.
- Put the API behind Nginx with HTTPS.
- Firewall allow:
  - `22/tcp` only from your IP if possible
  - `80/tcp`
  - `443/tcp`
- Firewall deny all direct database ports.
- Disable password SSH login after adding an SSH key.
- Use a non-root deploy user, for example `hypnosia`.
- Run the API as a systemd service under the non-root user.

## API Rules

Public endpoint:

```text
POST /api/license/check
```

This endpoint receives:

```json
{
  "license": "32_CHARACTER_LICENSE_KEY",
  "hwid": "32_CHARACTER_PUBLIC_HWID_KEY",
  "hwidHash": "64_CHARACTER_SHA256_HASH",
  "modVersion": "0.1.0"
}
```

Admin endpoints must be separate and protected:

```text
POST /api/admin/licenses/create
POST /api/admin/licenses/reset-hwid
POST /api/admin/licenses/disable
```

Every admin request must require:

```text
Authorization: Bearer <HYPNOSIA_ADMIN_TOKEN>
```

The admin token must be read from the server environment only.

## First-Bind Safety

Bind HWID atomically:

```sql
UPDATE licenses
SET hwid_hash = ?, bound_at = ?
WHERE license_key = ?
  AND hwid_hash IS NULL;
```

Then re-read the row and compare `hwid_hash`.

## Rate Limiting

Minimum recommended limits:

```text
/api/license/check: 10 requests/minute/IP
/api/admin/*: 3 requests/minute/IP
```

Log rejected attempts, but never log raw HWID source values.

## What A Leak Must Not Reveal

If someone decompiles the mod, they may see:

- public API URL
- public role names
- request JSON shape

They must not see:

- server password
- SSH private key
- admin token
- database password
- signing private key
- raw user HWID values
