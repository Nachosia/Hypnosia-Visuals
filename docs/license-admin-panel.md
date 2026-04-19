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
ssh -i "$env:USERPROFILE\.ssh\hypnosia_codex" -L 9090:127.0.0.1:9090 root@2.26.0.174
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
- list licenses
- create license
- change role
- change expiration
- reset HWID
- disable or enable license
- delete license
- create a server-side backup of `licenses.tsv`
- download a ready-to-use `license.properties` file for each key
- view player cloud configs saved through the public cloud config API
- download, disable, enable, or delete saved cloud configs

## Cloud Config API

Players can save a module config and receive an 8-character share key:

```text
POST /api/cloud-config/save
```

The payload should be sent as Base64 so the config can contain arbitrary JSON:

```json
{
  "hwidHash": "64_CHARACTER_SHA256_HASH",
  "license": "OPTIONAL_32_CHARACTER_LICENSE_KEY",
  "name": "Legit Visuals",
  "payloadBase64": "eyJtb2R1bGVzIjpbXX0="
}
```

The server limits active configs to 3 per HWID.

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
  "configKey": "A7K9Q2MX",
  "hwidHash": "64_CHARACTER_SHA256_HASH"
}
```

## Security

- Uses Basic Auth.
- The admin password is stored only in the VPS systemd unit.
- The panel is only reachable through an SSH tunnel.
- Public clients must use only `/api/license/check` and `/api/cloud-config/*`.
