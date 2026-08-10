# Videostorm

Catalogues movie and show metadata from Emby `.nfo` sidecar files and presents it as paged,
searchable listings. The scope lives in GitHub issue #1 of `bexxxhb/videostorm`.

## Running it

```bash
docker compose up --build
```

Then open <http://localhost:8080>. The database is `postgres:17-alpine`; its data lives in the
named volume `pgdata` and survives `docker compose down`. Use `docker compose down -v` to discard
the catalogue.

Overridable via the environment (or a `.env` file): `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD`, and `VIDEOSTORM_ADMIN_USERNAME` / `VIDEOSTORM_ADMIN_PASSWORD` (default
`admin` / `changeme`) — the login for the `/maintenance` area, BCrypted once at startup and never
logged. Change the admin credentials for anything beyond local use.

### Source paths and mounts

The library lives behind read-only bind mounts. Point the host paths at your library and, if the
container process needs a specific identity to read them, set the uid and gid:

- `VIDEOSTORM_MOVIES_HOST` / `VIDEOSTORM_SHOWS_HOST` — host directories to mount (default
  `./media/movies`, `./media/shows`), mounted read-only at `/media/movies` and `/media/shows`.
- `VIDEOSTORM_SOURCES_MOVIES` / `VIDEOSTORM_SOURCES_SHOWS` — comma-separated absolute paths the
  application indexes, **inside** the container; they must match the mounts (default
  `/media/movies`, `/media/shows`). Entries are trimmed and normalised; startup fails, naming the
  offending pair, if any two overlap or a path is not absolute. A type with no configured paths
  does not block startup — its re-index trigger is simply shown disabled. Configured paths are
  logged with their reachability at startup and never rendered in the UI.
- `PUID` / `PGID` — uid and gid the container runs as (default `1000`), so it can read files owned
  by your host account.

## Developing

Java 21 is required — `sdk env install` picks it up from [`.sdkmanrc`](.sdkmanrc).

```bash
mvn test      # unit and web-slice tests
mvn verify    # adds the integration tests, which need a running Docker daemon
```

Integration tests (`*IT`) boot the application against a real PostgreSQL via Testcontainers.
Unit and web-slice tests (`*Test`) need no database.

Running the application outside of compose expects PostgreSQL on `localhost:5432` with database,
user and password all set to `videostorm`; override with the usual `SPRING_DATASOURCE_*`
properties. It also needs `VIDEOSTORM_ADMIN_USERNAME` and `VIDEOSTORM_ADMIN_PASSWORD` in the
environment (or `videostorm.admin.username` / `videostorm.admin.password` as JVM properties) —
startup fails, naming the missing one, if either is absent.

## Layout

The code follows a hexagonal (ports and adapters) arrangement with a DDD domain model:

```
de.videostorm
├── config                              Spring wiring that belongs to no single slice
├── sources                             Configured source paths: domain value objects + config binding
└── catalogue
    ├── domain                          Movie, Show and value objects; no framework dependencies
    ├── application                     Use-case services (ListMoviesService, ListShowsService, ...)
    │   └── port
    │       ├── in                      Query interfaces the adapters call into the application
    │       └── out                     Repository interfaces the application calls out through
    └── adapter
        ├── in.web                      HTTP entry points (controllers), rendered with Pug4j
        └── out.persistence             JPA entities and repository adapters
```

Templates live under `src/main/resources/templates` (`layout.pug`, `movies.pug`, `shows.pug`),
with static assets in `src/main/resources/static`.

Schema changes are Flyway migrations under `src/main/resources/db/migration`. Hibernate never
generates DDL.
