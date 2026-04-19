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

## Security

- Uses Basic Auth.
- The admin password is stored only in the VPS systemd unit.
- The panel is only reachable through an SSH tunnel.
- Public clients must use only `/api/license/check`.
