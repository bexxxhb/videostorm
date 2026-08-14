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

### Operating mode

`APPLICATION_OPERATING_MODE` (`application.operating.mode`) selects what is reachable, switchable
purely via `.env` with no rebuild:

- `presentation` — view-only, and the **default** when unset: the maintenance area and login are
  disabled and hidden. `MaintenanceController` and `LoginController` are not registered, the
  security filter chain denies `/maintenance**` and `/login`, and the Maintenance nav link is not
  rendered. The admin credentials above are then unused.
- `maintenance` — full behaviour: the maintenance pages/actions and the login are available.

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

```bash
mvn test      # unit and web-slice tests
mvn verify    # adds the integration tests, which need a running Docker daemon
```

Integration tests (`*IT`) boot the application against a real PostgreSQL via Testcontainers.
Unit and web-slice tests (`*Test`) need no database.

Running the application outside of compose expects PostgreSQL on `localhost:5432` with database,
user and password all set to `videostorm`; override with the usual `SPRING_DATASOURCE_*`
properties. In `maintenance` mode it also needs `VIDEOSTORM_ADMIN_USERNAME` and
`VIDEOSTORM_ADMIN_PASSWORD` in the environment (or `videostorm.admin.username` /
`videostorm.admin.password` as JVM properties) — startup fails, naming the missing one, if either
is absent. The default `presentation` mode has no login and does not require them.

## Layout

The code follows a hexagonal (ports and adapters) arrangement with a DDD domain model. Each
bounded context (`catalogue`, `indexing`, `sources`) keeps its `domain` free of framework
dependencies, exposes `application` use-case services behind inbound/outbound `port`s, and reaches
the outside world only through `adapter`s.

### Root and cross-cutting

```
de.videostorm
├── VideostormApplication                   Spring Boot application entry point
└── config
    ├── PugViewConfiguration                Wires the Pug4j template loader, config and view resolver into MVC
    ├── security
    │   ├── AdminUserDetailsService         Single BCrypt-hashed admin account; registered only in maintenance mode
    │   └── SecurityConfig                  Public catalogue; gates/denies maintenance & login per operating mode
    └── web
        └── OperatingModeViewAdvice         Exposes the maintenanceEnabled flag to every page (hides the nav link)
```

### `sources` — configured source paths

```
sources
├── config
│   ├── SourcePathReachabilityLogger        Logs existence/readability of each configured path once at startup
│   ├── SourcesConfiguration                Builds the validated SourcePaths bean from raw properties
│   └── SourcesProperties                   Binds comma-separated movie/show path config, nulls → empty
└── domain
    ├── SourcePath                          Normalized absolute source location with ancestor-prefix checks
    ├── SourcePaths                         Validated per-type paths rejecting duplicates and nested overlaps
    └── SourceType                          Enum of the indexed media kinds (MOVIES, SHOWS) with labels
```

### `catalogue` — the read/browse side

