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

- `VIDEOSTORM_MOVIES_HOST_1` .. `_5` / `VIDEOSTORM_SHOWS_HOST_1` .. `_3` — host directories to mount,
  one per source root (default `./media/movies-1` .. `-5`, `./media/shows-1` .. `-3`), mounted
  read-only at `/media/movies-1` .. `-5` and `/media/shows-1` .. `-3` respectively. Compose wires up
  to 5 movie roots and 3 show roots by default; a slot you don't need can be left at its empty
  default folder. For a network library (NAS/SMB/NFS), point these at the **mount points** — the host
  directories the shares mount onto — not at paths inside the shares. A mount point stays
  present-but-empty while its share is offline, so the container still starts; the compose mounts use
  `create_host_path: false` (no masking stub is fabricated) and `propagation: rslave` (a share that
  (re)mounts on the host after the container started surfaces inside it). When a share is absent the
  container sees an empty directory and a re-index aborts without touching the catalogue, rather
  than the daemon refusing to start the container.
- `VIDEOSTORM_SOURCES_MOVIES` / `VIDEOSTORM_SOURCES_SHOWS` — comma-separated absolute paths the
  application indexes, **inside** the container; they must match the mounts (default the 5
  `/media/movies-N` paths and the 3 `/media/shows-N` paths above). The list accepts any number of
  entries — 5 and 3 are simply what compose wires by default, not an enforced limit. Entries are
  trimmed and normalised; startup fails, naming the offending pair, if any two overlap or a path is
  not absolute. A type with no configured paths does not block startup — its re-index trigger is
  simply shown disabled. Configured paths are logged with their reachability at startup and never
  rendered in the UI.
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
│   └── SourcesConfiguration                Builds the validated SourcePaths bean from the comma-separated movie/show path properties
└── domain
    ├── SourcePath                          Normalized absolute source location with ancestor-prefix checks
    ├── SourcePaths                         Validated per-type paths rejecting duplicates and nested overlaps
    └── SourceType                          Enum of the indexed media kinds (MOVIES, SHOWS) with labels
```

### `catalogue` — the read/browse side

```
catalogue
├── domain
│   ├── CastMember                          Domain record of one cast member (name, optional role and thumbnail)
│   ├── GenreList                           Parses/renders Emby's delimiter-padded genre string with a display limit
│   ├── Movie                               Domain record of the movie subset shown in listings
│   ├── Rating                              Provider rating value with display label
│   ├── SearchTerm                          Trimmed search input deriving title/genre/year match keys
│   ├── Show                                Domain record of the show subset shown in listings
│   ├── ShowStatus                          Enum ENDED/CONTINUING/UNKNOWN mapping Emby nfo status
│   ├── TitleNormalizer                     Lowercase, strip diacritics, collapse non-alphanumerics
│   └── Year                                Release-year value object with explicit unknown (0) state
├── application
│   ├── FetchMovieCastService               Reads one movie's cast behind the inbound port
│   ├── FetchMovieRawNfoService             Reads one movie's raw .nfo behind the inbound port
│   ├── FetchShowCastService                Reads one show's cast behind the inbound port
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
│       │   ├── MovieCastQuery              Inbound port for reading one movie's cast on demand
│       │   ├── MovieRawNfoQuery            Inbound port for reading one movie's raw .nfo on demand
│       │   ├── ShowCastQuery               Inbound port for reading one show's cast on demand
│       │   └── ShowRawNfoQuery             Inbound port for reading one show's raw .nfo on demand
│       └── out
│           ├── MovieRepository             Outbound port for reading/counting movies and reading one movie's cast
│           └── ShowRepository              Outbound port for reading/counting shows and reading one show's cast
└── adapter
    ├── in.web
    │   ├── ActorResponse                   JSON view of one actor (name, role, https-normalized thumbnail) for the layer
    │   ├── MovieCastController             Serves one movie's cast on demand as JSON; 404 when the movie is unknown
    │   ├── MovieListingController          Public GET controller rendering the movie listing page
    │   ├── MovieRawNfoController           Serves one movie's raw .nfo on demand as plain text; 404 when absent
    │   ├── MovieRow                        Display-ready JavaBean adapting a Movie for the template
    │   ├── MovieSortView                   Per-column SortHeaders plus the active sort as params for the movies form
    │   ├── PaginationLinks                 Computes first/prev/next/last URLs preserving the query
    │   ├── ShowCastController              Serves one show's cast on demand as JSON; 404 when the show is unknown
    │   ├── ShowListingController           Public GET controller rendering the show listing page
    │   ├── ShowRawNfoController            Serves one show's raw .nfo on demand as plain text; 404 when absent
    │   ├── ShowRow                         Display-ready JavaBean adapting a Show for the template
    │   ├── ShowSortView                    Per-column SortHeaders plus the active sort as params for the shows form
    │   └── SortHeader                      One sortable column's toggle link and ▲/▼/↕ direction marker
    └── out.persistence
        ├── CastRow                         Projection over movie_actor/show_actor mapping a cast row to a CastMember
        ├── ListingSort                     Builds the paging Sort: chosen column, nulls last, id tiebreak
        ├── MovieEntity                     JPA entity for the movie table (read path)
        ├── MovieJpaRepository              Spring Data repository with the shared movie search predicate
        ├── MovieRepositoryAdapter          Adapts the JPA repo to the port, escaping LIKE terms; reads cast in billing order
        ├── ShowEntity                      JPA entity for the show table (read path)
        ├── ShowJpaRepository               Spring Data repository with the shared show search predicate
        └── ShowRepositoryAdapter           Adapts the JPA repo to the port, escaping LIKE terms; reads cast in billing order
