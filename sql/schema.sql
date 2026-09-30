-- ============================================================
-- SSTS database schema.
--
-- DECLARATIVE, NOT INCREMENTAL. Every table is declared in its FINAL shape --
-- there are no ALTER/backfill migrations, no DO $$ blocks, no triggers. Still in
-- dev, so the workflow is: drop the database, run this file, then the seeds.
-- Applying it to an already-divergent database is a NO-OP (CREATE TABLE IF NOT
-- EXISTS), not a convergence, so never rely on it to repair an existing shape.
--
--   psql -v ON_ERROR_STOP=1 -f sql/schema.sql   (or ./gradlew executeSchema)
--   psql -v ON_ERROR_STOP=1 -f sql/data.sql      roles, users, staff, calendar,
--                                                gallery, announcements
--
-- That is the whole SQL story: two files. The former staff_profiles_seed.sql and
-- grant_super_admin.sql were absorbed into data.sql, and the superseded
-- merge_user_profiles_into_users.sql was deleted. ON_ERROR_STOP=1 is mandatory
-- in both cases -- without it psql reports a failing statement and still exits
-- 0, so a half-applied script looks like a clean run.
--
-- Prod runs ddl-auto=validate, so apply this BEFORE deploying a build that maps
-- these tables, and keep schema and entity in step.
--
-- ------------------------------------------------------------------
-- ENTITY-BACKED TABLES vs SCHEMA-ONLY TABLES -- READ THIS FIRST
-- ------------------------------------------------------------------
-- Nine tables are mapped by a JPA @Entity: ssts_roles, ssts_users,
-- ssts_families, ssts_user_sessions, ssts_login_attempts,
-- ssts_password_reset_tokens, ssts_calendar_events, ssts_gallery_events,
-- ssts_announcements. Prod runs `validate`, which checks that every column the
-- entity maps EXISTS and is type-compatible -- it does not check constraints,
-- indexes or naming. So for these tables you may freely ADD named constraints,
-- CHECKs and indexes, but you may NOT rename, retype, drop or re-null a mapped
-- column without changing the entity in the same commit.
--
-- The seven school-system tables (ssts_locations, ssts_classrooms,
-- ssts_doctors, ssts_gradelevels, ssts_grades, ssts_students,
-- ssts_disciplinary_actions) have NO entities yet, so their shape is free to
-- change. `ddl-auto=update` never creates or validates them: this file is the
-- only thing maintaining them. If you add an entity for one later, its columns
-- must match this file exactly or prod `validate()` refuses to start.
--
-- ------------------------------------------------------------------
-- CONVENTIONS
-- ------------------------------------------------------------------
-- Naming
--   - snake_case columns, bigint identity PKs, `ssts_` table prefix.
--   - Every foreign key is NAMED `fk_<table>_<column>` -- never left to
--     Postgres' auto-generated `..._fkey`, so a drop in a future migration can
--     target it by name.
--   - Constraints: `fk_`, `chk_`, `uq_`, `idx_` prefixes.
--
-- Indexing
--   - EVERY foreign key column carries an explicit `CREATE INDEX`. Postgres does
--     NOT auto-index referencing columns (only the referenced PK/unique side),
--     so an unindexed FK silently turns every parent DELETE/UPDATE into a full
--     scan of the child table. Indexes covering several filters are combined
--     where the leading column matches the common query.
--
-- Soft delete
--   - Master/reference data uses `is_active boolean NOT NULL DEFAULT true`, and
--     carries `start_date`/`end_date` when the thing has a lifespan. A row is
--     retired by flipping is_active (reversible, keeps history), never by
--     DELETE -- deleting master data would cascade into real records.
--   - Editable content (calendar, gallery, announcements, users, roles) uses
--     `is_active` the same way.
--   - APPEND-ONLY LOGS DELIBERATELY HAVE NO is_active: ssts_user_sessions,
--     ssts_login_attempts, ssts_password_reset_tokens and
--     ssts_disciplinary_actions are history, and old rows must stay
--     queryable for audit. Their rows expire/are closed instead
--     (expires_at, is_closed). Do not add is_active to these.
--   - `is_closed` on ssts_disciplinary_actions is a WORKFLOW state (an
--     incident is resolved), not a deletion flag. Keep the two concepts
--     separate if a delete is ever needed.
--
-- Normalisation
--   - 1NF: every column is atomic. No comma-joined or delimited lists.
--   - 2NF/3NF: NO stored aggregate anywhere. If a count is derivable from
--     other rows it is computed at read time, not persisted -- the application
--     aggregates in Java from countBy... methods, so a persisted total can only
--     ever be stale. (This is why ssts_grades.total_students and
--     ssts_students.has_disciplinary_action do not exist here.)
--   - A nullable column whose FK-shaped name points at a table that does not
--     exist is not a reference, it is a lie. Either give it a real target or
--     drop it.
--
-- Images
--   - Images are files under src/main/resources/static/images/; the database
--     stores the relative path only. There is no upload endpoint.
--
-- Seeds
--   - seed_key marks rows a seed script created, so it can delete exactly its
--     own rows on a re-run. NEVER key seed cleanup on `title`: titles are
--     user-supplied and collide, and the cleanup would destroy admin content.
--
-- Time
--   - "today" in application code is util/SchoolTime.today() (APP_TIMEZONE,
--     default America/New_York), never LocalDate.now() -- the server runs in
--     UTC and would be 4-5h ahead of the school.
--   - Entity-backed tables use `timestamp(6) without time zone` for
--     LocalDateTime, which is what Hibernate generates and what validate()
--     compares. ssts_password_reset_tokens is the one exception: its entity
--     uses OffsetDateTime, so it genuinely is `timestamptz`.
--
-- Auth
--   - user_type is ROUTING ONLY (which dashboard to render), never
--     authorization. Access comes from role_id -> ssts_roles.name ->
--     SstsUser.getAuthorities().
-- ============================================================


