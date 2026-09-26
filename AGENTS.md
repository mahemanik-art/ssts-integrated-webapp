# AGENTS.md

## Project
Spring Boot + Thymeleaf website for Sandy Springs Tamil School (SSTS): public
pages (home/about/team/calendar/contact) plus authenticated dashboards for
admin/staff, volunteers, and parents. Single Gradle module, deployed via
Docker to Render (Postgres backend).

## Stack
- Java 21 (Gradle toolchain), Gradle Groovy DSL, single module (no submodules)
- Spring Boot 3.4.3: Web MVC, Thymeleaf, Spring Data JPA, Spring Security,
  Validation, Mail, Cache (Caffeine)
- PostgreSQL (runtime), hosted on Render
- Frontend: server-rendered Thymeleaf templates (not a separate SPA) +
  plain CSS in src/main/resources/static/css
- No Flyway/Liquibase -- schema lives in sql/schema.sql and sql/data.sql,
  applied via `hibernate.ddl-auto=update` locally or the custom
  `executeSchema` Gradle task (runs psql directly) for one-off application

## Build & run
- Config is profile-based YAML: application.yml (base) + application-{dev,stage,prod}.yml.
  Active profile = SPRING_PROFILES_ACTIVE env var, defaults to dev. If BOTH
  application.properties and application.yml exist, .properties wins and the YAML is
  ignored -- do not re-add a .properties file.
- DB credentials and other secrets are NOT in the repo -- application.yml requires
  DB_USERNAME/DB_PASSWORD (no default; fails fast if unset). The single source of
  truth for local dev values is .vscode/launch.json.
- Run app (VS Code): use the Run and Debug panel, not the inline "Run" CodeLens
  above main() -- the CodeLens bypasses launch.json and won't set env vars.
- Run app (terminal):   ./gradlew bootRun
  (or ./scripts/run-dev.sh). The bootRun task itself reads the env vars out of
  .vscode/launch.json and injects them into the forked JVM, so plain bootRun just
  works -- no manual env export needed. launch.json is JSONC (it has `//` comments),
  so it must be comment-stripped before strict JSON parsing. If those vars are ever
  missing, the app dies with "password authentication failed for user ${DB_USERNAME}"
  and Gradle's generic "non-zero exit value 1" -- scroll UP to the real Spring error;
  that Gradle line alone is never diagnostic.
- Build jar:         ./gradlew build
- All tests:         ./gradlew test
- Single test class: ./gradlew test --tests "org.sstamilschool.service.TeamServiceCacheTest"
- Apply SQL schema:  ./gradlew executeSchema   (needs DB_HOST/DB_NAME/DB_USERNAME/DB_PASSWORD env vars; uses psql)
- Format templates/CSS: npm run format         (prettier; Java has no linter/formatter configured)
- Check formatting:  npm run lint
- Docker build:      docker compose up --build (see Dockerfile: multi-stage, eclipse-temurin:21;
                     docker-compose passes SPRING_PROFILES_ACTIVE, default dev)

## Structure map
```
src/main/java/org/sstamilschool/
  SstsIntegratedWebappApplication.java  -- entry point
  config/         -- SecurityConfig (form login, method security), CacheConfig (Caffeine)
  controller/     -- @Controller (page/view) and @RestController (api) classes, thin
  service/        -- business logic; @Transactional and @Cacheable live here
  repository/     -- Spring Data JPA interfaces (SstsXxxRepository)
  model/          -- JPA @Entity classes (SstsUser implements UserDetails directly)
  dto/            -- only 2 classes exist here (RegisterRequest, CalendarView) --
                     most controllers pass entities straight to the Thymeleaf
                     Model; there is no consistent DTO boundary, don't assume one
  util/           -- small helpers (e.g. AcademicYear)
src/main/resources/
  templates/      -- Thymeleaf views; admin/, parent/ subfolders for role dashboards
  static/css/     -- many theme .css files (dark-gold, midnight-teal, etc.) -- check
                     which is actually wired in templates before editing styles
  application.yml      -- base config (shared) + spring.profiles.active
  application-dev.yml   -- local: SQL logging, ddl-auto=update, template cache OFF
  application-stage.yml -- staging: SQL off, ddl-auto=update, template cache ON
  application-prod.yml  -- prod: SQL off, ddl-auto=validate, template cache ON
src/test/java/org/sstamilschool/  -- mirrors main package structure
sql/              -- schema.sql, data.sql, staff_profiles_seed.sql (hand-run SQL, no migration tool)
```

## Conventions actually followed in this code
- Controllers call services directly -- no facade/mediator layer.
- No centralized exception handler (no @ControllerAdvice found). Errors are
  handled ad hoc per-method, e.g. PageController.contactSubmit() wraps the
  call in try/catch and redirects with an error query param.
- Auth: SstsUser (the JPA entity) implements Spring Security's UserDetails
  directly and is pulled from SecurityContextHolder in controllers
  (see DashboardController) -- entities double as security principals.
- Caching: service methods use @Cacheable/@CacheEvict with cache names
  defined as constants in CacheConfig (TEAM_MEMBERS, CALENDAR_EVENTS). Both are
  in-memory Caffeine with a 24h TTL (app.cache.*-ttl-hours), so direct SQL edits
  to ssts_users/ssts_calendar_events are NOT visible until the TTL expires or the
  matching PATCH cache endpoint is called (team -> /api/team/cache,
  calendar -> /api/calendar/cache) or the app restarts.
