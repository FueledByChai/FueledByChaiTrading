# New Exchange Scaffolder

Use `scripts/new-exchange.sh` to generate a new exchange skeleton that follows the current provider/factory wiring.

## Quick Start

```bash
scripts/new-exchange.sh <exchange-slug> --all --update-exchange-enum
```

Example:

```bash
scripts/new-exchange.sh vertex --all --update-exchange-enum
```

## Default Behavior

Without extra flags, the script creates only:

- `commons/<exchange>-common-api`

Optional flags add implementation modules:

- `--with-market-data`
- `--with-broker`
- `--with-historical`
- `--all` (all three)

## Helpful Flags

- `--dry-run`: preview changes without writing files.
- `--no-register-modules`: don’t edit parent `pom.xml` module lists.
- `--update-exchange-enum`: add `Exchange.<NEW_EXCHANGE>` plus `ALL_EXCHANGES` entry.

## Validation

After scaffolding, validate the generated commons module:

```bash
scripts/validate-common-module.sh commons/<exchange>-common-api
```

## Notes

- The script creates a compile-ready commons module with placeholder implementations, a module `README.md`, example classes, and common-module test skeletons.
- You still need to implement exchange-specific REST, websocket parsing, and ticker normalization.
- New modules should follow the shared documentation and lifecycle rules in `docs/commons-api-conventions.md`.
