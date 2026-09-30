# AGENTS.md

## Project
Spring Boot + Thymeleaf website for Sandy Springs Tamil School (SSTS): public
pages (home/about/team/calendar/gallery) plus authenticated dashboards for
admin/staff, volunteers, and parents, and a `/superadmin/**` module space for
the nine admin CRUD/report screens. Single Gradle module, deployed via Docker to
Render (Postgres backend).

## Stack
- Java 21 (Gradle toolchain), Gradle Groovy DSL, single module
- Spring Boot 3.4.3: Web MVC, Thymeleaf, Spring Data JPA, Spring Security,
  Validation, Mail, Cache (Caffeine)
- PostgreSQL (runtime), hosted on Render
- Server-rendered Thymeleaf templates + plain CSS in
  `src/main/resources/static/css`. No SPA, no build step, no JS framework.
- No Flyway/Liquibase. The schema is `sql/schema.sql` (declarative, final-shape
  `CREATE TABLE`s), applied by the custom `executeSchema` Gradle task which runs
  psql directly. `ddl-auto=update` under the dev profile creates entity-backed
  tables but NOT the school-system tables, and silently no-ops against any
  `CREATE TABLE IF NOT EXISTS` colliding with a table Hibernate already made.

## Build & run
- Config is profile-based YAML: `application.yml` (base) +
  `application-{dev,stage,prod}.yml`. Active profile = `SPRING_PROFILES_ACTIVE`
  env var, defaults to dev. If both `application.properties` and
  `application.yml` exist, `.properties` wins and the YAML is ignored -- do not
  re-add a `.properties` file.
- DB credentials are NOT in the repo. `application.yml` requires
  `DB_USERNAME`/`DB_PASSWORD` with no default (fails fast if unset). The single
  source of truth for local dev values is `.vscode/launch.json`.
- Run (VS Code): use the Run and Debug panel, not the "Run" CodeLens above
  `main()` -- the CodeLens bypasses `launch.json` and won't set env vars.
- Run (terminal): `./gradlew bootRun`. The task reads `launch.json` (JSONC, has
  `//` comments -- strip them before strict JSON parsing) and injects those vars
  into the forked JVM. **It overrides any env vars you pass, and those point at
  PRODUCTION -- see Gotchas.**
- Build jar `./gradlew build` | tests `./gradlew test` | one class
  `./gradlew test --tests "org.sstamilschool.service.TeamServiceCacheTest"`
- Apply schema: `./gradlew executeSchema` (needs DB_HOST/DB_NAME/DB_USERNAME/
  DB_PASSWORD; psql is installed, so this runs for real). To rehearse DDL without
  touching a real database, use a throwaway cluster:
  ```bash
  initdb -D /tmp/verify/data -U verify --auth=trust -E UTF8
  pg_ctl -D /tmp/verify/data -o "-p 55432 -k /tmp/verify -c listen_addresses=''" -w start
  # ...run schema.sql + seeds against it (schema must be idempotent)...
  pg_ctl -D /tmp/verify/data stop -m immediate && rm -rf /tmp/verify
  ```
- Format `npm run format` | check `npm run lint` (prettier, CSS+HTML only -- it
  does NOT cover `.js`; use `node --check <file>` for that)
- Docker: `docker compose up --build` (multi-stage eclipse-temurin:21; passes
  SPRING_PROFILES_ACTIVE, default dev)

## Structure map
```
src/main/java/org/sstamilschool/
  SstsIntegratedWebappApplication.java  -- entry point
  config/         -- SecurityConfig, CacheConfig, RoleAwareAuthenticationSuccessHandler
  controller/     -- thin. Page/Dashboard/Login/CommonModelAdvice + the nine
                     /superadmin/** controllers (URL-guarded + @PreAuthorize)
  service/        -- business logic; @Transactional and @Cacheable live here
  repository/     -- Spring Data JPA interfaces (SstsXxxRepository)
  model/          -- JPA @Entity classes (SstsUser implements UserDetails directly)
  dto/            -- 4: RegisterRequest, CalendarView, ReportsView, DonorCard
                     (the public carousel's 4-field view of a donor, so the JSON
                     payload cannot publish notes/seedKey/timestamps).
                     There is
                     no consistent DTO boundary; most controllers pass entities
                     straight to the model. Don't assume one.
  util/           -- SchoolTime, AcademicYear
src/main/resources/
  templates/      -- Thymeleaf. admin/, parent/, superadmin/ role dashboards.
                     superadmin/{calendar,team,gallery,announcements,users,roles,
                     donors,donations}/ each hold list.html + form.html;
                     superadmin/reports/index.html
                     is read-only. fragments/nav.html is the ONE navigation.
  static/css/     -- theme files; check which are actually linked before editing.
                     admin.css is shared super-admin table/form/alert styling for
                     all nine screens -- don't re-add a per-module copy.
  static/js/      -- gallery.js, inline-delete.js, nav-dropdown.js,
                     donor-carousel.js (IIFE, 'use strict', plain DOM, no
                     modules/build step). /js/** must stay
                     in SecurityConfig's permitAll or public pages 302 to /login.
src/test/java/org/sstamilschool/  -- mirrors main package structure
sql/              -- EXACTLY TWO FILES: schema.sql and data.sql. schema.sql is
                     declarative final shape; data.sql is the single seed. The
                     former staff_profiles_seed.sql and grant_super_admin.sql
                     were absorbed into data.sql, and the superseded
                     merge_user_profiles_into_users.sql was deleted. Do not
                     re-split them and do not add a third script (see Database)
```

## Conventions

### Dates and time
- **Never `LocalDate.now()` for anything a user sees or compares against stored
  dates -- use `util/SchoolTime.today()`.** The app runs in UTC on Render; the
  school is US Eastern, so the server's date runs 4-5h ahead of the school's for
  part of every evening. That desynchronises `expires_on >= today` announcements
  and the Aug-1 academic-year rollover. `SchoolTime` reads `APP_TIMEZONE` and
  falls back to America/New_York. ReportsService and AnnouncementService both
  use it, so they cannot disagree.

### Data model rules -- do not violate these
These are the decisions most likely to look like mistakes to a future agent.
- **All 21 per-person columns are on `ssts_users`** (bio, avatarUrl, phone,
  address_line1/2, city, state, zipCode, country, alternateEmail, dateOfBirth,
  occupation, employer, yearsInCommunity, the four prior_* fields,
  certifications, interests, department). There is no `SstsUserProfile` entity,
  no `ssts_user_profiles` table and no `ssts_users.profile_id` column. Do not
  reintroduce a second row: "save the user, then save its profile" is now a bug
  because there is nothing to save. A user create/update is a single `save`, and
  a user delete is a single `deleteById` -- no child row to clear first.
- **`ssts_families` is ONE ROW PER FAMILY, not per person.** It holds BOTH
  parents (`parent1_*`/`parent2_*`, `phone1`/`phone2`) plus the shared address,
  so a family with two parents is a single row. `user_id` is NULLABLE so a family
  can exist before any parent has a portal login; UNIQUE still holds because
  Postgres allows many NULLs.
  - **`user_id` is `ON DELETE SET NULL` and must stay that way.** `CASCADE`
    collides with `ssts_students.family_id ON DELETE RESTRICT`: deleting a
    parent's login would try to delete the family row, RESTRICT blocks it
    because the family has enrolled students, and the whole `DELETE FROM
    ssts_users` fails with an opaque foreign-key error -- so the account isn't
    deleted either. CASCADE buys nothing and makes the operation impossible.
    SET NULL unlinks, which matches the other optional links in `ssts_students`
    (`admitted_grade_id`, `current_grade_id`, `doctor_id`). The `SstsFamily`
    entity declares no `cascade` on its `@OneToOne`; the DB constraint owns this.
  - `ssts_families` is the authoritative source for a parent's phone and
    address. `ssts_users.phone`/`address_line1` are for staff/volunteer/admin
    only -- do not read or write them for `user_type = 'parent'` rows.
    **This rule now has TWO enforcement points and both are required:**
    `UserAdminService.copyPerPersonFields` (the admin screens) skips every
    contact field when the form's userType is 'parent', and `ParentService`
    (registration) writes them to the family instead of the user. They drifted
    once already -- registration wrote the contact block onto `ssts_users` while
    the admin screen refused to, so the same field was readable from two places
    with two different answers. Any NEW path that creates or edits a parent must
    go through `ParentService`, not inline the fields.
    `UserAdminService` skips rather than nulls, so demoting a staff account to
    'parent' does not destroy the values.
  - **The parent module is `SstsFamily` + `ParentService` + `SstsFamilyRepository`.**
    It is deliberately NOT a separate `ssts_staffs`/`ssts_parents` table pair:
    `ssts_users` stays the single login row (username/email/password can only
    live in one place), and `user_type` stays routing-only. Staff subtypes
    (teacher, treasurer, volunteer) are ROLES, not tables -- a treasurer is one
    `INSERT INTO ssts_roles (name, ...)` and `getAuthorities()` hands out
    `ROLE_TREASURER` with no code or schema change. Do not reintroduce
    `ssts_teachers` / `ssts_staff_details`; see the parallel-tables rule above.
  - **`ssts_families` has ONE `street` column, not addressLine1/2.**
    `ParentService.composeStreet` joins the two form lines with ", ". This is
    deliberately lossy: it is a contact/display field, never queried by address.
    Do not add an `address_line2` column without deciding whether the existing
    single-line values need splitting -- that is a schema change plus a
    migration for every environment.
  - **Parent registration no longer asks for staff credentials.**
    `RegisterRequest` has no `priorTeachingExperience` and no `certifications`;
    those belong to a staff profile and are added by an administrator. It DOES
    still collect `occupation`, `employer`, `yearsInCommunity`, `bio` and
    `interests`, because those are facts about a person rather than about a
    job -- do not "tidy" them away just because the form is for parents.