- Role-based routing: DashboardController switches on user.getUserType()
  ("admin"/"staff" vs "volunteer" vs default-parent) to pick a template.

## Gotchas (learned the hard way -- check here before debugging from scratch)
- **Port 8081 / `webServerStartStop`:** "Failed to start bean 'webServerStartStop'" almost
  always means the port is already taken, NOT a code bug. `bootRun` forks a JVM that can
  survive stopping the Gradle wrapper, leaving a stray listener. Clear it with
  `lsof -ti tcp:8081 | xargs kill -9`. Run the app from ONE place (IDE or bootRun, not both).
- **JPA derived queries on booleans:** SstsCalendarEvent's field is `active` (getter
  `isActive()`), so the derived method must be `...AndActiveTrue...`, NOT `...AndIsActiveTrue...`
  (the latter throws "No property 'isActive' found" and fails the whole context).
- **Hibernate-created tables lack the column defaults declared in schema.sql.** With
  `ddl-auto=update`, a table first created from an entity gets `is_active`/`created_at`/
  `updated_at` as NOT NULL but with NO default, so a hand-written INSERT that omits those
  columns fails with "null value in column ... violates not-null constraint" even though
  schema.sql shows `DEFAULT`. schema.sql now carries idempotent `ALTER TABLE ... SET DEFAULT`
  statements for ssts_calendar_events to close this gap; keep them when editing that table.
- **SstsUser.profile must stay a PERSISTENT @OneToOne** (not `transient`): the query
  `SstsUserRepository.findPublicTeamMembers()` does `LEFT JOIN FETCH u.profile`. Marking
  the field `transient` makes that path unresolvable and the app won't start.
- **@WebMvcTest slices need a @MockitoBean for EVERY service the controller injects.**
  PageController injects EmailService + TeamService + CalendarService; add a mock for each
  or the slice fails to load the context.
- **Avoid @SpringBootTest when a unit/slice will do:** it boots JPA and needs live DB
  credentials (fails with "password authentication failed for user ${DB_USERNAME}" without
  them). Prefer @SpringJUnitConfig wiring just CacheConfig + the service with a mock
  repository, or @WebMvcTest -- both are fast and DB-free.
- **CSS logo sizing is a chain; last rule wins (equal specificity).** The header logo ends
  up `min(490px, 44vw)` from logo-size-large.css. A per-page inline `<style>` in a template
  placed after the stylesheet links will override it (this is why contact.html's logo
  rendered at a fixed 190px until the override was removed).
- **Mocked-bean tests share state across methods** (singleton context): reset invocation
  counts with `clearInvocations(...)` in @BeforeEach or verify() counts leak between tests.

## Do NOT read or modify
- build/, .gradle/, bin/  -- generated/IDE build output, always stale
- node_modules/           -- only dependency is prettier, for CSS/HTML formatting
- .kilo/                  -- another AI tool's worktree metadata, unrelated to app code
- c.txt, c2.txt, r.html   -- stray scratch files at repo root, not part of the app

## Where to look for X
- Auth / login flow: config/SecurityConfig.java, service/LoginService.java,
  service/SstsUserDetailsService.java, templates/login.html
- Password reset: service/PasswordResetService.java, model/SstsPasswordResetToken.java,
  templates/forgot-password.html, templates/reset-password.html
- DB schema / seed data: sql/schema.sql, sql/data.sql, sql/staff_profiles_seed.sql
- Frontend entry points: templates/index.html (home), templates/page.html
  (shared layout used by about/team/calendar/contact per PageController's PAGES map)
- Role dashboards: controller/DashboardController.java, templates/admin/dashboard.html,
  templates/parent/dashboard.html
- Team page caching (has its own reload endpoint): service/TeamService.java,
  controller/CacheAdminController.java (PATCH /api/team/cache and
  PATCH /api/calendar/cache, both admin-only). It is named CacheAdminController,
  not TeamCacheController -- it serves both caches.
- Calendar page caching: service/CalendarService.java (reads AcademicYear + CalendarView)
- Calendar seed data: sql/data.sql has a "BEGIN calendar seed" block -- ~40 INSERTs
  for academic_year 2026-2027 (working + holiday/break events) transcribed from the
  Marietta Tamil School academic calendar, wrapped in a DELETE for that year so it is
  idempotent. /calendar only shows the CURRENT AcademicYear (Aug-Jul, via util/AcademicYear),
  so a new year needs a new seed block or the page shows its "being finalized" empty state.
- Per-profile config: application.yml (base) + application-{dev,stage,prod}.yml;
  docker-compose passes SPRING_PROFILES_ACTIVE (default dev); Render should set it to prod
- Docker/deploy: Dockerfile, docker-compose.yml

## Resolved / still worth knowing
- application.properties was migrated to profile-based application.yml (dev/stage/prod).
  The old .properties file is intentionally gone -- do not re-add it (see Build & run).
- application.yml no longer contains hardcoded DB credentials (now ${DB_USERNAME}/
  ${DB_PASSWORD} with no default). .vscode/launch.json's mainClass was also fixed (it
  pointed at a nonexistent org.sstamilschool.web package; the real package is
  org.sstamilschool).
- The DB password was committed in plaintext at some point in this repo's
  history. If this repo has ever been pushed to a remote (GitHub, etc.), that
  password is exposed in commit history even though the current file is clean
  -- it should still be rotated on the Render dashboard if that hasn't
  happened yet. Don't assume "removed from the current file" means "safe."