```
catalogue
├── domain
│   ├── GenreList                           Parses/renders Emby's delimiter-padded genre string with a display limit
│   ├── Movie                               Domain record of the movie subset shown in listings
│   ├── Rating                              Provider rating value with display label
│   ├── SearchTerm                          Trimmed search input deriving title/genre/year match keys
│   ├── Show                                Domain record of the show subset shown in listings
│   ├── ShowStatus                          Enum ENDED/CONTINUING/UNKNOWN mapping Emby nfo status
│   ├── TitleNormalizer                     Lowercase, strip diacritics, collapse non-alphanumerics
│   └── Year                                Release-year value object with explicit unknown (0) state
├── application
│   ├── FetchMovieRawNfoService             Reads one movie's raw .nfo behind the inbound port
│   ├── FetchShowRawNfoService              Reads one show's raw .nfo behind the inbound port
│   ├── ListMoviesService                   Paginates and searches movies with page-clamping
│   ├── ListShowsService                    Paginates and searches shows with page-clamping
│   ├── MoviePage                           One page of movies with pagination metadata
│   ├── MovieSort                           Active movie sort (field + direction), parsed safely from params
│   ├── MovieSortField                      Whitelist of sortable movie columns; Title is the default/fallback
│   ├── ShowPage                            One page of shows with pagination metadata
│   ├── ShowSort                            Active show sort (field + direction), parsed safely from params
│   ├── ShowSortField                       Whitelist of sortable show columns; Title is the default/fallback
│   ├── SortDirection                       Sort direction ASC/DESC with its dir URL param; ASC default
│   └── port
│       ├── in
│       │   ├── ListMoviesQuery             Inbound port for listing movies
│       │   ├── ListShowsQuery              Inbound port for listing shows
│       │   ├── MovieRawNfoQuery            Inbound port for reading one movie's raw .nfo on demand
│       │   └── ShowRawNfoQuery             Inbound port for reading one show's raw .nfo on demand
│       └── out
│           ├── MovieRepository             Outbound port for reading/counting movies
│           └── ShowRepository              Outbound port for reading/counting shows
└── adapter
    ├── in.web
    │   ├── MovieListingController          Public GET controller rendering the movie listing page
    │   ├── MovieRawNfoController           Serves one movie's raw .nfo on demand as plain text; 404 when absent
    │   ├── MovieRow                        Display-ready JavaBean adapting a Movie for the template
    │   ├── MovieSortView                   Per-column SortHeaders plus the active sort as params for the movies form
    │   ├── PaginationLinks                 Computes first/prev/next/last URLs preserving the query
    │   ├── ShowListingController           Public GET controller rendering the show listing page
    │   ├── ShowRawNfoController            Serves one show's raw .nfo on demand as plain text; 404 when absent
    │   ├── ShowRow                         Display-ready JavaBean adapting a Show for the template
    │   ├── ShowSortView                    Per-column SortHeaders plus the active sort as params for the shows form
    │   └── SortHeader                      One sortable column's toggle link and ▲/▼/↕ direction marker
    └── out.persistence
        ├── ListingSort                     Builds the paging Sort: chosen column, nulls last, id tiebreak
        ├── MovieEntity                     JPA entity for the movie table (read path)
        ├── MovieJpaRepository              Spring Data repository with the shared movie search predicate
        ├── MovieRepositoryAdapter          Adapts the JPA repo to the port, escaping LIKE terms
        ├── ShowEntity                      JPA entity for the show table (read path)
        ├── ShowJpaRepository               Spring Data repository with the shared show search predicate
        └── ShowRepositoryAdapter           Adapts the JPA repo to the port, escaping LIKE terms
```

### `indexing` — the scan/ingest side

