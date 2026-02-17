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

## Notes

- The script creates compile-ready placeholders with `TODO` methods.
- You still need to implement exchange-specific REST, websocket parsing, and ticker normalization.
