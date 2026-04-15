# Public Repository Security

This repository is intended to be safe for public GitHub hosting.

## Commit

- Source code under `src/`
- Public assets under `src/main/resources/`
- Gradle wrapper files
- Documentation under `docs/`
- Example configuration files such as `.env.example` and `figma.properties.example`

## Do Not Commit

- VPS root password
- SSH private keys
- Figma Personal Access Token
- `.env`
- `figma.properties`
- Role/license databases
- Signed role files for real users
- Private signing keys
- Raw HWID values such as MachineGuid, machine-id, serial numbers, or device UUIDs
- Minecraft `run/` directory
- Build output directories such as `build/` and `bin/`

## License System Rule

The client mod may contain only public data:

- API base URL
- Public verification key, if offline cache signatures are used
- Role enum names

The server must keep private data:

- Admin token
- Database
- Signing private key
- Provider/VPS credentials

If a value can grant a role, sign a license, access the server, or edit the database, it must not be present in this repository.