```

### `indexing` — the scan/ingest side

```
indexing
├── config
│   └── IndexingConfiguration               Single-threaded indexing executor and UTC clock beans
├── domain
│   ├── DerivedTitle                        Folder-based fallback title, stripping media/nfo extensions
│   ├── EpisodeDuplicateGuard               Per-show guard skipping episodes duplicating a season+episode
│   ├── EpisodeNumberParser                 Parses season/episode from a filename via ordered regexes
│   ├── FeatureSelection                    Picks a folder's feature video by prefix/size/name; lists ignored
│   ├── FeatureVideo                        Min-size rule (500 MB) separating a feature film from clips/samples
│   ├── IndexingRun                         Immutable run aggregate with lifecycle transitions
│   ├── ParsedActor                         One parsed <actor>: name, optional role, billing order and thumbnail
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
│   ├── StagedMovie                         Movie parsed fields plus catalogue derivations and cast, ready to stage
│   ├── StagedShow                          Show parsed fields plus catalogue derivations and cast, ready to stage
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
        │   ├── JdbcMoviePromotion          Transactionally swaps staged movies and cast into live movie tables
        │   ├── JdbcMovieStaging            Writes movies, ratings and cast into staging, each in its own txn
        │   ├── JdbcRunIssueRepository      Batch-writes, reads and prunes per-run issue detail
        │   ├── JdbcShowPromotion           Transactionally swaps staged shows, episodes and cast into live tables
        │   ├── JdbcShowStaging             Writes shows, ratings, episodes and cast into staging
        │   └── RoutingCataloguePromotion   Dispatches promotion to the CataloguePromotor for the type
        └── scan
            ├── EmbyMovieNfoParser          Parses an Emby movie .nfo into a ParsedMovie
            ├── EmbyNfo                      Shared secure XML parsing and field extractors for .nfo files
            ├── EmbyShowNfoParser           Parses an Emby tvshow .nfo into a ParsedShow
            ├── FilesystemMountPreflight     Checks source paths are readable, non-empty directories
            ├── FilesystemMovieScan          Scans movie folders, staging each (duplicates kept, not skipped) and recording issues
            ├── FilesystemShowScan           Scans show folders, staging shows and recursively-found episodes
            ├── NfoParseException            Runtime exception for malformed or wrong-rooted .nfo XML
            ├── RoutingLibraryScan           Dispatches a scan to the SourceScan for the type
            └── SourceScan                   Interface: scans one type's library, rebuilding its staging
