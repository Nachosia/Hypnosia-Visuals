# Local Admin Panel

The admin panel is a separate service from the public license API.

It must listen only on VPS localhost:

```text
127.0.0.1:9090
```

Do not expose port `9090` through the firewall or Nginx.

## Open From Your PC

Create an SSH tunnel:

```powershell
ssh -i "$env:USERPROFILE\.ssh\hypnosia_codex" -L 9090:127.0.0.1:9090 root@<VPS_IP>
```

Keep that terminal open, then open:

```text
http://127.0.0.1:9090
```

## VPS Service Commands

```bash
systemctl status hypnosia-admin
systemctl restart hypnosia-admin
journalctl -u hypnosia-admin -f
```

## Features

- dashboard counters
- list accounts
- create account manually by HWID hash
- edit account display name
- disable account
- reset account key
- list role keys
- create role key
- change role key role
- change role key expiration
- reset role key HWID
- disable or enable role key
- delete role key
- create a server-side backup of `licenses.tsv`
- download a ready-to-use `license.properties` file for each key
- view player cloud configs saved through the account menu
- download, disable, enable, or delete saved cloud configs

## Minecraft Client Files

Accounts are stored locally in:

```text
.minecraft/hypnosia/account.properties
```

Role keys remain compatible with:

```text
.minecraft/hypnosia/license.properties
```

Only one chat command should remain:

```text
/actkey <32-char-role-key>
```

Account creation, account name/contact editing, and cloud config upload/load/delete are handled by the in-game account menu.

Local config files are read from and written to:

```text
.minecraft/hypnosia/configs/
```

For example, uploading `legit` from the account menu reads:

```text
.minecraft/hypnosia/configs/legit.json
```

## Account API

The public account API is exposed through Nginx:

```text
POST /api/account/create
POST /api/account/info
POST /api/account/set-name
POST /api/account/apply-key
```

Create account:

```json
{
  "hwidHash": "64_CHARACTER_SHA256_HASH",
  "displayName": "optional"
}
```

Apply a role key without restarting Minecraft:

```json
{
  "accountKey": "32_CHARACTER_ACCOUNT_KEY",
  "hwidHash": "64_CHARACTER_SHA256_HASH",
  "licenseKey": "32_CHARACTER_ROLE_KEY"
}
```

## Cloud Config API

Players can save a module config and receive an 8-character share key:

```text
POST /api/cloud-config/save
```

The payload should be sent as Base64 so the config can contain arbitrary JSON:

```json
{
  "accountKey": "OPTIONAL_32_CHARACTER_ACCOUNT_KEY",
  "hwidHash": "64_CHARACTER_SHA256_HASH",
  "license": "OPTIONAL_32_CHARACTER_LICENSE_KEY",
  "name": "Legit Visuals",
  "payloadBase64": "eyJtb2R1bGVzIjpbXX0="
}
```

If `accountKey` is missing, the server creates an account for that HWID and returns the new account key.
The server limits active configs to 3 per account.

Other players load a shared config by key:

```text
POST /api/cloud-config/load
```

```json
{
  "configKey": "A7K9Q2MX"
}
```

Owners can delete their config:

```text
POST /api/cloud-config/delete
```

```json
{
  "accountKey": "32_CHARACTER_ACCOUNT_KEY",
  "configKey": "A7K9Q2MX",
  "hwidHash": "64_CHARACTER_SHA256_HASH"
}
```

Owners can list their own configs:

```text
POST /api/cloud-config/list
```

```json
{
  "accountKey": "32_CHARACTER_ACCOUNT_KEY",
  "hwidHash": "64_CHARACTER_SHA256_HASH"
}
```

## Security

- Uses Basic Auth.
- The admin password is stored only in the VPS systemd unit.
- The panel is only reachable through an SSH tunnel.
- Public clients must use only `/api/license/check`, `/api/account/*`, and `/api/cloud-config/*`.