```
indexing
├── config
│   └── IndexingConfiguration               Single-threaded indexing executor and UTC clock beans
├── domain
│   ├── DerivedTitle                        Folder-based fallback title, stripping media/nfo extensions
│   ├── DuplicateGuard                      Per-run guard skipping movies duplicating title+year or imdb id
│   ├── EpisodeDuplicateGuard               Per-show guard skipping episodes duplicating a season+episode
│   ├── EpisodeNumberParser                 Parses season/episode from a filename via ordered regexes
│   ├── FeatureSelection                    Picks a folder's feature video by prefix/size/name; lists ignored
│   ├── FeatureVideo                        Min-size rule (500 MB) separating a feature film from clips/samples
│   ├── IndexingRun                         Immutable run aggregate with lifecycle transitions
│   ├── ParsedEpisodeNumber                 Parsed season number and episode number
│   ├── ParsedMovie                         Raw Emby movie fields from one .nfo (nullable, with absent())
│   ├── ParsedRating                        One parsed .nfo rating: source, value, max, votes, default
│   ├── ParsedShow                          Raw Emby show fields from one .nfo (nullable, with absent())
│   ├── PremieredYear                       Extracts a four-digit year from the Emby premiered date, else 0
│   ├── RecognizedVideo                     Recognized video extensions; tests whether a filename is video
│   ├── Resolution                          Movie resolution lifted from the feature filename, normalised to e.g. 1080p
│   ├── RunCounts                           Non-negative tally of entries found and indexed
│   ├── RunGapSummary                       Counts title and year field gaps among a run's issues
│   ├── RunIssue                            One questionable finding against a path, with per-type factories
│   ├── RunIssueType                        Enum of the kinds of questionable findings a run records
│   ├── RunReportCsv                        Renders run issues as a BOM-led, semicolon-separated CSV
│   ├── RunStatus                           Enum RUNNING/COMPLETED/FAILED/INTERRUPTED
│   ├── ScanReport                          A scan's RunCounts plus its list of RunIssues
│   ├── SeasonNumber                        Non-negative season value; 0 is Specials
│   ├── Slug                                Identity slug from normalized title plus year
│   ├── StagedEpisode                       Episode season/episode numbers ready for staging
│   ├── StagedMovie                         Movie parsed fields plus catalogue derivations, ready to stage
│   ├── StagedShow                          Show parsed fields plus catalogue derivations, ready to stage
│   └── TechnicalTokens                     Strips extension and release/codec/resolution tokens from a name
├── application
│   ├── IndexingService                     Owns the run lifecycle: preflight, background scan, promote, settle
│   ├── RunReportService                    Read side of run reports: gaps, downloadable ids, CSV export
│   └── port
│       ├── in
│       │   ├── IndexingOverview            Consistent snapshot of run history with derived active run
│       │   ├── IndexingStatus              Inbound port exposing the overview snapshot
│       │   ├── ReconcileRuns               Inbound port marking previous-lifecycle runs interrupted
│       │   ├── RunReportDownload           A report's CSV filename and byte content
│       │   ├── RunReports                  Inbound port for gaps, downloadable ids and CSV export
│       │   ├── TriggerReindex              Inbound port starting a background re-index for a type
│       │   └── TriggerResult               Reindex outcome (started/already-running/unreachable) plus paths
│       └── out
│           ├── CataloguePromotion          Outbound port promoting a type's staging into live in one txn
│           ├── IndexingRunRepository       Outbound port persisting/querying append-only runs
│           ├── LibraryScan                 Outbound port scanning a type, rebuilding staging, returning a report
│           ├── MountPreflight              Outbound port returning unreachable configured source paths
│           ├── MovieStaging                Outbound port clearing/staging movies with per-movie commits
│           ├── RunIssueRepository          Outbound port recording/reading/pruning run issue detail
│           └── ShowStaging                 Outbound port clearing/staging shows and their episodes
└── adapter
    ├── in.lifecycle
    │   └── StartupRunReconciliation        On app-ready, marks runs stranded by a previous JVM interrupted
    └── out
        ├── persistence
        │   ├── CataloguePromotor           Interface: promotes one type's staging into the live catalogue
        │   ├── IndexingRunEntity           JPA entity for the indexing_run table
        │   ├── IndexingRunJpaRepository    Spring Data repo querying runs by status and start order
        │   ├── IndexingRunRepositoryAdapter Adapts the JPA repo to the port, mapping to/from IndexingRun
        │   ├── JdbcMoviePromotion          Transactionally swaps staged movies into live movie tables
        │   ├── JdbcMovieStaging            Writes movies and ratings into staging, each in its own txn
        │   ├── JdbcRunIssueRepository      Batch-writes, reads and prunes per-run issue detail
        │   ├── JdbcShowPromotion           Transactionally swaps staged shows/episodes into live tables
        │   ├── JdbcShowStaging             Writes shows, ratings and episodes into staging
        │   └── RoutingCataloguePromotion   Dispatches promotion to the CataloguePromotor for the type
        └── scan
            ├── EmbyMovieNfoParser          Parses an Emby movie .nfo into a ParsedMovie
            ├── EmbyNfo                      Shared secure XML parsing and field extractors for .nfo files
            ├── EmbyShowNfoParser           Parses an Emby tvshow .nfo into a ParsedShow
            ├── FilesystemMountPreflight     Checks source paths are readable, non-empty directories
            ├── FilesystemMovieScan          Scans movie folders one level deep, staging and recording issues
            ├── FilesystemShowScan           Scans show folders, staging shows and recursively-found episodes
            ├── NfoParseException            Runtime exception for malformed or wrong-rooted .nfo XML
            ├── RoutingLibraryScan           Dispatches a scan to the SourceScan for the type
            └── SourceScan                   Interface: scans one type's library, rebuilding its staging
```

### `maintenance` — the gated admin area

```
maintenance
└── adapter.in.web
    ├── CsrfViewAttributes                  Pushes the Spring Security CSRF token into the Pug4j model
    ├── IndexingRunView                     JavaBean view of a run with display strings for the template
    ├── LoginController                     Serves /login with CSRF and a login-failed flag (maintenance mode only)
    └── MaintenanceController               Shows runs, triggers reindex, downloads a report CSV (maintenance only)
```

The whole area is governed by the operating mode (see *Operating mode* under *Running it*): in the
default `presentation` mode `MaintenanceController` and `LoginController` are not registered and
their routes are denied, so nothing here is reachable; in `maintenance` mode it behaves as above.

Templates live under `src/main/resources/templates` — `layout.pug` (the shared shell),
`movies.pug` and `shows.pug` (public listings), and `login.pug` and `maintenance.pug` (the admin
area, rendered only in `maintenance` mode) — with static assets (`css/`, `js/`) in
`src/main/resources/static`.

Schema changes are Flyway migrations under `src/main/resources/db/migration`. Hibernate never
generates DDL.
