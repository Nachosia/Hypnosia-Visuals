# License Server Security Checklist

The public mod must never contain anything that can grant server access. Current server management is console-only: no web panel and no public admin HTTP endpoint.

## Client Mod Rules

- Store only the 32-character `license.key` on the user's machine.
- Do not store admin tokens in the client.
- Do not store database or license data files in the client.
- Do not store VPS IP credentials, SSH keys, or passwords in the client.
- Do not let users override the license API endpoint from `license.properties`.
- Accept roles only from the client-side allow-list enum.
- Query the license server once per Minecraft session and reuse `LicenseManager.state`.

## Server Rules

- Manage keys only through the VPS console over SSH, using the local `license-server <command>` CLI.
- Keep `HYPNOSIA_LICENSE_DATA` outside the Git repository on the VPS.
- Do not expose the license data file or backup files through Nginx.
- Put the public API behind Nginx with HTTPS.
- Firewall allow:
  - `22/tcp` only from your IP if possible
  - `80/tcp`
  - `443/tcp`
- Bind the Kotlin service to `127.0.0.1` when Nginx proxies to it.
- Disable password SSH login after adding an SSH key.
- Use a non-root deploy user, for example `hypnosia`.
- Run the API as a systemd service under the non-root user.

## Public API

Only this endpoint should be reachable by mod clients:

```text
POST /api/license/check
```

Request:

```json
{
  "license": "32_CHARACTER_LICENSE_KEY",
  "hwid": "32_CHARACTER_PUBLIC_HWID_KEY",
  "hwidHash": "64_CHARACTER_SHA256_HASH",
  "modVersion": "0.1.0"
}
```

There are no `/admin` routes. Key creation, disabling, role edits, expiry edits, and HWID resets happen from the server console only.

## First-Bind Safety

The first valid client request binds the key to the submitted HWID hash. After that, only the same HWID hash is accepted.

The console command for manual reset is:

```text
reset-hwid <key>
```

## Rate Limiting

Minimum recommended limit at Nginx:

```text
/api/license/check: 10 requests/minute/IP
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
- license data file
- signing private key
- raw user HWID values