```

### `maintenance` — the gated admin area

```
maintenance
├── domain
│   ├── DuplicateCriterion                  How two movies match: exact imdb id, or lowercased+trimmed original title
│   ├── DuplicateGroup                      Movies sharing one value under one criterion (only 2+ members form a group)
│   ├── DuplicateMember                     One member movie's imdb id, original title and file path, snapshotted
│   ├── DuplicateScanner                    Groups candidates per shared value, unioning the two criteria
│   ├── DuplicateScanRun                    One scan's outcome: timestamp, duration and the groups found
│   ├── DuplicateScanRunSummary             A run's metadata without its groups, for the history table
│   └── ScanCandidate                       One movie reduced to the attributes duplicate detection needs
├── application
│   ├── DuplicateScanService                Runs a scan (read, group, time, persist) and answers the run-result reads
│   └── port
│       ├── in
│       │   ├── DuplicateScanReports        Inbound port: run history, and one run's groups fetched on demand
│       │   └── TriggerDuplicateScan        Inbound port running a synchronous duplicate-movie scan now
│       └── out
│           ├── DuplicateScanCandidates     Outbound port supplying catalogued movies as scan candidates
│           └── DuplicateScanRunStore       Outbound port persisting scan runs and reading them back
└── adapter
    ├── in.web
    │   ├── CsrfViewAttributes              Pushes the Spring Security CSRF token into the Pug4j model
    │   ├── DuplicateGroupResponse          JSON group for the drill-down: criterion label, shared value, members
    │   ├── DuplicateGroupsController        Serves one run's groups as JSON on demand for the drill-down layer
    │   ├── DuplicateMemberResponse          JSON member: imdb id, original title and file path
    │   ├── DuplicateScanRunView            JavaBean view of a scan run with display strings for the template
    │   ├── IndexingRunView                 JavaBean view of a run with display strings for the template
    │   ├── LoginController                 Serves /login with CSRF and a login-failed flag (maintenance mode only)
    │   └── MaintenanceController           Shows runs, triggers reindex and duplicate scans, downloads a report CSV
    └── out.persistence
        ├── DuplicateScanGroupEntity        JPA entity for the duplicate_scan_group table
        ├── DuplicateScanMemberEntity       JPA entity for the duplicate_scan_member table
        ├── DuplicateScanRunEntity          JPA entity for the duplicate_scan_run table
        ├── DuplicateScanRunJpaRepository   Spring Data repo listing runs newest-first
        ├── DuplicateScanRunStoreAdapter    Adapts the JPA repo to the port, mapping to/from DuplicateScanRun
        └── JdbcDuplicateScanCandidates     Projects the movie table to candidates (imdb id, original title, path)
```

The whole area is governed by the operating mode (see *Operating mode* under *Running it*): in the
default `presentation` mode `MaintenanceController` and `LoginController` are not registered and
their routes are denied, so nothing here is reachable; in `maintenance` mode it behaves as above.

Alongside re-indexing, the maintenance page offers a standalone **duplicate-movie scan**. Movies are
deliberately **not** de-duplicated during indexing — a film appearing in two source folders is
catalogued from both — because the source filesystems unavoidably contain doubled entries and the
point is to reveal them rather than silently drop one. The scan finds them: two movies are duplicates
when they share an exact imdb id **or** an original title (compared lowercased and trimmed), grouped
per shared value so a movie can appear under more than one group. Each run is persisted in full and
kept indefinitely; the page lists past runs (duration, group count) and a drill-down link that fetches
that run's groups on demand and lists each member's imdb id, original title and file path.

Templates live under `src/main/resources/templates` — `layout.pug` (the shared shell),
`movies.pug` and `shows.pug` (public listings), and `login.pug` and `maintenance.pug` (the admin
area, rendered only in `maintenance` mode) — with static assets (`css/`, `js/`, `img/`) in
`src/main/resources/static`. The listings keep their per-row detail out of the initial page: the
shared `content-dialog` (wired by `js/content-dialog.js`) opens a title's Plot inline and fetches
its raw `.nfo` and its cast (the "Actors" layer) on demand, the cast rendered from JSON as a block
per performer with the bundled `img/no-actor.svg` placeholder when a portrait is missing. The same
dialog backs the maintenance page's duplicate-scan drill-down, fetching a run's groups as JSON and
rendering each group's member movies on demand.

Schema changes are Flyway migrations under `src/main/resources/db/migration`. Hibernate never
generates DDL. The duplicate-movie scan persists to `duplicate_scan_run`/`_group`/`_member`
(`V15`), and `V16` drops the movie identity/imdb unique indexes so doubled entries can be
catalogued for the scan to reveal (the show tables keep their uniqueness). Because `V8`'s comments
still describe those now-removed movie indexes, they read as stale — `V16` is the corrective
migration.
