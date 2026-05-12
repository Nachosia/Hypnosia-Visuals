# Security Policy

## Public Repository Rules

The public repository must not include private server code, admin panel code, deployment notes, server data, account keys, license keys, Figma tokens, SSH keys, passwords, `.env` files, or local account/license property files.

Before publishing, scan the tree for sensitive strings such as `accountKey`, `licenseKey`, `FIGMA_TOKEN`, `FIGMA_PAT`, `PRIVATE KEY`, `password`, `.env`, `account.properties`, and `license.properties`.

## Reporting

Report security issues privately to the project owner. Do not open public issues containing secrets, exploit details, server paths, or infrastructure details.
