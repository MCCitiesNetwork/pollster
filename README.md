# Pollster

Dialog-based surveys for Paper servers, with an in-game editor, MariaDB storage and a web report viewer.

## Scope

Players answer surveys through Paper dialogs. Staff build, open, close and report on surveys in game.
Results can be published as a link to a static web page. See
`docs/superpowers/specs/2026-10-22-pollster-design.md` for the full design.

## Requirements

- Paper 26.3 or later
- Java 25
- MariaDB 10.5+ or MySQL 8+

## Build

```sh
./gradlew build
```

The plugin jar is written to `pollster-paper/build/libs/`.

## Development database

`./gradlew :pollster-paper:runServer` starts a MariaDB container from `compose.dev.yml` (Docker required)
and points `run/plugins/Pollster/database.yml` at it. `stopDevDb` stops it; `resetDevDb` deletes its data.