- **The student FK runs `ssts_students.family_id` -> `ssts_families`, never the
  reverse.** It is `NOT NULL` with `ON DELETE RESTRICT`, deliberately: deleting a
  family that still has enrolled students is a mistake, and `CASCADE` would
  delete the students. `SET NULL` is impossible on a NOT NULL column. A
  `student_id` on `ssts_families` would cap each row at one student, forcing
  sibling rows with duplicated contact details.
- **Health and safety data is per STUDENT, not per family.**
  `pickup_authorized`, `medical_conditions`, `allergies`, `insurance_info` live
  on `ssts_students` -- siblings share one family row, so per-family would mean
  one child's peanut allergy overwriting the other's. `emergency_contact` stays
  on the family; that one is household-level.
- **There are no parallel teacher/staff tables.** A teacher is a `ssts_users`
  row with `user_type = 'staff'`, and `ssts_grades.teacher1_id`/`teacher2_id`
  reference `ssts_users(id)`. `ssts_teachers` and `ssts_staff_details` must not
  come back. When listing grade teachers, filter to `user_type = 'staff'` -- the
  FK alone would let a grade be assigned to a parent.
- **`parent_type` is a per-student column on `ssts_students`,** not on the family:
  a family is one row holding both parents, so one value there could describe
  only one of them. Known gap: this captures one relationship per student;
  recording both parents needs a `ssts_student_parents` join table.
- **Do not re-add the redundant free-text student/grade columns** --
  `ssts_families.student_name`/`student_grade`/`student_class`,
  `ssts_students.grade`/`class_level`, `ssts_students.parent_name`/`parent_phone`/
  `parent_email`. Grade was represented three ways and every representation
  drifted. Sources of truth: `ssts_students.first_name`/`last_name` and
  `current_grade_id`/`admitted_grade_id`. Note these are absent from the
  `CREATE TABLE`s, so an existing database will still have them, harmlessly
  unused -- only a drop-and-recreate removes them.
- **The school-system tables are derived from the PRSSTSCRMDB export**
  (`u877669764_prsstscrmdb.pdf`). schema.sql's header block lists the
  deliberate deviations (UUID-as-text PKs -> bigint identity, `year(4)` ->
  INTEGER, `tinyint(1)`/`char(1)` -> BOOLEAN, plus fixed source typos). Read it
  before treating a column as authoritative. Two deviations are open questions,
  not settled: `ssts_gradelevels.name` is an INTEGER ("Kindergarten" is not a
  number) and `ssts_grades.teacher2_id` was relaxed to nullable (a vacant
  co-teacher slot is real). The PDF's `parent`/`user`/`teacher` tables are
  deliberately not created -- `ssts_families`, `ssts_users` and
  `user_type='staff'` cover them.

### Security and auth
- Super-admin is the existing `ssts_roles` row named `super_admin`, granted via
  `role_id`. `SstsUser.getAuthorities()` derives `ROLE_SUPER_ADMIN` from it --
  no boolean flag, no extra userType, no parallel system. `userType`
  (CHECK-constrained to parent/staff/volunteer/admin) is routing-only, never
  authorization.
- **The role NAME is the load-bearing column**, because authorities are derived
  as `"ROLE_" + name.toUpperCase()`:
  - Must match `^[a-z][a-z0-9_]*$` (max 50) -- a space, dot, dash or uppercase
    builds a broken or colliding authority. Enforced in Java (RoleAdminService)
    AND in the DB (`chk_ssts_roles_name_format`).
  - Case-insensitive collisions are refused (`countByNameIgnoreCase`): `admin`
    and `ADMIN` both yield `ROLE_ADMIN`.
  - **A role name is immutable after creation** -- renaming would silently
    detach every holder. The `@InitBinder("role")` denies `name`, create reads
    it from a separate `@RequestParam("name")`, and the edit form renders no
    name input at all.
  - **`super_admin` is immutable in both directions** (RoleAdminService
    .PROTECTED_ROLE): it is the sole source of ROLE_SUPER_ADMIN, so it cannot be
    renamed, deleted, deactivated, or have privileges reduced.
  - **Deleting a role users still hold is refused by catching the DB's FK
    violation, not by pre-counting.** `ssts_users.role_id` has NO
    `ON DELETE CASCADE`, so the `DataIntegrityViolationException` *is* the
    constraint, and it is race-free. Do not "improve" it with a count.
  - `SstsRole`'s 21 `can_*` booleans are **not read by anything.** Real
    authorization is `getAuthorities()` and `ROLE_SUPER_ADMIN` alone; the flags
    are maintenance-only. Setting `canDeleteUsers` grants nothing.
    All 21 are copied with explicit setters (no BeanUtils) -- adding a flag means
    editing `create` AND `update` in RoleAdminService AND roles/form.html, plus
    `RoleAdminService.enabledPrivilegeCount`'s if-chain. Forgetting the template
    fails silently.
- **Module-space URL protection** (e.g. `/superadmin/**`):
  1. In SecurityConfig add `.requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")`
     BEFORE `anyRequest().authenticated()`. Unauthenticated users are redirected
     to /login with a SAVED REQUEST (not 403) and continue there after login.
  2. Authenticated non-super-admins get 403.
  3. Controllers in the space ALSO carry
     `@PreAuthorize("hasRole('SUPER_ADMIN')")` as defense in depth.
- **Post-login routing has one owner: `config/RoleAwareAuthenticationSuccessHandler`**
  (wired as `.successHandler(...)`). It checks its `HttpSessionRequestCache` for
  a saved request FIRST (load-bearing -- a protected URL hit while logged out
  must continue there after login); only with no saved request does it choose
  `/superadmin` for ROLE_SUPER_ADMIN, else `/dashboard`.
  `.defaultSuccessUrl` is not used. Do not add a `POST /login` handler --
  `UsernamePasswordAuthenticationFilter` always consumes that route.
- Logout is `POST /logout` (CSRF auto-injected by Thymeleaf on `th:action`
  forms). Never link to `/logout` with a plain `<a href>` in Spring Security 6
  (404/405). Session invalidated, JSESSIONID deleted, redirect to `/`.
- Grant/revoke: there is NO grant script. `super_admin` is granted by the
  `ssts_admin` row in `data.sql`, which is deliberately seeded with the
  `super_admin` role. Its `password_hash` is the placeholder
  `$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH` (31 chars, NOT a valid bcrypt, so the
  seeded account cannot log in) -- set a real hash yourself before deploying,
  with the `htpasswd -bnBC 10 "" 'pw'` recipe in data.sql's header. Never
  hardcode a real hash in the repo. **Rotate the super-admin password before any
  public deployment.** The DB password was once committed in plaintext, so it is
  exposed in git history and must be rotated on Render regardless of how clean
  the current file is.
- `SstsUser` (the entity) implements `UserDetails` directly and is pulled from
  `SecurityContextHolder` in controllers -- entities double as principals.
- `DashboardController` routes on `user.getUserType()` ("admin"/"staff" vs
  "volunteer" vs default-parent) to pick a dashboard template.

### Super-admin forms are mass-assignment surfaces
`create`/`update` bind the whole request onto `SstsUser`, so a crafted POST can
set any property the form doesn't render. Two independent layers guard the auth
fields, and **both are required**:
- An `@InitBinder` denying them, AND
- the service unconditionally assigning the role rather than honouring the
  bound one.

- **Team** (`TeamAdminController`): binder denies `role`, `role.id`, `role.name`,
  `passwordHash`, `emailVerified`; `TeamAdminService.create` assigns
  `DEFAULT_ROLE` unconditionally. Locked in by
  `createRejectsAClientSuppliedRole` in both TeamAdmin*Test classes.
- **Users** (`/superadmin/users`) is a different trust boundary: binder denies
  `role`, `role.id`, `role.name`, `passwordHash`, `lastLogin`, `createdAt`,
  `updatedAt`, and the role arrives as a separate `@RequestParam("roleId")`
  resolved through `SstsRoleRepository`. It **deliberately does NOT deny
  `emailVerified`, unlike the team binder** -- marking an account verified is the
  entire point of a user-administration screen, so this is the one sanctioned
  place to set it. Do not "fix" it by copying the team binder; the two modules
  get opposite answers on the same field on purpose.
- **The users module enforces the DB's constraints server-side, because a
  `<select>` offering four values is not a whitelist:** `user_type` re-validated
  against exactly parent/staff/volunteer/admin (matching the CHECK);
  username/email uniqueness pre-checked (`existsByUsername`/`existsByEmail` ->
  IllegalArgumentException) *plus* a controller-side
  `DataIntegrityViolationException` catch for the check-then-act race; column
  limits re-checked (username 50, email 100, fullName 100, designation 100)
  because `maxlength` is client-side only. Password minimum is 12, enforced in
  the controller (`UserAdminController.MIN_PASSWORD_LENGTH`), not just the form's
  `minlength`.
