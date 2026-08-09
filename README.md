# Videostorm

Catalogues movie and show metadata from Emby `.nfo` sidecar files and presents it as paged,
searchable listings. The scope lives in GitHub issue #1 of `bexxxhb/videostorm`.

## Running it

```bash
VIDEOSTORM_ADMIN_USERNAME=admin VIDEOSTORM_ADMIN_PASSWORD=change-me docker compose up --build
```

Then open <http://localhost:8080>. The database is `postgres:17-alpine`; its data lives in the
named volume `pgdata` and survives `docker compose down`. Use `docker compose down -v` to discard
the catalogue.

`VIDEOSTORM_ADMIN_USERNAME` and `VIDEOSTORM_ADMIN_PASSWORD` are required — they log in to the
`/maintenance` area — and have no default; compose refuses to start without them. The password is
BCrypted once at startup and never logged. `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` are
overridable the same way (via the environment or a `.env` file) and do have defaults.

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
