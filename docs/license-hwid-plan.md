# License HWID Binding Plan

Hypnosia licenses should be bound to a hardware fingerprint instead of a Minecraft UUID.

## Client Identifier

The client exposes:

```text
HardwareFingerprint.currentKey32()
```

This returns a 32-character uppercase SHA-256 prefix. It is safe to send to the license server because it is not the raw hardware id.

The full hash is also available:

```text
HardwareFingerprint.currentHash64()
```

Prefer storing the full 64-character hash on the server and showing the 32-character key to users/admins.
In the current local server implementation, admins manage keys from the server console only. There is no web panel.

## Source Priority

The fingerprint source is platform-specific:

- Windows: `HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid`
- Linux: `/etc/machine-id` or `/var/lib/dbus/machine-id`
- macOS: `IOPlatformUUID`
- Fallback: random local install id stored under Fabric config

The raw value is never exposed by the API. It is normalized and hashed with the `hypnosia-hwid-v1` version prefix.

## Server Record

Example server-side license row:

```json
{
  "license": "A1B2C3D4E5F60718293A4B5C6D7E8F90",
  "hwidHash": "64_CHARACTER_SHA256_HASH",
  "role": "PREMIUM",
  "expiresAt": "2026-12-31T23:59:59Z",
  "disabled": false
}
```

Role values returned by the server must be one of the client allow-list values:

```text
USER
PREMIUM
QA
ADMIN
OWNER
```

Unknown roles are treated as an invalid response.

## Client Request

```json
{
  "license": "A1B2C3D4E5F60718293A4B5C6D7E8F90",
  "hwid": "32_CHARACTER_PUBLIC_HWID_KEY",
  "hwidHash": "64_CHARACTER_SHA256_HASH",
  "modVersion": "0.1.0"
}
```

The client must not send this request when `config/hypnosia/license.properties` is missing or `license.key` is blank.
The client also does not read the API URL from `license.properties`; server endpoint configuration belongs to the build/server deployment, not the user config.

Startup flow:

```text
load license.properties
if license.key is blank:
  state = NO_KEY
  do not calculate HWID
  do not call the server
else:
  calculate HWID hash
  call /api/license/check
  keep the returned role in LicenseManager.state for the whole Minecraft session
```

The client starts this flow once from `HypnosiaClient.onInitializeClient()` through:

```text
LicenseManager.startSessionAsync()
```

`startSessionAsync()` is idempotent. If it is called again by UI code, it returns the already-created session future and does not perform another HTTP request. Menus and widgets must read:

```text
LicenseManager.state
LicenseManager.sessionRole
```

They must not call the server directly.

## First HWID Binding

The user only stores the license key locally. The server owns the HWID binding.

When a license row has no `hwidHash`, the first valid client request binds it:

```sql
UPDATE licenses
SET hwid_hash = ?, bound_at = ?
WHERE license_key = ?
  AND hwid_hash IS NULL;
```

If the update affects one row, the server returns:

```json
{
  "valid": true,
  "status": "BOUND_NOW",
  "role": "PREMIUM"
}
```

If the row is already bound, the server compares the stored `hwidHash` with the request hash:

- match: return `valid=true`
- mismatch: return `valid=false`, `status=HWID_MISMATCH`

## Notes

- Do not store raw MachineGuid, machine-id, serial numbers, or usernames on the server.
- Do not commit fallback `install-id.dat` or license cache files.
- HWID binding is stronger than UUID-only binding, but it is still client-side and can be bypassed by a modified client jar.
- Users who reinstall Windows or replace major hardware may need a manual HWID reset.

For the console server, the reset command is:

```text
reset-hwid <key>
```