- **Two lockout paths are refused by UserAdminService:** (a) SELF-protection --
  it refuses to delete, deactivate or re-role the ACTING user (principal
  resolved exactly as in `TeamAdminController.currentUserId()`); (b)
  LAST-SUPER-ADMIN -- it refuses to delete, deactivate or demote the final holder
  of `super_admin`, via `countByRoleName`.
- **CRUD convention (calendar/team/gallery/announcements):** the ENTITY is the
  form object (`@ModelAttribute("event"/"member")`), the controller validates
  required fields and redirects with a flash message, and the SERVICE maps
  form -> entity and evicts the cache. `bio`/`avatarUrl` are flat `@RequestParam`
  on the team controller (not nested path binding) -- harmless legacy shape.
- **`/superadmin/users` is the ONLY screen that edits the per-person columns**
  (dateOfBirth, bio, occupation, employer, department, alternateEmail,
  avatarUrl, phone, the address block, the four `prior_*` fields,
  certifications, interests). `/superadmin/team` covers only
  fullName/designation/bio/avatarUrl, so anything else on a staff or volunteer
  row is maintained here or nowhere. Both `create` and `update` funnel through
  the single `copyPerPersonFields` helper -- never add a second copy of that
  list, or the two paths will drift. Adding a per-person column to `SstsUser`
  means editing that helper, `validatePerPersonFields`, and users/form.html;
  `UserAdminControllerTest.editFormRendersEveryPerPersonField` fails if you skip
  the template. The lengths are re-checked server-side because `maxlength` is
  client-side only. `static/js/user-form.js` greys out the contact block when
  userType is 'parent' -- presentational only, the service is the enforcement
  point.
- **No centralized exception handler** (no @ControllerAdvice except
  `CommonModelAdvice`, which only supplies nav model attributes). Errors are
  handled ad hoc per-method, e.g. `PageController.contactSubmit()` wraps the
  call in try/catch and redirects with an error query param.

### Caching
- `@Cacheable`/`@CacheEvict` live in services; cache names are constants in
  CacheConfig (TEAM_MEMBERS, CALENDAR_EVENTS, GALLERY_EVENTS). All in-memory
  Caffeine, 24h TTL (`app.cache.*-ttl-hours`). Direct SQL edits to
  `ssts_users` / `ssts_calendar_events` / `ssts_gallery_events` are invisible
  until the TTL expires, the matching PATCH cache endpoint is called (team ->
  `/api/team/cache`, calendar -> `/api/calendar/cache`, gallery ->
  `/api/gallery/cache`), or the app restarts.
- **Announcements are deliberately UNCACHED.** `expires_on` is time-based, so a
  cached list would render an announcement for a full TTL *after* it expired and
  would delay a new notice by up to 24h. The query is one indexed read over a
  few rows. If caching is ever wanted, use a TTL in MINUTES, not
  `app.cache.*-ttl-hours`. Consequence: `AnnouncementAdminService` has no
  `@CacheEvict` because there is nothing to evict.
- **The users, roles and reports modules are deliberately UNCACHED** -- no
  `@Cacheable`, no `@CacheEvict`, no PATCH endpoint. They're administrative
  state read once per page view over a handful of rows; a 24h TTL would show an
  admin stale accounts, roles or counts. Reports must be live. If you cache one,
  add the matching PATCH eviction in the same change.