-- ============================================================
-- Roles
--
-- `name` is load-bearing: authorities are derived as "ROLE_" || upper(name),
-- so the lowercase/underscore CHECK is what stops a space, dot, dash or
-- uppercase letter building a broken or colliding authority. This mirrors the
-- Java validation in RoleAdminService, and the two must agree.
--
-- The can_* flags are maintenance-only and are NOT read by any authorization
-- code; the /superadmin/roles module makes them editable but does not wire
-- them into security. Setting can_delete_users grants nothing.
-- ============================================================
CREATE TABLE IF NOT EXISTS ssts_roles (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name character varying(50) NOT NULL,
    description character varying(255),
    is_active boolean NOT NULL DEFAULT true,
    can_view_dashboard boolean NOT NULL DEFAULT false,
    can_view_reports boolean NOT NULL DEFAULT false,
    can_view_calendar boolean NOT NULL DEFAULT false,
    can_view_users boolean NOT NULL DEFAULT false,
    can_create_users boolean NOT NULL DEFAULT false,
    can_edit_users boolean NOT NULL DEFAULT false,
    can_delete_users boolean NOT NULL DEFAULT false,
    can_manage_classes boolean NOT NULL DEFAULT false,
    can_manage_content boolean NOT NULL DEFAULT false,
    can_publish_content boolean NOT NULL DEFAULT false,
    can_edit_pages boolean NOT NULL DEFAULT false,
    can_view_audit_logs boolean NOT NULL DEFAULT false,
    can_view_financials boolean NOT NULL DEFAULT false,
    can_view_donors boolean NOT NULL DEFAULT false,
    can_manage_donors boolean NOT NULL DEFAULT false,
    can_manage_events boolean NOT NULL DEFAULT false,
    can_manage_levels boolean NOT NULL DEFAULT false,
    can_manage_settings boolean NOT NULL DEFAULT false,
    can_manage_volunteers boolean NOT NULL DEFAULT false,
    can_send_announcements boolean NOT NULL DEFAULT false,
    can_send_newsletters boolean NOT NULL DEFAULT false,
    created_at timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP,
    -- A name is immutable after creation (renaming would silently detach every
    -- holder), so this is a table-level uniqueness rule rather than a column
    -- NOT NULL UNIQUE only.
    CONSTRAINT uq_ssts_roles_name UNIQUE (name),
    -- Mirrors RoleAdminService's role-name rule. A space, dot, dash or
    -- uppercase letter would build a broken or colliding authority.
    CONSTRAINT chk_ssts_roles_name_format CHECK (name ~ '^[a-z][a-z0-9_]*$')
);

COMMENT ON TABLE ssts_roles IS
    'Access roles. name is the authority source: ROLE_ || upper(name). The '
    'can_* columns are not wired into authorization.';


-- ============================================================
-- Users -- login identity AND per-person detail, in one row.
--
-- Deliberately NOT split into a separate profile table. A 1:1 child with a
-- shared lifecycle buys nothing in normalisation terms and costs a lot: the
-- circular FK (users.profile_id <-> profiles.user_id) is what forces a
-- two-step create and a delete that must clear the child first. Per-person
-- columns are a FUNCTION of the user, so they belong here.
--
-- phone/address here are for staff, volunteer and admin. For
-- user_type='parent', ssts_families is the authoritative source (it has room
-- for both parents); these columns must not be used for parents.
--
-- ENTITY-BACKED: column set is frozen by SstsUser. See header.
-- ============================================================
CREATE TABLE IF NOT EXISTS ssts_users (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    username character varying(50) NOT NULL,
    email character varying(100) NOT NULL,
    password_hash character varying(255) NOT NULL,
    full_name character varying(100) NOT NULL,
    role_id bigint NOT NULL,
    user_type character varying(20) NOT NULL DEFAULT 'parent',
    designation character varying(100),
    joining_date date,
    last_date date,
    receive_newsletter boolean NOT NULL DEFAULT true,
    receive_volunteer_updates boolean NOT NULL DEFAULT true,
    is_active boolean NOT NULL DEFAULT true,
    email_verified boolean NOT NULL DEFAULT false,
    last_login timestamp(6) without time zone,
    -- Per-person detail
    date_of_birth date,
    bio text,
    occupation character varying(100),
    employer character varying(100),
    years_in_community integer,
    phone character varying(20),
    alternate_email character varying(100),
    address_line1 character varying(255),
    address_line2 character varying(255),
    city character varying(100),
    state character varying(50),
    zip_code character varying(20),
    country character varying(50) DEFAULT 'USA',
    avatar_url character varying(500),
    prior_education text,
    prior_teaching_experience text,
    prior_tamil_experience text,
    prior_volunteer_experience text,
    certifications text,
    interests text,
    department character varying(100),
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- No ON DELETE CASCADE anywhere below: deleting a role that users still
    -- hold, or a user that other rows reference, must fail loudly in the
    -- service layer (which turns it into a flash message) rather than silently
    -- deleting accounts. RoleAdminService catches this exact violation.
    CONSTRAINT fk_ssts_users_role FOREIGN KEY (role_id)
        REFERENCES ssts_roles (id) ON DELETE RESTRICT,
    CONSTRAINT uq_ssts_users_username UNIQUE (username),
    CONSTRAINT uq_ssts_users_email UNIQUE (email),
    CONSTRAINT chk_ssts_users_user_type CHECK (user_type IN ('parent', 'staff', 'volunteer', 'admin')),
    -- Deliberately asymmetric: alternate_email is an OPTIONAL second address,
    -- so a blank string is a legitimate "none supplied" and must not be
    -- rejected as a malformed address.
    CONSTRAINT chk_ssts_users_email CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    CONSTRAINT chk_ssts_users_alternate_email CHECK (
        alternate_email IS NULL
        OR btrim(alternate_email) = ''
        OR alternate_email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'
    ),
    CONSTRAINT chk_ssts_users_years_in_community CHECK (years_in_community IS NULL OR years_in_community >= 0),
    -- Permissive on purpose: accepts the formats a school actually collects
    -- (dashes, spaces, parens, extensions, country prefix) and allows a blank
    -- string as "not collected" without storing NULL.
    CONSTRAINT chk_ssts_users_phone CHECK (
        phone IS NULL OR btrim(phone) = '' OR phone ~ '^[0-9+(). -]{7,25}$'
    ),
    CONSTRAINT chk_ssts_users_dates CHECK (last_date IS NULL OR joining_date IS NULL OR last_date >= joining_date)
);

COMMENT ON TABLE ssts_users IS
    'Login identity plus per-person detail. There is no separate profile table: '
    'the 1:1 child with a shared lifecycle added a circular FK and a two-step '
    'save for no normalisation benefit.';

CREATE INDEX IF NOT EXISTS idx_ssts_users_email  ON ssts_users(email);
CREATE INDEX IF NOT EXISTS idx_ssts_users_role   ON ssts_users(role_id);
CREATE INDEX IF NOT EXISTS idx_ssts_users_type   ON ssts_users(user_type);
CREATE INDEX IF NOT EXISTS idx_ssts_users_active ON ssts_users(is_active);


