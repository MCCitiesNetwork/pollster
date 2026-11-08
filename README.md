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

## Live-server tests

`./gradlew :pollster-paper:plugwrightTest` builds the plugin, starts a Paper 26.3 test server with the
development MariaDB, and runs the TypeScript specs in `pollster-paper/src/test/e2e/tests/` with
[plugwright](https://plugwright.dev) bots. Docker and Node.js are required. It is not part of `build`.

The bots connect as Minecraft 1.21.11 clients; ViaVersion and ViaBackwards on the test server translate.
`tests/dialogs.ts` reads the dialogs the bots receive and clicks their buttons. The server's log is in
`pollster-paper/src/test/e2e/generated/local/run/logs/latest.log`.