- The PATCH cache endpoints are **not** redundant: they exist for out-of-band
  changes that bypass the service and therefore cannot evict -- hand-run SQL
  (`sql/data.sql`'s staff block UPDATEs `ssts_users` directly), manual DB
  edits, future bulk imports. They're ADMIN *or* SUPER_ADMIN, while the CRUD
  screens are SUPER_ADMIN only.
- **Announcement visibility is defined once** in
  `SstsAnnouncement.isVisibleOn(today)`: active AND (`expires_on` IS NULL OR
  `expires_on >= today`). `expires_on` is INCLUSIVE -- it shows through the
  expiry date and hides the next day. The admin list applies NO filter
  (`findAllByOrderByAnnounceDateDesc`) so expired rows stay editable, and its
  "Showing" column calls `isVisibleOn(today)`.

### Donors and donations
Two tables, one module space, and four rules that the other seven screens do
not share. `ssts_donors` is the public-facing curated list; `ssts_donations` is
the append-only gift ledger that backs every number shown about it.
- **A donor is NOT a user.** There is no FK to `ssts_users` and there must not
  be one. A donor is an external party with no portal login, and donors are
  sometimes anonymous, which a `ssts_users` row cannot express. Creating login
  accounts for donors would put accounts nobody asked for on the users table.
- **NO total-giving column exists anywhere**, on the donor row or the ledger.
  Every total -- per donor, per year, overall -- is summed in Java at read time
  in `DonorService`. A stored total is a second source of truth that can only
  disagree with the ledger, and nothing recalculates it on add/edit/delete. This
  is why `SstsDonationRepository` deliberately has no `sumByAmount`: a SQL SUM
  also hands back a different numeric type that is easy to widen by accident.
- **`publiclyListed` is a CONSENT flag, not a display toggle, and it defaults to
  FALSE.** It decides whether a donor's name reaches the public home page. The
  gate lives in ONE place, the `findPubliclyListed` query (active AND
  publiclyListed), and `HomeDonorCarouselTest` pins the consequence: a donor the
  service excludes never reaches the page. `active` is a separate axis --
  "still a donor we work with" vs "may we name them".
- **Money is `BigDecimal`, never double or float,** and `SstsDonation.setAmount`
  enforces scale 2 with `RoundingMode.UNNECESSARY`, which THROWS on a third
  decimal place instead of quietly rounding a gift. There is exactly ONE
  `setAmount` overload: a `setAmount(String)` would make the property ambiguous
  for Spring's data binder, which could then pick either.
- **Deleting a donor with gifts is refused** (`countByDonorId > 0`), pointing at
  deactivation instead. The service check exists for a readable message; the
  database's `ON DELETE RESTRICT` is the actual guarantee. A donor's name being
  corrected is a rename, and a rename NEVER touches the ledger.
- **The donation form posts a plain `donorId`, not a bound `SstsDonor`.** The
  form must not be able to smuggle in a whole donor object, and
  `DonationService.record` re-resolves the id through `DonorService.require` so
  the reference can never dangle. Editing cannot RE-ASSIGN the donor: moving a
  gift between donors silently rewrites whose history it belongs to, so that is
  delete-and-re-record, not an edit.
- **The donor carousel used to be HARDCODED TWICE in index.html** -- three
  static server-rendered cards AND a separate JS block rotating nine hardcoded
  donor objects with dead `example.com` links. Both are gone.
  `static/js/donor-carousel.js` now does the rotation, fed by the server data in
  `window.__SSTS_DONORS` (emitted via Thymeleaf JS inlining, which escapes for
  the script context). The panel keeps a fixed THREE-card window so the narrow
  `.donor-cards` column beside the hero slider does not grow once per donor --
  `PageController.DONOR_WINDOW` cuts the server-rendered fallback, and the JS
  holds exactly that many `<article class="donor-card">` nodes. Only the first
  three are in the markup, so the section still shows real content with JS
  disabled. Rules the script follows, do not "simplify" them away:
  - It REBUILDS each card with `createElement` + `textContent`, never innerHTML.
    The old code did `card.querySelector('.donor-photo').src = ...`, which
    THROWS for a donor with no photo, and `textContent` keeps a donor name
    containing markup from executing.
  - A donor with no logo gets the `.donor-photo--fallback` initials tile, so a
    rotated card is indistinguishable from a server-rendered one and the card
    keeps its 132px photo block.
  - Autoplay PAUSES on hover and on focus-in. Rotating under the pointer makes
    the "Visit website" link unclickable.
  - `prefers-reduced-motion` disables autoplay; the arrows still work.
  - It publishes `window.__syncDonorTimer`, which the hero slider calls so the
    two 8s timers restart together. Deleting either side silently desyncs them.
  - The arrows render only when `donors.size() > donorWindowSize`, so three or
    fewer donors never show a pointless control.
  - Everything is inside try/catch: it is a public page, and the three
    server-rendered cards are already in the DOM if the script cannot start.
- **The donor seed uses `WHERE NOT EXISTS (lower(name))`, deliberately NOT the
  DELETE-by-`seed_key` pattern the gallery and announcement seeds use.** Those
  seed rows are event records the school replaces wholesale; a donor row is
  curated state an admin edits, and `is_public` is a consent decision. A
  DELETE+INSERT re-seed would silently reset it and RE-PUBLISH a donor the
  school had deliberately unlisted. Re-running the donor seed preserves admin
  edits, including unlisting someone. Verified: 3 consecutive runs leave 7
  donors, and un-ticking `is_public` survives a re-run.
- **No donations are seeded.** Fabricating dollar figures for a real school
  would put invented money on the public site. The ledger starts empty.
- **`website_url` is https-only, and the CHECK is the real guard** (the public
  page renders it into an anchor, so a permissive scheme would be stored XSS).
  It is refused by `chk_ssts_donors_website`, which is stricter than it looks:
  the path segment is a `(...)*` group, so `https://example.com` is accepted but
  a query string is not, and `//evil.com` is refused -- it has no scheme at all
  and would sail past a naive "starts with http" test.
  `DonorPathConstraintTest` transcribes both donor CHECKs in Java and pins the
  accept/reject cases. It is a transcription of the Postgres patterns, NOT a
  database test, and it was cross-checked against a real Postgres run: all 11
  cases agree. A CHECK is invisible to the rest of the suite, which mocks every
  repository.
- **`DonorPhotoStorageService` is a thin adapter over
  `GalleryImageStorageService`, not a second S3 client.** All bucket mechanics
  live in the gallery service; the donor service only decides naming. A donor's
  logo is a FIXED `logo.<ext>` in `donors/<slug>-<id>/`, so re-uploading
  overwrites rather than accumulating `1.png`, `2.png` the way a gallery event
  does. `folderOfUrl` returning null is what stops a delete from touching
  objects this service does not own -- that is how a committed
  `/images/...` path is left alone.
- **The donor card height is FIXED at 252px, and that number is budgeted.**
  Without it the panel is content-sized, so cards differ in height depending on
  how the donor's name and tagline wrap and on whether it has a website link --
  and rotating between differently sized cards resizes the whole `.donor-cards`
  column beside the hero slider on every 8s tick. The 252px is the worst case:
  20px padding + 134px photo + 8px gap + 20px name (1 line) + 4px grid gap +
  37px tagline (2 lines) + 4px grid gap + 21px link + 2px card borders = 250,
  plus 2px slack.
  - **The clamps are asymmetric and that is deliberate: `.donor-name` is
    `-webkit-line-clamp: 1`, `.donor-biz` is `-webkit-line-clamp: 2`.** Budget
    against what the real data needs, not against the theoretical worst case.
    Of the nine seeded donors, eight taglines fit one line but ONE -- "Human
    resources consulting services", 35 characters -- needs two. So the name gets
    one line and the tagline gets two. Clamping the tagline to one line to
    "save" 19px would ellipsis a real sponsor's text; budgeting a two-line NAME
    as well reserves 20px that no current donor uses and pushes 3 x 288 to 962px,
    which is taller than the hero slider it sits beside -- see below.
  - **`.donor-card .donor-photo` is `flex: 0 0 132px`** (not 150px), so the photo
    block is never compressed AND the card still fits a two-line tagline. It is
    what pays for the second tagline line. The `.donor-photo--fallback`
    initials tile inherits this, so a photo-less donor keeps an identical card.
  - **`.donor-info` is `flex: 1 1 auto; align-content: center`, NOT
    `align-content: start`.** The eight one-line donors leave ~20px unused; the
    previous `start` dumped all of it at the bottom as a visible hole below the
    link. Centring splits it ~10px above the name and ~10px below the link, so
    a short card reads as padded rather than as one with a gap in it.
  - **BOTH `.hero-slider` and `.donor-cards` are `align-self: center`, and
    neither may carry a `margin-top`.** This is what makes the two panels'
    vertical CENTRES coincide. Two traps make it easy to get wrong:
    - `hero-full.css` gives `.hero-slider` a **definite** height
      (`calc()` off the viewport), and a definite height makes
      `align-self: stretch` a **no-op** -- so a stretched slider silently sits at
      the TOP of the flex line. Meanwhile `.donor-cards` is the tallest item and
      therefore defines the line height and trivially fills it. The two centres
      then differ by half the height difference: measured **+142.7px** at
      1440x900 and +192.2px at 1366x768. Centring BOTH panels fixes it to
      exactly 0 at 1280/1366/1440/1920 wide.
    - An earlier revision had `margin-top: -40px` on `.donor-cards`, which pushed
      the panel up by hand to fake the alignment. It is gone; do not reintroduce
      it, because it silently re-breaks the moment the card height changes.
    The parent `.hero-visual` is `display: flex` (`hero-full.css` sets
    `display: grid` but `donors.css` is linked last and wins).
  - **Below 900px the panels STACK, so centre alignment does not apply.**
    `@media (max-width: 900px)` sets `.hero-visual { flex-wrap: wrap }`, putting
    the donor column on its own row beneath the slider. A non-zero centre delta
    there is correct, not a regression.
  If you change a font-size, recompute the height. `donors.css` is the only
  stylesheet that touches `.donor-card`, and it is linked last, so nothing
  overrides the height on cascade.
- **Every donor card carries a "Visit website" link.** Losing them was a real
  regression in the public display, not a cosmetic detail. All nine seeded
  `website_url` values are the **example.com placeholders the page already
  shipped with**, not verified real sites -- replace them with the sponsors'
  real addresses via `/superadmin/donors`. `chk_ssts_donors_website` still
  applies (https-only; a query string or scheme-relative `//evil.com` is
  refused), so an admin cannot save an unsafe link either.
- **Donors ARE cached (`DONORS`, 24h); donations are NOT.** A donor list changes
  rarely and is on the public home page. The ledger is money edited one row at a
  time, so a stale total is a correctness problem, not a slow page.

### Aggregation
- Done in Java from simple `countBy...` methods. There is no GROUP BY and no
  `Object[]` JPQL projection anywhere. `ReportsService`'s only `@Query` is a
  `SELECT DISTINCT e.academicYear` scalar. It renders ~30 counts per request;
  a hand-written GROUP BY would be a little faster, much less readable, and the
  first projection query in the project. Don't add one without a measured
  reason.
- `ReportsView` is the one real view DTO, because the reports page has no
  entity to bind. `#temporals` has no `today()`, so the controller puts `today`
  and the fixed `userTypes` list in the model.

### Seeds
- **Seed cleanup must never key on `title`.** Titles are user-supplied, so
  `DELETE ... WHERE title IN (...)` destroys admin-created content on re-run.
  `ssts_gallery_events.seed_key` and `ssts_announcements.seed_key` are nullable
  markers set only by `sql/data.sql`, and both seed blocks delete on those. The
  one title-keyed statement in schema.sql is a one-time backfill marking
  pre-existing seed rows -- don't copy that pattern.
- The calendar seed in `data.sql` covers `academic_year 2026-2027` (~40 INSERTs,
  wrapped in a DELETE for that year so it's idempotent). `/calendar` shows only
  the current AcademicYear, so a new year needs a new block or the page renders
  its "being finalized" empty state.

### Images
- **Committed images are files in `static/images/`, but GALLERY UPLOADS are
  objects in a bucket. There are still no blobs (no bytea, no image bytes in
  the DB).** Two different storage models now coexist, deliberately:
  - **Committed** photos/logos live in `src/main/resources/static/images/` and
    are served at `/images/...`; the DB stores the relative path. Four
    places: the school logo (hardcoded `/images/ssts-logo.png` in templates),
    `ssts_users.avatar_url` (`/images/teachers/teacher-01.jpg`), the
    7 seeded `ssts_gallery_events` rows (`/images/gallery/<folder>/<n>.jpg`),
    and the 7 seeded `ssts_donors` rows (`/images/<CamelCaseName>.jpg`).
    To change one of these, commit the file.
  - **Uploaded** gallery photos go to the S3-compatible bucket configured under
    `app.storage.*`; the DB stores the public `https://` URL.
- **Why uploads cannot use the local filesystem.** The app is containerized and
  a container's disk is discarded on every deploy. The original
  `GalleryImageStorageService` wrote to `src/main/resources/static/images/gallery`
  in dev and fell back to a sibling `./static` folder next to the jar in Docker
  -- which could not work: that directory is not on the classpath so nothing
  served it (every uploaded photo 404'd), and it was wiped on the next release
  while the `ssts_gallery_events` row survived pointing at a deleted file. In
  dev the primary path masked both problems because that directory *is* the
  classpath and *is* served. Do not reintroduce a filesystem fallback.
- **`app.storage.*` defaults to DISABLED (`enabled: false`)** and the service
  builds no S3 client in that state, so a missing bucket can never fail app
  startup -- the public gallery and every other page keep working. With it off,
  an upload throws `StorageDisabledException` and the admin gets a flash
  explaining the bucket is not configured, rather than an opaque failure. Set
  on Render: `APP_STORAGE_ENABLED`, `APP_STORAGE_BUCKET`,
  `APP_STORAGE_PUBLIC_URL`, plus `APP_STORAGE_ENDPOINT_URL` (R2 account
  endpoint) / `APP_STORAGE_ACCESS_KEY` / `APP_STORAGE_SECRET_KEY`. Leave
  access/secret blank to use the default provider chain (env, task role).
- **The object KEY and the public URL are separate concerns.** The service
  stores `gallery/<folder-slug>-<id>/<n>.<ext>` and builds the URL as
  `public-url + "/" + key`. It never infers a URL from the SDK, so swapping a
  bucket for a custom domain is config-only. `folderOf()` is used by the delete
  path and returns null for any URL that is not ours, which is what stops
  delete from touching objects it does not own.
- **Re-running `data.sql` RENUMBERS `ssts_gallery_events` ids.** The seed
  block is `DELETE FROM ssts_gallery_events WHERE seed_key = ...` followed by a
  fresh multi-row INSERT, which is what makes it idempotent and safe to re-run
  -- but Postgres assigns NEW ids, so the seven entries went 1-7 -> 21-27 on
  2026-09-28. Two consequences:
  - Any URL or bookmark carrying an id is stale after a re-seed, and an
    `/superadmin/gallery/{id}/edit` for an old id silently 302s to the list
    (the controller treats a missing row as "not found", not a 404).
  - `GalleryImageStorageService.folderFor` names a NEW upload's key prefix
    `<title-slug>-<id>`, so uploading to "Pongal Celebration" (id 21) creates
    `gallery/pongal-celebration-21/`, a DIFFERENT prefix from the committed
    `static/images/gallery/pongal-celebration-1/`. Both render fine -- the
    stored value is a full path, not a lookup -- but one event then has two
    prefixes. Do not treat the committed folder name as authoritative for an
    event's id; read the id from `ssts_gallery_events`.
- Gallery ordering: public feed is
  `findByActiveTrueOrderByDisplayOrderAscEventDateDesc()` (curated
  displayOrder first, then newest eventDate). The admin list uses `findAll(Sort)`
  and deliberately ignores `isActive` so a deactivated entry stays editable.
  When `imageUrl` is blank, the card shows the title's first letter on a
  gradient tile, mirroring team.html's `.team-photo-fallback`.
- **`chk_ssts_gallery_events_image_urls`'s `$` must stay OUTSIDE the repeating
  group.** The pattern is
  `^(\s*(/images/[A-Za-z0-9._/-]+|https://[A-Za-z0-9.-]+/[A-Za-z0-9._/-]+)\s*)+$`.
  Written the obvious way -- `(\s*...\s*$)+` -- it can only ever match a SINGLE
  trailing line: the inner `\s*$` must match at end-of-string, and Postgres `$`
  does not match at an interior newline, so the repetition could never advance
  past line 1. Every entry with 2+ photos was therefore rejected by the
  constraint and surfaced to the admin as a bare HTTP 500, on 2026-09-28. That
  is not an edge case: `SstsGalleryEvent.setSlides` joins with `\n`, so a
  multi-file upload ALWAYS produces a multi-line value. The single-photo path
  worked, which is exactly why this survived so long. Fixed in `schema.sql` AND
  migrated on the live table (the `CREATE TABLE IF NOT EXISTS` in schema.sql is
  a no-op there, so a schema fix needs its own `ALTER TABLE`).
  Both CHECKs now accept EITHER a relative `/images/...` path OR an `https://`
  bucket URL, since uploads are objects; `http:`, `data:`, `javascript:`,
  query strings and bare filesystem paths are still rejected.
  `GalleryImagePathConstraintTest` transcribes both regexes in Java and pins
  the accept and reject cases -- Java and Postgres agree on this semantics, so
  the transcription is faithful. It is the only guard: a CHECK constraint is
  invisible to the unit tests, which mock every repository.
- **An uncaught `DataIntegrityViolationException` becomes a raw 500.** The
  image-path CHECKs reject absolute URLs and paths with spaces, and the
  cover-path field is free text, so an admin pasting an image URL got an
  opaque error. `GalleryAdminController` now catches it on create AND update
  and redirects with a flash explaining the `/images/...` rule, matching what
  `UserAdminController` does for its unique-constraint race. Generalize this
  pattern to any controller that writes a value a DB constraint can reject.
- `templates/index.html` renders the announcement list TWICE inside
  `.marquee-track` (second copy `aria-hidden`) because the CSS animation
  (`donor-scroll` in theme.css) translates the track 100% -> -100%, so a single
  copy visibly jumps at the wrap point. The `<section>` is `th:if`'d off when
  there are no announcements. The marquee is home-page only by design; other
  templates keep it hardcoded/empty.

### Frontend
- **The nav has exactly one source: `templates/fragments/nav.html`.** Every
  template includes it with
  `<header th:replace="~{fragments/nav :: nav}"></header>`. Never hand-write a
  `<header class="template-header">` and never add a link in one template only.
  It needs `currentPath`, `isAuthenticated`, `isSuperAdmin` -- all supplied to
  every view by `controller/CommonModelAdvice` (@ModelAttribute methods). If
  you add a controller you do NOT need to add anything, but you must not expect
  `isSuperAdmin` to be set locally. `isAuthenticated` is deliberately broader
  than `currentUser != null`: a non-SstsUser principal is still logged in, just
  not role-checkable. Two traps: the class names (`.template-header`,
  `.header-logo`, `nav a.active`, `.logout-form`, `.logout-button`) are
  load-bearing for several themes, and Thymeleaf COPIES HTML comments into the
  output -- so a note inside the `th:fragment` element ships on every page. Keep
  commentary outside it.
- **The nav has two dropdowns, `div.nav-about` (public, rendered for every
  visitor including logged-out) and `div.nav-superadmin`.** Both parent anchors
  are click-toggles that call `preventDefault()` and do NOT navigate; the `href`
  is retained for accessibility and for server-rendered test assertions. Because
  of that, `/superadmin` needs the explicit "Overview" submenu item to stay
  reachable, and `/about` + `/team` are submenu items rather than flat links.
  That looks redundant -- it isn't; don't remove them.
  - Parent anchor text must be EXACTLY "Super Admin" / "About" with **no child
    elements**, because `NavFragmentTest.anchorIsActive` regexes
    `<a[^>]*>\s*Super Admin\s*</a>`. The caret is a `::after`.
  - The `--current` markers go on the WRAPPER, not the parent anchor, so the
    submenu can be force-opened and the active module link stays visible.
    `/about` and `/team` match exactly; the super-admin modules use `startsWith`.
  - A super-admin does **not** get the "Dashboard" link
    (`th:if="${isAuthenticated and not isSuperAdmin}"`) because they land on
    /superadmin and it would only bounce. Plain admins, staff and parents do.
  - **Adding a module means editing TWO places:** the nav submenu in
    `nav.html` AND the cards on `templates/superadmin/dashboard.html` (the
    /superadmin landing page, nine `.cards > article` cards). Also add it to
    SecurityConfig's URL rule and the @PreAuthorize set.
  - `templates/admin/dashboard.html` contains **no** `isSuperAdmin` conditional
    and its module paragraph deliberately contains **no** `href="/superadmin"`
    -- `DashboardControllerTest.plainAdminDoesNotSeeSuperAdminLinks` and
    `staffRoutesToAdminDashboardWithoutSuperAdminLinks` assert a plain admin and
    a staff member never see a /superadmin link on that page. Name the modules
    in text; do not link them.
- **`static/js/nav-dropdown.js`** drives both dropdowns. State is expressed
  only as wrapper classes (`nav-about--open`/`--closed`,
  `nav-superadmin--open`/`--closed`, mutually exclusive), with the suffix
  derived from the wrapper's own base class so one code path serves both. It
  closes on Escape (returning focus to the parent anchor), outside click, and
  `focusin` outside the wrapper; opening moves focus to the first submenu link.
  Key rules:
  - **Absence of `--open` does not mean closed** -- the stylesheet can open a
    menu on its own via `--current`. `isMenuOpen()` mirrors the stylesheet:
    `--closed` wins outright, otherwise `--open` OR `--current` means open.
  - A dismissal that must survive a *navigation* has to be persisted, because
    the click and the new document are different pages. A submenu click writes a
    timestamp to `sessionStorage` (`DISMISSAL_KEY = 'sstsNavSubmenuDismissed'`)
    before closing; the next load removes it **unconditionally** and honours it
    only if fresher than `DISMISS_WINDOW_MS = 3000`. The window is not optional:
    when a click does *not* navigate the flag is still stored and would
    otherwise wrongly suppress `--current` on a page reached minutes later. All
    storage access is wrapped in try/catch -- an uncaught throw would abort the
    IIFE and kill the parent-anchor toggle; degradation is silent (same-page
    close still works).
  - The submenu click handler does NOT call `preventDefault()` -- the link must
    navigate.
  - **There is no browser and no JS test runner**, so verify by hand-tracing
    state plus `node --check` and the Java suite (NavFragmentTest covers the
    markup). Before trusting the menu, click through: open/close the parent on a
    page with no `--current` (e.g. `/gallery`); close on `/about` or
    `/superadmin/team` (a `--current` page); close after clicking "Overview"
    from a module page; close after "About Us"/"Our Team" from another page;
    dismiss on outside click.
- **The delete-confirm prompt is shared**: `static/js/inline-delete.js` reads
  `data-confirm` off any `form.inline-delete`; all six super-admin list
  templates pass a per-row `th:data-confirm` and
  `<script th:src="@{/js/inline-delete.js}">`. A further module must use it,
  not another inline copy. The reports page has no delete and uses neither.
- `template-hero` exists ONLY in `index.html`. The dashboards and every
  `superadmin/` page put their heading in a `div.page-heading` as the first
  child of `.intro-section`. Do not reintroduce full-viewport heroes:
  `hero-full.css` sets `min-height: calc(100vh - 140px)`, pushing forms and
  tables below the fold.
- **Super-admin table widths:** the six list tables and the reports tables sit
  inside `.intro-section`, a TWO-COLUMN CSS GRID (`0.9fr 1.1fr`), so
  `.admin-toolbar` and `.admin-table` are given `grid-column: 1 / -1` to get
  full width. `.admin-table` uses `table-layout: fixed`; per-module widths come
  from `.admin-table--calendar` / `--team` / `--gallery` / `--announcements` /
  `--users` (7 cols) / `--roles` (5 cols) / `--reports`. **The column ORDER in
  each template is a contract with those `:nth-child` rules** -- reordering a
  `<th>` silently hands the wrong width to the wrong data.
  `.admin-table--reports` deliberately sums to 80% on its 2-column tables; under
  `fixed` layout the leftover spreads across both columns to give the ~70/30
  split. That is intended -- don't "fix" the 80%.

## Gotchas

- **`./gradlew bootRun` overrides your env vars and they point at PRODUCTION.**
  `build.gradle` gives `bootRun` a `doFirst` that reads `.vscode/launch.json`
  and calls `environment k, v` for each entry, overwriting the inherited
  environment. Those entries are the Render host, `DB_NAME=ssts_pr`, and
  **`DB_DDL_AUTO=update`**. So `DB_DDL_AUTO=validate DB_HOST=127.0.0.1 ./gradlew
  bootRun` does neither. **Never verify a schema with `bootRun`.** Use the jar:
  ```bash
  ./gradlew bootJar
  DB_HOST=127.0.0.1 DB_PORT=55432 DB_NAME=ssts_verify DB_USERNAME=verify \
    DB_PASSWORD= DB_DDL_AUTO=validate DB_SHOW_SQL=true \
    java -jar build/libs/ssts-integrated-webapp-0.0.1.jar --server.port=8099
  ```
  A correct `validate` run emits **zero** `create table` / `alter table` /
  `drop table` lines. Converse trap: no DDL proves nothing on its own, because
  `update` is also a silent no-op when the schema already matches.
- **`NULLS LAST` cannot be spelled in a derived query name.**
  `findBy...OrderByJoiningDateAscNullsLastFullNameAsc(...)` fails at STARTUP
  with "No property 'nullsLastFullName' found" -- Spring Data parses
  `NullsLast` as a property path. `findPublicTeamMembers()` is therefore a
  `@Query` JPQL method even though derived is the house style. **This class of
  bug is invisible to the test suite**, because every repository in the
  service/controller tests is a Mockito mock and no real query is ever built.
  Only the JAR boot against a real database catches it.
- **JPA derived queries on booleans:** `SstsCalendarEvent`'s field is `active`
  (getter `isActive()`), so the derived method is `...AndActiveTrue...`, NOT
  `...AndIsActiveTrue...` (the latter throws "No property 'isActive' found" and
  fails the whole context).
- **JPA's implicit naming strategy mangles a digit before the camel-case
  boundary.** `parent1Email` maps to `parent1email` (no underscore) while
  `parent1FullName` maps to `parent1full_name` -- same field-name shape,
  different result. Under `ddl-auto=update` this is invisible (Hibernate just
  creates it that way); under `validate` it's a hard startup failure
  (`missing column [parent1email] in table [ssts_families]`). The four
  `SstsFamily` parent fields carry explicit `@Column(name = ...)` -- **do not
  delete them**, and pin names explicitly for any new field with a digit in it.
- **Never key a test stub on a hardcoded date** -- use `SchoolTime.today()`.
  A stubbed `countByExpiresOnBefore(LocalDate.of(2026, 9, 26))` against a
  service that calls `SchoolTime.today()` went green on the 26th and red on the
  27th with no code change, because the machine's timezone rolls New York over
  mid-morning. An unstubbed Mockito call returns 0, so the symptom is a bare
  `expected: 2L but was: 0L` with no hint the clock moved. If a suite fails on a
  count, check for a hardcoded date before assuming a code regression.
- **Nav CSS cascade** (easy to get wrong):
  - `school-template.css` is stylesheet link 4, but `welcoming-template.css`
    (6) and `fullscreen-dark.css` (7) also set `.template-header nav a`. An
    EQUAL-specificity rule added to school-template.css LOSES on source order,
    and media queries add no specificity. The submenu rules therefore use higher
    specificity (`.template-header nav ul.nav-submenu a` (0,2,3),
    `... a:first-child` (0,3,3)) to beat `welcoming-template.css`'s
    `.template-header nav a:first-child` (0,2,2), which otherwise re-targets
    onto the nested submenu anchors.
  - Per-state specificity: hidden base `ul.nav-submenu` (0,2,2);
    `--current` (0,3,2); `:hover`, `:focus-within`, `--open`, `--closed` all
    (0,4,2). So `--closed` beats `--current` outright but only TIES
    `--open`/`:hover`/`:focus-within` and wins on source order.
  - **The `--closed` override MUST be the LAST block in
    `school-template.css`, AFTER the `@media (max-width: 760px)` rule.** The
    mobile block sets `opacity: 1; pointer-events: auto` at only (0,2,2);
    placed earlier, the override would lose on order at that breakpoint. Moving
    that block up reintroduces a stuck-open menu that a "tidier" will believe
    specificity protects them from.
  - The submenu's hidden state must stay `opacity` + `pointer-events: none` and
    must NEVER become `display: none` / `visibility: hidden` -- those drop the
    links from the accessibility tree and break the keyboard reveal.
  - At `max-width: 760px` both submenus are `position: static` and inline, NOT
    absolute: the nav has `overflow-x: auto; overflow-y: hidden` there and
    would clip an absolutely-positioned descendant. All links visible on mobile
    is the intended trade-off.
  - Other facts worth not rediscovering: there is no `<ul>` reset and no
    `:focus-within` outside the submenu block; the effective nav `gap` is 0, not
    the 28px in school-template.css; the effective hover/active colour is the
    teal `#183c4a`/`#70d6c7` from fullscreen-dark.css (no gold gradient in the
    nav's cascade -- that impression comes from logo-palette.css, which
    fullscreen-dark overrides); `centered-logo.css` and `royal-indigo.css` are
    linked by no template, so editing them changes nothing.
- **CSS logo sizing is a chain; last rule wins (equal specificity).** The header
  logo ends up `min(490px, 44vw)` from `logo-size-large.css`. A per-page inline
  `<style>` after the stylesheet links overrides it.
- **Hibernate-created tables lack schema.sql's column defaults.** Under
  `ddl-auto=update`, a table first created from an entity gets `is_active`/
  `created_at`/`updated_at` as NOT NULL with NO default, so a hand-written
  INSERT omitting them fails even though schema.sql shows `DEFAULT`. schema.sql
  carries idempotent `ALTER TABLE ... SET DEFAULT` for ssts_calendar_events to
  close this -- keep them.
- **Port 8081 / `webServerStartStop`:** "Failed to start bean
  'webServerStartStop'" almost always means the port is taken, not a code bug.
  `bootRun` forks a JVM that can survive stopping the Gradle wrapper. Clear with
  `lsof -ti tcp:8081 | xargs kill -9`. Run the app from ONE place.
- **The login page renders TWO `_csrf` inputs.** Scraping with a naive grep
  captures both and a scripted `POST /login` fails CSRF with a bare 403. Take
  only the first match.
- **`com.zaxxer.hikari` and `org.hibernate` are pinned to WARN** in
  application.yml (all profiles). DEBUG on Hikari dumps pool config including
  the JDBC URL and credentials. Don't downgrade, and don't add SQL param logging
  elsewhere.
- **Thymeleaf's `#temporals` has format()/formatISO() but NO `today()`.** Put the
  date in the model from the controller. Also never nest `${}` inside a
  `th:text` expression -- write `${obj.method(today)}`, not
  `${obj.method(${today})}` (EL1043E).

### Testing
- **@WebMvcTest slices need a @MockitoBean for EVERY service the controller
  injects.** `PageController` injects EmailService + TeamService + CalendarService
  + GalleryService + AnnouncementService. This also bites slices that load
  PageController as a side effect (`CacheAdminControllerTest`).
- **@WebMvcTest DOES render the template, not just resolve the view name.** So
  `status().isOk()` + `view().name(...)` will NOT catch a broken template
  expression -- assert on `content().string(containsString(...))`.
- **Prefer @SpringJUnitConfig / @WebMvcTest over @SpringBootTest.** The latter
  boots JPA and needs live DB credentials (fails with "password authentication
  failed for user ${DB_USERNAME}"). @SpringJUnitConfig wiring just CacheConfig +
  the service with a mock repository is fast and DB-free.
- **Mocked-bean tests share state across methods** (singleton context): reset
  invocation counts with `clearInvocations(...)` in @BeforeEach or `verify()`
  counts leak between tests. Pass ONE mock per call -- several JpaRepository
  subtypes in one varargs call trip an unchecked generic array warning.

## Database
- **`sql/schema.sql` is DECLARATIVE, not incremental, and that is the single
  most important thing to know about it.** Every table is declared once in its
  FINAL shape: no `ADD COLUMN`, no `DROP COLUMN`, no backfill `UPDATE`s, no
  `DO $$` blocks. It is entirely `CREATE`.
  - **Dev workflow: drop the database, run schema.sql, run the seeds.**
    ```bash
    dropdb ssts_pr && createdb ssts_pr
    psql -v ON_ERROR_STOP=1 -f sql/schema.sql
    psql -v ON_ERROR_STOP=1 -f sql/data.sql
    ```
    **BOTH files are idempotent** -- data.sql guards every role and user
    INSERT with `WHERE NOT EXISTS (username)` and keys its DELETEs on
    `seed_key`, so re-running it is a no-op instead of a unique-constraint
    error. Verified: three consecutive runs leave every table count unchanged.
    `ON_ERROR_STOP=1` is mandatory; without it psql reports the failure and
    still exits 0, so a half-applied seed looks clean.
  - **`CREATE TABLE IF NOT EXISTS` is a NO-OP, not a convergence.** Applying it
    to a database whose table already has a different shape changes NOTHING for
    that table -- no error, no repair. Nothing in the codebase converges a
    drifted database; the only fix is to drop and recreate.
  - **ALWAYS prove a schema.sql change on a throwaway database before trusting
    it.** A pure `dropdb && createdb && psql -f schema.sql` run must produce
    exactly 18 tables, 49 named CHECKs, 71 indexes and 14 FKs. On 2026-09-27 the
    `CREATE TABLE ... ssts_families` statement had been given the name
    `ssts_users` by mistake, so a pure build produced TWO tables and then died
    with `relation "ssts_families" does not exist` -- and because
    `CREATE TABLE IF NOT EXISTS` makes the duplicate a silent no-op, nothing
    errored until the first families index 40 lines later. The live database
    looked perfect throughout, because it had been built from an earlier good
    copy of the file. `CREATE TABLE IF NOT EXISTS` hides a wrong table NAME far
    more easily than a wrong column, and only a from-scratch build catches it.
  - `chk_ssts_roles_name_format` is an inline table-level CHECK, created WITH the
    table. This matters: `validate()` does not check CHECK constraints, so a
    missing one lets the app start fine and a malformed role name is only
    rejected at INSERT/UPDATE time.
  - **ALWAYS prove a schema.sql change on a throwaway database before trusting
    it.** A pure `dropdb && createdb && psql -f schema.sql` run must produce
    exactly 18 tables, 49 named CHECKs, 71 indexes and 14 FKs. On 2026-09-27 the
    `CREATE TABLE ... ssts_families` statement had been given the name
    `ssts_users` by mistake, so a pure build produced TWO tables and then died
    with `relation "ssts_families" does not exist` -- and because
    `CREATE TABLE IF NOT EXISTS` makes the duplicate a silent no-op, nothing
    errored until the first families index 40 lines later. The live database
    looked perfect throughout, because it had been built from an earlier good
    copy of the file. `CREATE TABLE IF NOT EXISTS` hides a wrong table NAME far
    more easily than a wrong column, and only a from-scratch build catches it.
  - **Entity-backed tables mirror what Hibernate generates**, because that is
    what `validate()` compares against: `bigint GENERATED BY DEFAULT AS IDENTITY`
    keys, `timestamp(6) without time zone` for `LocalDateTime` (NOT
    `timestamptz`), `varchar` for `String`, snake_case names.
    `ssts_password_reset_tokens` is the one exception -- its entity uses
    `OffsetDateTime`, so it genuinely is `timestamp(6) with time zone`. The
    school-system tables have no entities but follow the same conventions.
- **The dev database (Render `ssts_pr`) was dropped and recreated from
  `schema.sql` on 2026-09-27, then AGAIN on 2026-09-28** when the three seed
  scripts were merged into `data.sql`. It now carries the final normalized shape
  directly (18 tables after the donor module landed, all FKs named `fk_*`), and
  `ssts_user_profiles` / `ssts_parent_details` / `ssts_staff_details` /
  `ssts_teachers` no longer exist. There is no `profile_id` column on
  `ssts_users` either.
  - **Do not trust a CHECK count you did not measure.** Different psql/pg
    versions report different totals for `pg_constraint` (`contype='c'` counts
    NOT-NULL constraints too, so 42/43/44 all appeared for the same file). The
    only trustworthy check is a per-constraint NAME diff between the local
    schema.sql build and the live database -- that came back empty, which is
    what actually proves the two shapes agree.
- **Restoring users across the recreate: map by role NAME, never by
  `role_id`.** Reseeding renumbers roles (`super_admin` 3 -> 1 and down), so
  copying `role_id` would silently give every user the wrong role while still
  looking successful. Two further traps, both hit during this migration: the 5
  seeded accounts `editor_user`/`teacher_user`/`parent2`/`volunteer_lead`/
  `school_admin` had been hand-overwritten with the PLAINTEXT string `test123`
  in the live DB (`BCryptPasswordEncoder.matches()` can never accept it), so
  they are deliberately NOT restored -- `data.sql` recreates them with valid
  bcrypt hashes; and 7 of the 11 real accounts collide on `email` with the
  staff seed block, which already holds the same people's data under better
  usernames (`art_teacher` -> `munmazhalai_teacher`), so only 4 were actually
  restored. 18 users remain, all with a role.
  - **The restore must be an UPSERT (`INSERT ... ON CONFLICT (username) DO
    UPDATE`), not an `UPDATE ... FROM`.** The e2e accounts
    (`e2ereg1790219578`, `e2o1790219648`, `flowtest1790221519`,
    `full1790219816`) are NOT in the seed, so they do not exist in a freshly
    built database and a plain `UPDATE` matches **0 rows while still exiting
    0**. A silent zero-row restore looks exactly like a successful one. Always
    check the affected-row count, and verify by logging in afterwards.
  - **Never guess a password hash.** `ssts_admin`'s real hash is NOT in the repo
    and NOT in the seed. Reading a hash out of context and writing it over the
    live account silently breaks super-admin login, and `data.sql`'s placeholder
    cannot be used to recover it. Take it from the pre-migration
    `pg_dump` in `/tmp/keep/`, or ask the user to set a new one with the
    `htpasswd` recipe. Verify the login round-trips before declaring success.
- **Full backups of the live database live in `/tmp/keep/`, not in git** --
  `ssts_pr_PRE_MERGE_*.sql` (2026-09-28) and `ssts_pr_PRE_RECREATE_*.sql`
  (2026-09-27), plus `real_users_snapshot.psv` (the 4 real accounts, `~`-delimited
  so passwords containing `,` cannot corrupt it). These contain every password
  hash, are `chmod 600`, and are **sensitive**. `/tmp` is wiped on reboot, so
  copy them somewhere durable before relying on them.
- **There is no separate "normalize the school system" migration, and there
  does not need to be one.** A one-time script used to exist for this and was
  deleted on 2026-09-27: it was a verified PURE NO-OP against a
  `schema.sql`-built database (42 named CHECKs / 64 indexes before and after), and
  it could not run at all against a database at the export shape, because it
  referenced `ssts_families` -- a table that does not exist there -- so it
  died with `relation "ssts_families" does not exist`, psql exit 3, and its
  single BEGIN/COMMIT rolled the whole thing back. `schema.sql` now carries the
  whole final shape declaratively, and its own header comments record WHY each
  constraint exists (no stored aggregates, the `YYYY-YYYY` year format, `fk_*`
  naming, indexed FK columns). Read the shape and the reasoning from there.
- **Open data debt: the 6 existing `user_type = 'parent'` accounts have no
  `ssts_families` row.** Registration only started creating one when `ParentService`
  landed on 2026-09-27, and nothing backfills the accounts that predate it, so
  every parent's contact details are still sitting in `ssts_users.phone` /
  `address_line1` where nothing reads them. Two of them
  (`e2ereg1790219578`, `e2o1790219648`) carry a real phone and address, the rest
  are empty. The backfill is a one-off INSERT ... SELECT from `ssts_users` to
  `ssts_families` for unlinked parents; it has NOT been run. Separately,
  `ssts_staff_details` held `salary`,
  `qualification`, `teaching_certification`, `employment_type` and
  `employee_id`, which exist in no other table; that table was dropped, so
  those 10 rows' unique columns are gone (recoverable from the dump below).
- **Pre-recreate dump:** `/tmp/keep/ssts_pr_PRE_RECREATE_*.sql` holds the
  complete original database (schema + all rows) if anything above needs
  re-deriving. It is a local file, not in git, and should be treated as
  sensitive -- it contains every password hash.
- **PRE-DEPLOY, do not skip: apply the schema BEFORE deploying a new build.**
  There is no Flyway/Liquibase and the Dockerfile has no schema step, so nothing
  creates tables automatically. Prod runs `ddl-auto: validate`, so deploying a
  jar that references a new table/column before it exists means the context
  fails to start and the app is down -- Hibernate will not create it. Apply
  `sql/schema.sql` with production credentials first, then deploy.
  **But applying it changes nothing for tables that already exist** (see the
  no-op note above), so a schema change to an existing table needs its own
  migration.
- **The old `sql/merge_user_profiles_into_users.sql` was DELETED on 2026-09-28.**
  Do not resurrect it. The recreate from `schema.sql` already performed the
  merge, so its first step (add the 21 per-person columns) would have been a
  no-op and its `DROP TABLE IF EXISTS ssts_user_profiles` had nothing to drop.
  For any environment still at the old shape, `schema.sql` + `data.sql`
  reproduces the finished state directly, which is the cheaper path.
  `ON_ERROR_STOP=1` matters whenever a psql script is run at all: without it
  psql reports a failing statement and still exits 0.
- **`SstsFamily` is the only school-system table with an entity**, and it is the
  only one the app touches: `SstsFamilyRepository` + `ParentService` back the
  parent module. Because it is an entity, prod's `validate()` fails with
  `missing table [ssts_families]` if the table is absent -- and because
  `schema.sql` is what creates it, a `schema.sql` file that has lost or
  misnamed that one CREATE TABLE takes the whole app down at startup. There is
  still no `SstsStudent` entity, so `ddl-auto=update` never creates or validates
  `ssts_students` either -- schema.sql is the only thing maintaining it. If an
  entity is ever added for a school-system table, its columns must match
  schema.sql exactly or prod `validate()` refuses to start.
- **Schema rules that are easy to undo by accident:**
  - **No stored aggregates anywhere.** If a count is derivable, compute it at
    read time -- the app aggregates in Java from `countBy...` by design, so a
    persisted total can only ever be stale. Do not reintroduce
    `total_students` or `has_disciplinary_action`. Do not add a trigger to
    maintain them either: that keeps the second source of truth and hides the
    drift instead of removing the column.
  - **Every FK is named `fk_<table>_<column-without-_id>`** (e.g.
    `fk_ssts_grades_teacher1`, NOT `..._teacher1_id`), so a future migration can
    drop one by name.
  - **Every FK column has an explicit index.** Postgres only indexes the
    REFERENCED side, so an unindexed FK silently turns each parent DELETE into
    a full scan of the child table.
  - **A dangling FK-shaped column is not a reference.** A NOT NULL column
    pointing at no table makes its table uninsertable while storing nothing.
    Either give such a column a real target or drop it.
  - **Two `academic_year` columns, one format.** `ssts_grades.academic_year` and
    `ssts_calendar_events.academic_year` both use VARCHAR(9) CHECK
    `^[0-9]{4}-[0-9]{4}$` so they are directly comparable, but there is no FK
    between them: `ssts_calendar_events` is entity-backed and cannot take a new
    column.
  - **`ssts_grades.teacher1_id/teacher2_id` can still point at a PARENT
    account.** A Postgres FK cannot check `user_type` because it is not part of
    the referenced key, so every query that lists or renders a grade teacher
    MUST filter to `user_type = 'staff'`. Closing it properly needs a trigger or
    a composite FK to a UNIQUE `(id, user_type)`; do not add a trigger, since it
    would put a second source of truth outside the app.
  - **Soft delete is `is_active` + `start_date`/`end_date` on master data, and
    deliberately ABSENT on append-only logs** (`ssts_user_sessions`,
    `ssts_login_attempts`, `ssts_password_reset_tokens`,
    `ssts_disciplinary_actions`). `is_closed` on disciplinary actions is a
    workflow state, not a deletion flag -- do not merge the two concepts.

## Where to look for X
- Auth/login: `config/SecurityConfig.java`, `config/RoleAwareAuthenticationSuccessHandler.java`
  (+ its test), `service/LoginService.java`, `service/SstsUserDetailsService.java`,
  `templates/login.html`
- Logout: `SecurityConfig`'s `.logout(...)` block; the nav button is a POST form
- Password reset: `service/PasswordResetService.java`,
  `model/SstsPasswordResetToken.java`, `templates/forgot-password.html`,
  `templates/reset-password.html`
- Super-admin module: `controller/SuperAdminController.java`,
  `templates/superadmin/dashboard.html` (landing page + module cards)
- The nine super-admin screens, all following one shape
  (`list.html` + `form.html`, URL-guarded, `@PreAuthorize`):
  - calendar `CalendarAdminController` / `CalendarAdminService`
    (`@CacheEvict(CALENDAR_EVENTS, allEntries)`), `SstsCalendarEvent`
  - team `TeamAdminController` / `TeamAdminService`
    (`@CacheEvict(TEAM_MEMBERS, allEntries)`), `SstsUser`. Members are
    `ssts_users` rows with `userType` 'staff'/'volunteer'. The admin list is
    `findByUserTypeInOrderByFullNameAsc` (ignores `isActive` so a deactivated
    member stays editable); the edit form uses plain `findById`. New members get
    `TeamAdminService.DEFAULT_ROLE` ('volunteer_coordinator'). username, email,
    passwordHash and role are deliberately NOT editable there.
  - users `UserAdminController` / `UserAdminService` (uncached), `SstsUser`.
    `findAllForAdmin()` and `findByIdWithRole(Long)` both `LEFT JOIN FETCH` the
    role so rendering doesn't hit a lazy-loading failure; plus `countByRoleId`,
    `countByRoleName`, `countByIsActiveTrue/False`,
    `countByEmailVerifiedFalse`, `countByUserTypeAndIsActive` (the last three
    also feed ReportsService)
  - roles `RoleAdminController` / `RoleAdminService` (uncached), `SstsRole`.
    `findAllByOrderByNameAsc`, `existsByName`, `countByNameIgnoreCase`
    (a `@Query` -- Spring Data has no derived IgnoreCase count for that shape),
    `countByIsActiveTrue`
  - gallery `GalleryAdminController` / `GalleryAdminService`
    (`@CacheEvict(GALLERY_EVENTS, allEntries)`), `SstsGalleryEvent`
  - announcements `AnnouncementAdminController` / `AnnouncementAdminService`
    (no `@CacheEvict`), `SstsAnnouncement`
  - reports `ReportsAdminController` / `ReportsService` (read-only, uncached),
    `ReportsView`
  - donors `DonorAdminController` / `DonorService` (`@CacheEvict(DONORS)`,
    allEntries), `SstsDonor`, `SstsDonorRepository`, plus
    `DonorPhotoStorageService`. See the Donors section below -- this module has
    rules the other six do not.
  - donations `DonationAdminController` / `DonationService` (deliberately
    UNCACHED, no `@CacheEvict` because there is nothing cached to evict),
    `SstsDonation`, `SstsDonationRepository`
  Each has a `SstsXxxServiceTest` (mock repo + CacheConfig) and a
  `web/SstsXxxControllerTest` (@WebMvcTest slice). Adding a module means all of:
  controller, service, entity, repository, list+form templates, the
  `.admin-table--<module>` widths in admin.css, the nav submenu, the
  /superadmin cards, and the SecurityConfig URL rule.
- Team/calendar caching + reload endpoints: `service/TeamService.java`,
  `service/CalendarService.java`, `controller/CacheAdminController.java` (PATCH
  `/api/team/cache`, `/api/calendar/cache`, `/api/gallery/cache`; named for both
  caches, not just team)
- Navigation: `templates/fragments/nav.html` + `controller/CommonModelAdvice.java`
  + `tests web/NavFragmentTest.java`
- Frontend entry points: `templates/index.html` (home), `templates/page.html`
  (shared layout for about/team/calendar/contact per PageController's PAGES
  map), `templates/gallery.html` (own template), `templates/fragments/nav.html`
- Gallery page behaviour: `PageController.gallery()` +
  `static/js/gallery.js` (hover a card to smooth-scroll it to page center;
  mouse within 90px of the window edge auto-scrolls via rAF, MAX_SPEED
  18px/frame -- keep EDGE_ZONE/MAX_SPEED there when adjusting feel) +
  `static/css/gallery.css`
- **Gallery photo UPLOADS: `service/GalleryImageStorageService`** (S3/R2, see
  Images above) + `GalleryAdminController` create/update. The public card
  renders with `th:src="@{...}"`, a LINK expression, which passes an absolute
  `https://` bucket URL through untouched -- do NOT "fix" that to `th:src="${...}"`
  or the committed relative paths lose their context-path handling.
  `GalleryImageUrlRenderingTest` renders the real template through MockMvc and
  pins all three forms (relative, absolute, multi-slide); it needs a
  `@MockitoBean` for every service `PageController` injects.
- Role dashboards: `controller/DashboardController.java`,
  `templates/admin/dashboard.html`, `templates/parent/dashboard.html`
- Parent module: `model/SstsFamily.java`, `service/ParentService.java`,
  `repository/SstsFamilyRepository.java`, and the `processRegister` path in
  `controller/LoginController.java` (which calls `LoginService.register` ->
  `ParentService.createFamilyForNewParent`). A parent's contact block lives on
  the family row; `templates/parent/dashboard.html` is still static text and has
  no family/children data yet, so the portal is a Phase-2 gap.
- Per-profile config: `application.yml` + `application-{dev,stage,prod}.yml`
  (dev: SQL on, `ddl-auto=update`, template cache off | stage: SQL off,
  `update`, cache on | prod: SQL off, `validate`, cache on). docker-compose
  passes SPRING_PROFILES_ACTIVE (default dev); Render should set prod.
- Docker/deploy: `Dockerfile`, `docker-compose.yml`

## Do NOT read or modify
- `build/`, `.gradle/`, `bin/` -- generated, always stale
- `node_modules/` -- only dependency is prettier
- `.kilo/` -- another tool's worktree metadata, unrelated to app code
- `c.txt`, `c2.txt`, `r.html` -- stray scratch files at repo root