-- ============================================================
-- Families -- ONE ROW PER FAMILY, not per person.
--
-- Named ssts_families, NOT ssts_parents, because the row describes a HOUSEHOLD
-- holding two different people (parent1_/parent2_ and phone1/phone2 column
-- pairs) plus their shared address. "ssts_parents" would read as one row per
-- parent and invite exactly the per-child duplication this shape exists to
-- prevent.
--
-- HEALTH AND SAFETY DATA DOES NOT LIVE HERE. pickup_authorized,
-- medical_conditions, allergies and insurance_info live on ssts_students
-- because they are facts about a CHILD: on a per-family row two siblings
-- share one value, so one child's peanut allergy would overwrite the other's.
-- What stays here is genuinely household-level -- who the two parents are and
-- how to reach them.
--
-- The student relationship runs the OTHER way: ssts_students.family_id points
-- here. A student_id column here would cap a family at one child, so two
-- siblings would need two rows duplicating both parents' contact details.
--
-- ENTITY-BACKED (SstsFamily) and now REFERENCED: SstsFamilyRepository +
-- ParentService own the parent module, and registration creates a family row.
-- It is still the one school-system table with an entity -- there is no
-- SstsStudent entity, so ssts_students is maintained by this file alone.
-- ============================================================
CREATE TABLE IF NOT EXISTS ssts_families (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    -- SET NULL, not CASCADE, and the reason is the combination with
    -- ssts_students.family_id ON DELETE RESTRICT below. CASCADE would mean
    -- deleting a parent's login tries to delete the family row -- and if that
    -- family still has enrolled students, RESTRICT blocks the cascade, so the
    -- whole DELETE on ssts_users fails with an opaque foreign-key error and the
    -- account can't be removed either. That is the worst of both outcomes: the
    -- family survives, but so does the login nobody wanted. SET NULL just
    -- unlinks, which is also what makes user_id NULLABLE coherent -- a family
    -- is allowed to exist with no login on the way IN, so it must not be
    -- destroyed when one goes away on the way OUT. Same rule as the optional
    -- admitted_grade_id / current_grade_id / doctor_id links in ssts_students.
    user_id bigint,
    emergency_contact character varying(20),
    parent1_full_name character varying(100),
    parent1_email character varying(100),
    parent2_full_name character varying(100),
    parent2_email character varying(100),
    street character varying(255),
    city character varying(100),
    state character varying(50),
    country character varying(50) DEFAULT 'USA',
    zip character varying(20),
    phone1 character varying(20),
    phone2 character varying(20),
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ssts_families_user FOREIGN KEY (user_id)
        REFERENCES ssts_users (id) ON DELETE SET NULL,
    -- Partial-family rows are legal (a family can exist with neither parent's
    -- portal account yet), so a NOT NULL name CHECK would be wrong here. What
    -- IS enforced is that a supplied name/email pair is internally consistent.
    CONSTRAINT chk_ssts_families_parent1_email CHECK (
        parent1_email IS NULL
        OR btrim(parent1_email) = ''
        OR parent1_email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'
    ),
    CONSTRAINT chk_ssts_families_parent2_email CHECK (
        parent2_email IS NULL
        OR btrim(parent2_email) = ''
        OR parent2_email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'
    ),
    -- An email with no name is unusable for a school: nobody can be contacted.
    -- Blank name WITH a blank email is fine (the parent is simply not recorded).
    CONSTRAINT chk_ssts_families_parent1_email_needs_name CHECK (
        parent1_email IS NULL OR btrim(parent1_email) = '' OR parent1_full_name IS NOT NULL
    ),
    CONSTRAINT chk_ssts_families_parent2_email_needs_name CHECK (
        parent2_email IS NULL OR btrim(parent2_email) = '' OR parent2_full_name IS NOT NULL
    ),
    CONSTRAINT chk_ssts_families_phones CHECK (
        (phone1 IS NULL OR btrim(phone1) = '' OR phone1 ~ '^[0-9+(). -]{7,25}$')
        AND (phone2 IS NULL OR btrim(phone2) = '' OR phone2 ~ '^[0-9+(). -]{7,25}$')
        AND (emergency_contact IS NULL OR btrim(emergency_contact) = '' OR emergency_contact ~ '^[0-9+(). -]{7,25}$')
    )
);

COMMENT ON TABLE ssts_families IS
    'One row per HOUSEHOLD, holding both parents and the shared address. '
    'Health and safety data is per student, not here. user_id is a nullable '
    'optional link (SET NULL), so a family outlives any portal login.';

CREATE INDEX IF NOT EXISTS idx_ssts_families_user       ON ssts_families(user_id);
CREATE INDEX IF NOT EXISTS idx_ssts_families_parent1_email ON ssts_families(parent1_email);
CREATE INDEX IF NOT EXISTS idx_ssts_families_parent2_email ON ssts_families(parent2_email);


-- ============================================================
-- Login sessions and failed-attempt tracking.
--
-- APPEND-ONLY: no is_active. A session row is history and must stay
-- queryable for audit; it stops being usable by expiring (expires_at) or by
-- its parent user disappearing (CASCADE), never by a soft-delete flag.
-- ============================================================
CREATE TABLE IF NOT EXISTS ssts_user_sessions (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL,
    session_token character varying(255) NOT NULL,
    refresh_token character varying(255),
    ip_address character varying(45),
    user_agent text,
    expires_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- CASCADE is correct and safe here: a session is meaningless without its
    -- user, and nothing else references a session. This is the opposite case to
    -- ssts_families.user_id, which SET NULLs because a family is real data.
    CONSTRAINT fk_ssts_user_sessions_user FOREIGN KEY (user_id)
        REFERENCES ssts_users (id) ON DELETE CASCADE,
    CONSTRAINT uq_ssts_user_sessions_token UNIQUE (session_token)
);

CREATE INDEX IF NOT EXISTS idx_ssts_sessions_user  ON ssts_user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_ssts_sessions_token ON ssts_user_sessions(session_token);

CREATE TABLE IF NOT EXISTS ssts_login_attempts (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    email character varying(100) NOT NULL,
    ip_address character varying(45),
    success boolean NOT NULL DEFAULT false,
    failure_reason character varying(255),
    attempted_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Not UNIQUE and not FK: this table records attempts against an address
    -- that may not correspond to any account (that is the point of a brute
    -- force log), so an email CHECK is the only integrity rule that applies.
    CONSTRAINT chk_ssts_login_attempts_email CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    CONSTRAINT chk_ssts_login_attempts_failure_reason CHECK (
        success OR failure_reason IS NULL OR btrim(failure_reason) <> ''
    )
);

CREATE INDEX IF NOT EXISTS idx_ssts_login_attempts_email ON ssts_login_attempts(email);
CREATE INDEX IF NOT EXISTS idx_ssts_login_attempts_time  ON ssts_login_attempts(attempted_at);
-- Supports the login-attempt lockout sweep ("failed attempts from this address
-- since X"), which is a range scan on ip_address + time. Declared here, after
-- the table, because Postgres resolves an index against an existing relation.
CREATE INDEX IF NOT EXISTS idx_ssts_login_attempts_ip_time
    ON ssts_login_attempts (ip_address, attempted_at DESC);


-- ============================================================
-- Password reset tokens. APPEND-ONLY: no is_active; single-use is enforced by
-- the `used` flag, and expiry by expires_at.
--
-- This is the one table using timestamptz, because its entity uses
-- OffsetDateTime. Do not "normalise" it to timestamp -- validate() will fail.
-- ============================================================
CREATE TABLE IF NOT EXISTS ssts_password_reset_tokens (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    email character varying(100) NOT NULL,
    token character varying(255) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    used boolean NOT NULL DEFAULT false,
    created_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ssts_password_reset_tokens_token UNIQUE (token),
    -- An address is kept on the row so PasswordResetService can find a live
    -- token without a users join, which is why it is not a foreign key: the
    -- token must still be findable (and reportable) after an account is gone.
    CONSTRAINT chk_ssts_password_reset_tokens_email CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$')
);

CREATE INDEX IF NOT EXISTS idx_ssts_reset_tokens_email ON ssts_password_reset_tokens(email);


-- ============================================================
-- Calendar events (public /calendar page, /superadmin/calendar)
--
-- academic_year is e.g. '2026-2027' (Aug-Jul cycle) and is always produced by
-- util/AcademicYear.of(), never free-typed. The CHECK keeps manual inserts
-- safe so equality and string ordering stay correct. /calendar shows only the
-- current academic year, so a new year needs a new seed block.
--
-- ssts_grades.academic_year uses the identical format and CHECK so the two are
-- directly comparable, but it is not a foreign key: ssts_calendar_events is
-- ENTITY-BACKED and cannot take an FK column without an entity change.
-- ============================================================
CREATE TABLE IF NOT EXISTS ssts_calendar_events (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title character varying(255) NOT NULL,
    event_date date NOT NULL,
    end_date date,
    event_type character varying(20) NOT NULL DEFAULT 'working',
    academic_year character varying(9) NOT NULL,
    description character varying(500),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- working = class/session day, holiday = no school
    CONSTRAINT chk_ssts_calendar_events_event_type CHECK (event_type IN ('working', 'holiday')),
    CONSTRAINT chk_ssts_calendar_events_academic_year CHECK (academic_year ~ '^[0-9]{4}-[0-9]{4}$'),
    -- A multi-day span must not end before it starts. NULL end_date means a
    -- single-day event, which is the common case.
    CONSTRAINT chk_ssts_calendar_events_dates CHECK (end_date IS NULL OR end_date >= event_date)
);

CREATE INDEX IF NOT EXISTS idx_ssts_calendar_events_year ON ssts_calendar_events(academic_year);
CREATE INDEX IF NOT EXISTS idx_ssts_calendar_events_date ON ssts_calendar_events(event_date);
-- The public feed filters on year + active and orders by date.
CREATE INDEX IF NOT EXISTS idx_ssts_calendar_events_year_active_date
    ON ssts_calendar_events (academic_year, is_active, event_date);


-- ============================================================
-- Gallery events (public /gallery page, /superadmin/gallery)
--
-- image_url is a RELATIVE static path, e.g. '/images/gallery/pongal.jpg', with
-- the file committed under static/images/gallery/. Feed order is display_order
-- ascending, then newest event_date first. NULL image_url is legal and renders
-- as a title-initial gradient tile. image_urls holds extra slider photos, one
-- per line, under the event's own folder (see GalleryImageStorageService).
-- ============================================================
CREATE TABLE IF NOT EXISTS ssts_gallery_events (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title character varying(255) NOT NULL,
    event_date date NOT NULL,
    image_url character varying(500),
    -- Extra photos for the card's hover slider, one per line. Each is a
    -- RELATIVE static path under the event's own folder, e.g.
    -- '/images/gallery/pongal/2.jpg'. First line order = slide order. NULL or
    -- blank rows render as the single-image card.
    image_urls text,
    description text,
    display_order integer NOT NULL DEFAULT 0,
    is_active boolean NOT NULL DEFAULT true,
    seed_key character varying(64),
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- A stored path must be EITHER a relative app path under /images/ (an
    -- image committed to static/images/) OR an https:// URL into the
    -- configured gallery bucket. Uploaded photos are objects in S3/R2, not
    -- files, because a container's filesystem is discarded on every deploy --
    -- see GalleryImageStorageService. http://, data:, javascript: and bare
    -- absolute filesystem paths are all rejected.
    CONSTRAINT chk_ssts_gallery_events_image_url CHECK (
        image_url IS NULL
        OR btrim(image_url) = ''
        OR image_url ~ '^/images/[A-Za-z0-9._/-]+$'
        OR image_url ~ '^https://[A-Za-z0-9.-]+/[A-Za-z0-9._/-]+$'
    ),
    -- Same rule per slider line: every line must be a relative /images/ path
    -- or an https:// bucket URL.
    --
    -- The `$` sits OUTSIDE the repeating group, and that placement is
    -- load-bearing. Written as `(\s*...\s*$)+` it can only ever match a single
    -- trailing line: the inner `\s*$` has to match at end-of-string, and
    -- Postgres `$` does not match at an interior newline, so the repetition
    -- could never advance past line 1. Every multi-photo entry was rejected
    -- with a raw 500 (constraint violation -> DataIntegrityViolationException).
    -- With `$` outside, the group's trailing `\s*` eats the newline and the
    -- repetition advances line by line.
    CONSTRAINT chk_ssts_gallery_events_image_urls CHECK (
        image_urls IS NULL
        OR btrim(image_urls) = ''
        OR image_urls ~ '^(\s*(/images/[A-Za-z0-9._/-]+|https://[A-Za-z0-9.-]+/[A-Za-z0-9._/-]+)\s*)+$'
    ),
    CONSTRAINT chk_ssts_gallery_events_display_order CHECK (display_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_ssts_gallery_events_active
    ON ssts_gallery_events(is_active, display_order, event_date);


-- ============================================================
-- Announcements (public home-page marquee, /superadmin/announcements)
--
-- Visible when active AND not expired. expires_on is nullable: NULL means
-- "never expires", and a row stops rendering the day AFTER expires_on with no
-- admin action (SstsAnnouncement.isVisibleOn / findVisibleOn). This feed is
-- deliberately UNCACHED -- a 24h TTL would keep an expired notice visible.
-- ============================================================
CREATE TABLE IF NOT EXISTS ssts_announcements (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title character varying(255) NOT NULL,
    message text NOT NULL,
    announce_date date NOT NULL,
    expires_on date,
    is_active boolean NOT NULL DEFAULT true,
    seed_key character varying(64),
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- An announcement cannot expire before it was announced. NULL = never.
    CONSTRAINT chk_ssts_announcements_dates CHECK (expires_on IS NULL OR expires_on >= announce_date),
    CONSTRAINT chk_ssts_announcements_message CHECK (btrim(message) <> '')
);

-- Partial index: the public feed only ever asks for active, unexpired rows, and
-- it is the highest-traffic query in the app.
CREATE INDEX IF NOT EXISTS idx_ssts_announcements_visible
    ON ssts_announcements (is_active, announce_date DESC);


-- ============================================================
-- SCHOOL ACADEMIC STRUCTURE
--
-- Derived from u877669764_prsstscrmdb.pdf (classroom, disciplinaryaction,
-- doctor, grade, gradelevel, location, teacher). Deviations from the source:
--   1. varchar(36) UUID-as-text PKs -> bigint identity, matching every other
--      table here.
--   2. Typos fixed: clasroomid -> classroom_id, craetedts/createtimestamp/
--      createdts -> created_at, lastupdatedts -> updated_at, birthpalce ->
--      birthplace, perviousexperiencedetails -> previous_experience_details.
--   3. tinyint(1)/char(1) flags -> BOOLEAN.
--   4. Address/phone widths normalised to ssts_users' sizing.
--   5. gradelevel.name was INTEGER; it is a LABEL, not a number -- the levels
--      are Munmazhalai, Mazhalai, Nilai 1, Nilai 2, etc. Now VARCHAR(50) and
--      UNIQUE. (The PDF also has a separate `level` integer; see below.)
--   6. gradelevel.affiliation_id was NOT NULL but pointed at a table the export
--      does not define. A NOT NULL column that references nothing is not a
--      reference, it is a lie -- it made the table uninsertable while storing
--      no information. DROPPED. If affiliations become a real concept, bring
--      it back as a real FK to an ssts_affiliations table.
--   7. grade.totalstudents was varchar(36) (unsummable) -> INTEGER -> REMOVED.
--      A stored total is a 3NF violation: it is exactly
--      COUNT(ssts_students WHERE current_grade_id = grades.id), so it drifts
--      the moment a student transfers or is unenrolled, and nothing in the app
--      reads it. Compute it at read time. The trigger alternative was rejected
--      deliberately -- it would put a second source of truth outside the
--      application and hide the drift instead of removing the column.
--   8. grade.gradeyear was MySQL year(4), which Postgres lacks. Stored as
--      academic_year VARCHAR(9) in the SAME 'YYYY-YYYY' format (and the same
--      CHECK) as ssts_calendar_events.academic_year, because "2026" and
--      "2026-2027" are not comparable as written. No FK between them: the
--      calendar table is entity-backed and cannot take a new column.
--   9. grade.teacher2id was NOT NULL; made nullable, since a vacant co-teacher
--      slot is a real case. Flip back if two teachers per grade is a hard rule.
--   10. student.admittedgradeid/currentgradeid were NOT NULL; nullable, since
--      requiring a value breaks existing rows and pre-placement enrollment.
--   11. student.hasdisciplinaryaction was a boolean duplicating the existence of
--      rows in ssts_disciplinary_actions. REMOVED as a stored aggregate for the
--      same reason as (7); the flag silently disagreed with the log it claimed
--      to summarise.
--   12. grade.teacher1id/teacher2id could be the same person; now excluded.
--
-- The PDF's `parent` and `user` tables are NOT created -- ssts_families and
-- ssts_users cover them. The PDF's `teacher` table is likewise gone: a teacher
-- is a ssts_users row with user_type 'staff', so there is no parallel roster
-- to drift out of sync with accounts.
--
-- There is deliberately NO SstsStudent entity, repository or controller for any
-- of these tables, so ddl-auto=update never creates or validates them. This file
-- is the only thing maintaining them, which means adding an entity later must be
-- paired with a matching column set here.
-- ============================================================


-- ------------------------------------------------------------------
-- Locations -- physical school sites / campuses
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ssts_locations (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name character varying(100) NOT NULL,
    start_date date NOT NULL DEFAULT CURRENT_DATE,
    end_date date,
    street character varying(255) NOT NULL,
    city character varying(100) NOT NULL,
    state character varying(50) NOT NULL,
    zip character varying(20) NOT NULL,
    phone1 character varying(20) NOT NULL,
    phone2 character varying(20),
    facility_poc character varying(120) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- A site is a single physical place with one name; two rows named the same
    -- thing are a duplicate, not two campuses.
    CONSTRAINT uq_ssts_locations_name UNIQUE (name),
    CONSTRAINT chk_ssts_locations_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_ssts_locations_phone1 CHECK (phone1 ~ '^[0-9+(). -]{7,25}$'),
    CONSTRAINT chk_ssts_locations_phone2 CHECK (
        phone2 IS NULL OR btrim(phone2) = '' OR phone2 ~ '^[0-9+(). -]{7,25}$'
    ),
    -- "Retired" is expressed by end_date, so is_active and end_date must not
    -- contradict each other.
    CONSTRAINT chk_ssts_locations_active_consistent CHECK (is_active OR end_date IS NOT NULL)
);

COMMENT ON TABLE ssts_locations IS
    'Physical school sites. Soft-deleted via is_active plus end_date; the two '
    'are kept consistent by a CHECK.';

CREATE INDEX IF NOT EXISTS idx_ssts_locations_active ON ssts_locations(is_active);


-- ------------------------------------------------------------------
-- Classrooms -- physical rooms at a location
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ssts_classrooms (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name character varying(60) NOT NULL,
    size integer NOT NULL,
    start_date date NOT NULL DEFAULT CURRENT_DATE,
    end_date date,
    location_id bigint NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- RESTRICT: a location with classrooms is real, occupied data. Deleting it
    -- must fail and be handled in the application, not orphan the rooms.
    CONSTRAINT fk_ssts_classrooms_location FOREIGN KEY (location_id)
        REFERENCES ssts_locations (id) ON DELETE RESTRICT,
    -- A room is identified by its name WITHIN a site. Without this, two "Room
    -- 3" rows in one location are indistinguishable and ssts_grades.classroom_id
    -- becomes ambiguous. Scoped rather than global because two sites may each
    -- have a Room 1.
    CONSTRAINT uq_ssts_classrooms_location_name UNIQUE (location_id, name),
    CONSTRAINT chk_ssts_classrooms_size CHECK (size > 0),
    CONSTRAINT chk_ssts_classrooms_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_ssts_classrooms_active_consistent CHECK (is_active OR end_date IS NOT NULL)
);

COMMENT ON TABLE ssts_classrooms IS
    'Physical rooms within a location. Unique per (location_id, name).';

CREATE INDEX IF NOT EXISTS idx_ssts_classrooms_location ON ssts_classrooms(location_id);
CREATE INDEX IF NOT EXISTS idx_ssts_classrooms_active   ON ssts_classrooms(is_active);


-- ------------------------------------------------------------------
-- Doctors -- emergency contact roster
--
-- APPEND-ONLY-ish: a doctor stays on the roster and is deactivated rather than
-- deleted, because a student's doctor_id must remain resolvable for the record.
-- That is why is_active is added here (it was missing) and why nothing cascades
-- out of ssts_students.doctor_id.
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ssts_doctors (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name character varying(60) NOT NULL,
    phone character varying(20) NOT NULL,
    engage_if_emergency boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ssts_doctors_name UNIQUE (name),
    CONSTRAINT chk_ssts_doctors_phone CHECK (phone ~ '^[0-9+(). -]{7,25}$')
);

CREATE INDEX IF NOT EXISTS idx_ssts_doctors_active ON ssts_doctors(is_active);


-- ------------------------------------------------------------------
-- Grade levels -- the academic ladder: Munmazhalai, Mazhalai, Nilai 1, ...
--
-- name is a LABEL and was INTEGER in the source export, which cannot represent
-- "Munmazhalai" at all. It is now VARCHAR(50) and UNIQUE, because a grade
-- level is a member of a fixed vocabulary and two rows spelling it differently
-- would silently fragment "how many students are in Nilai 1?".
--
-- `level` is retained as the SORT ORDINAL. It is a transitive dependency of
-- name (level is a property of the label, not of the year the level runs in) and
-- lives here rather than on ssts_grades precisely so it is stored once. It is
-- deliberately NOT UNIQUE: ordering must stay stable if a label is ever
-- duplicated, and no query depends on the two being one-to-one.
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ssts_gradelevels (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name character varying(50) NOT NULL,
    level integer NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    start_date date NOT NULL DEFAULT CURRENT_DATE,
    end_date date,
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ssts_gradelevels_name UNIQUE (name),
    CONSTRAINT chk_ssts_gradelevels_level CHECK (level > 0),
    CONSTRAINT chk_ssts_gradelevels_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT chk_ssts_gradelevels_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

COMMENT ON TABLE ssts_gradelevels IS
    'Academic ladder (Munmazhalai, Mazhalai, Nilai 1, ...). name is a text '
    'label; level is the sort ordinal. Both are stored here rather than on '
    'ssts_grades because they are a property of the LEVEL, not of one year''s run.';

CREATE INDEX IF NOT EXISTS idx_ssts_gradelevels_active ON ssts_gradelevels(is_active);
-- Serves ORDER BY level (roster order) without a sort.
CREATE INDEX IF NOT EXISTS idx_ssts_gradelevels_level  ON ssts_gradelevels(level);


-- ------------------------------------------------------------------
-- Grades -- one grade in one year: a level, a room, one or two teachers.
--
-- teacher1_id/teacher2_id reference ssts_users, NOT a teachers table: a teacher
-- is a user with user_type 'staff'.
--
-- KNOWN LIMITATION (cannot be expressed as a plain constraint in Postgres): the
-- FK permits a grade to be assigned to a PARENT or admin account, because
-- ssts_users.role and user_type are not part of the referenced key. Every query
-- that lists or renders a grade teacher MUST filter to user_type = 'staff'.
-- Closing this properly needs either a trigger or a composite FK to a UNIQUE
-- (id, user_type) -- deliberately not added here, because nothing reads these
-- tables yet and a trigger would add a second source of truth outside the app.
-- The same query-level filter is documented in AGENTS.md.
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ssts_grades (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    gradelevel_id bigint NOT NULL,
    academic_year character varying(9) NOT NULL,
    classroom_id bigint NOT NULL,
    teacher1_id bigint NOT NULL,
    teacher2_id bigint,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- All RESTRICT: a grade referencing a level, room or teacher that other
    -- rows depend on must not vanish underneath them.
    CONSTRAINT fk_ssts_grades_gradelevel FOREIGN KEY (gradelevel_id)
        REFERENCES ssts_gradelevels (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ssts_grades_classroom FOREIGN KEY (classroom_id)
        REFERENCES ssts_classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ssts_grades_teacher1 FOREIGN KEY (teacher1_id)
        REFERENCES ssts_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ssts_grades_teacher2 FOREIGN KEY (teacher2_id)
        REFERENCES ssts_users (id) ON DELETE RESTRICT,

    -- Same format and CHECK as ssts_calendar_events.academic_year, so the two
    -- are directly comparable. No FK: the calendar table is entity-backed and
    -- cannot take a new column without an entity change.
    CONSTRAINT chk_ssts_grades_academic_year CHECK (academic_year ~ '^[0-9]{4}-[0-9]{4}$'),
    -- A co-teacher slot must be a DIFFERENT person from the lead teacher.
    -- Previously the same user could be assigned twice.
    CONSTRAINT chk_ssts_grades_distinct_teachers CHECK (teacher2_id IS NULL OR teacher1_id <> teacher2_id),
    -- One grade per level per year per room: without this, "the Nilai 1 grade"
    -- has several indistinguishable rows.
    CONSTRAINT uq_ssts_grades_level_year_room UNIQUE (gradelevel_id, academic_year, classroom_id)
);

COMMENT ON TABLE ssts_grades IS
    'One grade in one academic year: a level, a classroom, one or two teachers. '
    'NO total_students column -- that count is derived from ssts_students and '
    'a stored copy would drift.';

CREATE INDEX IF NOT EXISTS idx_ssts_grades_gradelevel ON ssts_grades(gradelevel_id);
CREATE INDEX IF NOT EXISTS idx_ssts_grades_classroom  ON ssts_grades(classroom_id);
-- One composite, not a single-column index plus a composite: any lookup on
-- academic_year alone is served by the leading column of this index, so a
-- separate idx_ssts_grades_year would be pure write amplification.
CREATE INDEX IF NOT EXISTS idx_ssts_grades_year_active ON ssts_grades(academic_year, is_active);
-- The two teacher FKs are indexed explicitly: Postgres does not index
-- referencing columns, so without these every user DELETE is a full scan here.
CREATE INDEX IF NOT EXISTS idx_ssts_grades_teacher1    ON ssts_grades(teacher1_id);
CREATE INDEX IF NOT EXISTS idx_ssts_grades_teacher2    ON ssts_grades(teacher2_id);


-- ============================================================
-- Students (enrollment records, independent of login accounts)
--
-- family_id is NOT NULL: every student belongs to a family, and that family row
-- is the authoritative source for both parents' names, emails, address and
-- phones. Free-text parent_name/parent_phone/parent_email here would duplicate
-- it, so they are gone.
--
-- ON DELETE RESTRICT: deleting a family that still has enrolled students is a
-- mistake, and CASCADE (which would delete the students) is never right in a
-- school. SET NULL is impossible on a NOT NULL column.
--
-- parent_type is PER STUDENT -- the contact parent's relationship to THIS
-- child -- so the same parent can be 'mother' to one child and 'guardian' to
-- another. It deliberately does NOT live on ssts_families, which is one row per
-- FAMILY and holds both parents, so a single value there could only describe
-- one of them.
--
-- KNOWN GAP: this captures ONE relationship per student. Recording BOTH
-- parents' relationships to the same child needs a
-- ssts_student_parents(student_id, family_id, relationship) join table.
--
-- Grade lives ONLY in admitted_grade_id / current_grade_id. The free-text
-- grade and class_level columns, and ssts_families.student_grade, are gone:
-- grade was the most duplicated fact in this schema and every representation
-- drifted. current_grade_id is nullable -- a student may be enrolled before
-- being placed in a grade.
--
-- KNOWN GAP: pickup_authorized is TEXT, so it currently holds a free-form
-- description of who may collect the child. Under a strict reading it is a
-- multi-valued attribute and belongs in a
-- ssts_student_pickup_authorizations(student_id, authorised_name, relationship)
-- join table. It is left as text here because no code reads it and changing the
-- shape of unknown data is worse than documenting the debt.
-- ============================================================
CREATE TABLE IF NOT EXISTS ssts_students (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    first_name character varying(100) NOT NULL,
    last_name character varying(100),
    date_of_birth date,
    gender character varying(20),
    enrollment_date date NOT NULL DEFAULT CURRENT_DATE,
    pickup_authorized text,
    medical_conditions text,
    allergies text,
    insurance_info text,
    parent_type character varying(20),
    family_id bigint NOT NULL,
    admitted_grade_id bigint,
    current_grade_id bigint,
    doctor_id bigint,
    birthplace character varying(100),
    nationality character varying(100),
    first_registration_date date,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- family_id RESTRICT (see header). The three optional links SET NULL,
    -- matching ssts_families.user_id: they are genuinely optional, and
    -- unlinking a grade or a doctor must never delete a child's record.
    CONSTRAINT fk_ssts_students_family FOREIGN KEY (family_id)
        REFERENCES ssts_families (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ssts_students_admitted_grade FOREIGN KEY (admitted_grade_id)
        REFERENCES ssts_grades (id) ON DELETE SET NULL,
    CONSTRAINT fk_ssts_students_current_grade FOREIGN KEY (current_grade_id)
        REFERENCES ssts_grades (id) ON DELETE SET NULL,
    CONSTRAINT fk_ssts_students_doctor FOREIGN KEY (doctor_id)
        REFERENCES ssts_doctors (id) ON DELETE SET NULL,

    -- NULLABLE rather than NOT NULL-with-empty-string: unknown gender is a
    -- real state, and 'prefer_not_to_say' is a positive assertion, not the
    -- absence of one.
    CONSTRAINT chk_ssts_students_gender CHECK (gender IS NULL OR gender IN ('male', 'female', 'other', 'prefer_not_to_say')),
    -- parent_type describes the CONTACT parent, so it is meaningless without a
    -- family to identify them.
    CONSTRAINT chk_ssts_students_parent_type CHECK (
        parent_type IS NULL OR parent_type IN ('mother', 'father', 'guardian')
    ),
    -- Registration cannot precede enrolment, and a child cannot be born after
    -- enrolling.
    CONSTRAINT chk_ssts_students_dates CHECK (
        first_registration_date IS NULL OR first_registration_date <= enrollment_date
    ),
    CONSTRAINT chk_ssts_students_dob CHECK (date_of_birth IS NULL OR date_of_birth <= enrollment_date),
    -- The same grade cannot be both the admitted and the current one -- that
    -- means the student never moved, which is only true if the columns are
    -- equal, and expressing it as a constraint catches the copy/paste error of
    -- pointing a student at a completely different grade twice.
    CONSTRAINT chk_ssts_students_grade_distinct CHECK (
        admitted_grade_id IS NULL OR current_grade_id IS NULL OR admitted_grade_id <> current_grade_id
    )
);

COMMENT ON TABLE ssts_students IS
    'Enrollment records. No has_disciplinary_action flag: that count is derived '
    'from ssts_disciplinary_actions and a stored copy would drift.';

CREATE INDEX IF NOT EXISTS idx_ssts_students_current_grade  ON ssts_students(current_grade_id);
CREATE INDEX IF NOT EXISTS idx_ssts_students_admitted_grade ON ssts_students(admitted_grade_id);
CREATE INDEX IF NOT EXISTS idx_ssts_students_family         ON ssts_students(family_id);
CREATE INDEX IF NOT EXISTS idx_ssts_students_doctor         ON ssts_students(doctor_id);
CREATE INDEX IF NOT EXISTS idx_ssts_students_active         ON ssts_students(is_active);
-- Roster query: active students in a grade, by surname.
CREATE INDEX IF NOT EXISTS idx_ssts_students_grade_name
    ON ssts_students(current_grade_id, last_name);


-- ------------------------------------------------------------------
-- Disciplinary actions -- per-student incident log.
--
-- APPEND-ONLY: no is_active, deliberately. An incident record is history and
-- must stay queryable for audit; it is resolved with is_closed (a workflow
-- state) and never deleted while it matters. That is why ssts_students no
-- longer stores a has_disciplinary_action boolean -- it duplicated the
-- existence of these rows and disagreed with them.
--
-- CASCADE here is correct: an incident with no student is meaningless, and this
-- is the one direction where cascading away log rows on parent delete is right.
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ssts_disciplinary_actions (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    student_id bigint NOT NULL,
    type character varying(60) NOT NULL,
    remarks character varying(500) NOT NULL,
    is_closed boolean NOT NULL DEFAULT false,
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ssts_disciplinary_actions_student FOREIGN KEY (student_id)
        REFERENCES ssts_students (id) ON DELETE CASCADE,
    -- `type` is an open vocabulary the school extends, so it is constrained to
    -- be a real value rather than pinned to a fixed list that would reject
    -- legitimate new categories.
    CONSTRAINT chk_ssts_disciplinary_actions_type CHECK (btrim(type) <> '')
);

COMMENT ON TABLE ssts_disciplinary_actions IS
    'Per-student incident log. Append-only; is_closed is a workflow state, not '
    'a soft-delete flag.';

CREATE INDEX IF NOT EXISTS idx_ssts_disciplinary_actions_student ON ssts_disciplinary_actions(student_id);
CREATE INDEX IF NOT EXISTS idx_ssts_disciplinary_actions_closed  ON ssts_disciplinary_actions(is_closed);
-- Serves "open incidents for this student", the only query the table really gets.
CREATE INDEX IF NOT EXISTS idx_ssts_disciplinary_actions_student_closed
    ON ssts_disciplinary_actions(student_id, is_closed);

-- ============================================================================
-- DONORS  (added 2026-09-30)
-- ============================================================================
-- A donor is a person or business the school thanks. It is NOT a financial
-- record: the money lives in ssts_donations, one row per gift, so one donor can
-- have many gifts over many years without any history being rewritten.
--
-- Deliberately NOT related to ssts_users. A donor is an external party with no
-- portal login, and making them a user row would put a PII-bearing login they
-- never asked for on the accounts table. Donors are also sometimes anonymous.
--
-- `is_active` and `is_public` are SEPARATE and both load-bearing:
--   is_active  = is this still a donor we work with? (hides from admin pickers)
--   is_public  = has this donor consented to appear by NAME on the public home
--                page carousel? Default false, because publishing a donor's
--                name without consent is a real-world problem, not a UI detail.
-- Collapsing these two into one flag would either expose a private donor or
-- hide a willing one from the carousel.
--
-- There is deliberately NO total-giving column here. It is derivable by summing
-- ssts_donations, and a stored total can only ever be stale. See the
-- no-stored-aggregates rule in the schema header.
-- ============================================================================
CREATE TABLE IF NOT EXISTS ssts_donors (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    -- Person or business name as it should be shown. Not split into
    -- first/last: donors are frequently businesses or families.
    name character varying(255) NOT NULL,
    -- Short descriptor under the name, e.g. "Technology Services".
    tagline character varying(255),
    -- Optional external link. https-only: the public page renders it as an
    -- anchor, and allowing arbitrary schemes here would make stored XSS easy.
    website_url character varying(500),
    -- Relative /images/... path or an https:// bucket URL, exactly the same two
    -- forms the gallery accepts (see chk_ssts_gallery_events_image_url).
    photo_path character varying(500),
    -- Manual ordering for the carousel; lower sorts first.
    display_order integer NOT NULL DEFAULT 0,
    is_active boolean NOT NULL DEFAULT true,
    is_public boolean NOT NULL DEFAULT false,
    notes text,
    -- Marks rows created by sql/data.sql so the seed can delete exactly its own
    -- rows. NULL for anything made through /superadmin/donors. Titles/names are
    -- user-supplied and collide, so seeding never keys on them.
    seed_key character varying(64),
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_ssts_donors_name CHECK (btrim(name) <> ''),
    CONSTRAINT chk_ssts_donors_display_order CHECK (display_order >= 0),
    -- Blank OR https:// only. No http, no javascript:, no data:, no
    -- scheme-relative //evil.com -- the last would otherwise sail past a naive
    -- "starts with http" test and become an off-site link on our homepage.
    CONSTRAINT chk_ssts_donors_website CHECK (
        website_url IS NULL
        OR btrim(website_url) = ''
        OR website_url ~ '^https://[A-Za-z0-9.-]+(/[A-Za-z0-9._/-]+)*$'
    ),
    -- Same two accepted forms as the gallery image path.
    CONSTRAINT chk_ssts_donors_photo CHECK (
        photo_path IS NULL
        OR btrim(photo_path) = ''
        OR photo_path ~ '^/images/[A-Za-z0-9._/-]+$'
        OR photo_path ~ '^https://[A-Za-z0-9.-]+/[A-Za-z0-9._/-]+$'
    )
);

COMMENT ON TABLE ssts_donors IS
    'Donors thanked by the school. No totals are stored here; giving history '
    'lives in ssts_donations. is_public is consent to appear by name on the '
    'public site, and is independent of is_active.';

-- Serves the public carousel: public + active, curated order first.
CREATE INDEX IF NOT EXISTS idx_ssts_donors_public ON ssts_donors (is_public, is_active, display_order);
CREATE INDEX IF NOT EXISTS idx_ssts_donors_active  ON ssts_donors (is_active, display_order);

-- Donor names are unique CASE-INSENSITIVELY. Two rows for "Acme Corp" and
-- "acme corp" would double-count in every giving total and put the same donor
-- on the public carousel twice, so uniqueness is enforced in the database
-- rather than only by the admin form. A plain UNIQUE(name) would not catch the
-- different-case case, hence the functional index on lower(name).
CREATE UNIQUE INDEX IF NOT EXISTS uq_ssts_donors_name_lower ON ssts_donors (lower(name));

-- ============================================================================
-- DONATIONS  (added 2026-09-30)
-- ============================================================================
-- One row per gift received. This is the append-only financial ledger; there is
-- no total column because a total is derivable and a stored one goes stale.
--
-- MONEY IS numeric(12,2), NOT a float. The entity maps it to BigDecimal. A
-- float cannot represent 0.10 or 19.99 exactly, so summing float amounts
-- produces totals that are off by cents and do not reconcile against a bank
-- statement. Do not "simplify" this to double or float.
--
-- scale 2 is the CHECK, not just the type: it rejects a third decimal place
-- that would otherwise be silently rounded on the way in.
--
-- The donor FK is ON DELETE RESTRICT, not CASCADE. Deleting a donor that has
-- given money would destroy the financial record, which is exactly the kind of
-- mistake a school should not be able to make by clicking Delete. The admin
-- screen refuses such a delete in Java as well, but the database is the
-- backstop.
-- ============================================================================
CREATE TABLE IF NOT EXISTS ssts_donations (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    donor_id bigint NOT NULL,
    amount numeric(12,2) NOT NULL,
    donated_on date NOT NULL,
    -- cash / check / online / in_kind. An open vocabulary the school extends,
    -- constrained only to be a real value -- a CHECK pinning it to a fixed list
    -- would reject a legitimate new method, exactly as with
    -- chk_ssts_disciplinary_actions_type.
    method character varying(40),
    -- Check number, transaction id, or similar free text.
    reference_no character varying(120),
    notes text,
    seed_key character varying(64),
    created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ssts_donations_donor FOREIGN KEY (donor_id)
        REFERENCES ssts_donors (id) ON DELETE RESTRICT,
    -- A gift of zero or less is a data-entry mistake, not a donation. > 0 also
    -- keeps the value out of the "sum of everything" edge case.
    CONSTRAINT chk_ssts_donations_amount_positive CHECK (amount > 0),
    -- Guards against an entry that carries cents that would be rounded away.
    CONSTRAINT chk_ssts_donations_amount_scale CHECK (amount = round(amount, 2)),
    CONSTRAINT chk_ssts_donations_method CHECK (method IS NULL OR btrim(method) <> '')
);

COMMENT ON TABLE ssts_donations IS
    'Append-only gift ledger. amount is numeric(12,2)/BigDecimal on purpose. '
    'Donor delete is RESTRICT so giving history cannot be destroyed.';

-- Postgres only indexes the referenced side, so this FK column needs its own.
CREATE INDEX IF NOT EXISTS idx_ssts_donations_donor ON ssts_donations (donor_id);
-- The reports and the donations list both ask for "newest first" and often
-- "this financial year", which is a date range.
CREATE INDEX IF NOT EXISTS idx_ssts_donations_donated_on ON ssts_donations (donated_on DESC);
