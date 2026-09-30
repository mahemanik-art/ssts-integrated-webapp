-- ============================================================================
-- SSTS SEED DATA -- the ONLY data script. Paired with sql/schema.sql.
-- ============================================================================
-- Load order:  schema.sql  THEN  data.sql. Both are idempotent, so either can be
-- re-applied to an existing database without duplicating rows.
--
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f sql/schema.sql
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f sql/data.sql
--
-- ON_ERROR_STOP=1 MATTERS: without it psql reports a failing statement and still
-- exits 0, so a half-applied seed looks like a clean run.
--
-- This file absorbed sql/staff_profiles_seed.sql (the per-person staff data) and
-- sql/grant_super_admin.sql (the super-admin role grant, which is simply the
-- ssts_admin INSERT below pointing at the super_admin role). There are no other
-- SQL scripts: schema.sql is declarative and data.sql is the seed.
--
-- PASSWORDS ARE PLACEHOLDERS, NOT CREDENTIALS.
--   Every seeded account shares one 38-character bcrypt PLACEHOLDER that is not
--   a valid BCryptPasswordEncoder value, so NO seeded account can actually log
--   in. That is deliberate: a committed real hash is a committed password.
--   To make an account log in, set a real hash yourself:
--     htpasswd -bnBC 10 "" 'YourPassword' | tr -d ':\n'
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
--       -c "UPDATE ssts_users SET password_hash = '<paste hash>' WHERE username = 'ssts_admin';"
--   The super_admin ROLE needs no script: it is granted by the ssts_admin INSERT
--   below, and SstsUser.getAuthorities() derives ROLE_SUPER_ADMIN from the role
--   name, so renaming or ungranting that row is what changes access.
-- ============================================================================

-- ============================================================
-- ROLES
-- ============================================================

-- Super Admin - full access to everything
INSERT INTO ssts_roles (name, description, is_active, can_create_users, can_edit_users, can_delete_users, can_view_users, can_manage_content, can_edit_pages, can_publish_content, can_manage_levels, can_manage_classes, can_manage_donors, can_view_donors, can_manage_events, can_view_calendar, can_manage_volunteers, can_view_financials, can_view_reports, can_manage_settings, can_view_audit_logs, can_send_announcements, can_send_newsletters, can_view_dashboard)
SELECT 'super_admin', 'Full system access - all privileges', true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true
WHERE NOT EXISTS (SELECT 1 FROM ssts_roles r WHERE r.name = 'super_admin');

-- Admin - most privileges except user deletion
INSERT INTO ssts_roles (name, description, is_active, can_create_users, can_edit_users, can_delete_users, can_view_users, can_manage_content, can_edit_pages, can_publish_content, can_manage_levels, can_manage_classes, can_manage_donors, can_view_donors, can_manage_events, can_view_calendar, can_manage_volunteers, can_view_financials, can_view_reports, can_manage_settings, can_view_audit_logs, can_send_announcements, can_send_newsletters, can_view_dashboard)
SELECT 'admin', 'Administrative access - can manage content, users, and view financials', true, true, true, false, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true
WHERE NOT EXISTS (SELECT 1 FROM ssts_roles r WHERE r.name = 'admin');

-- Editor - content management focused
INSERT INTO ssts_roles (name, description, is_active, can_create_users, can_edit_users, can_delete_users, can_view_users, can_manage_content, can_edit_pages, can_publish_content, can_manage_levels, can_manage_classes, can_manage_donors, can_view_donors, can_manage_events, can_view_calendar, can_manage_volunteers, can_view_financials, can_view_reports, can_manage_settings, can_view_audit_logs, can_send_announcements, can_send_newsletters, can_view_dashboard)
SELECT 'editor', 'Content editor - manages pages, events, and announcements', true, false, false, false, false, true, true, true, false, false, false, true, true, true, false, false, false, false, false, true, true, true
WHERE NOT EXISTS (SELECT 1 FROM ssts_roles r WHERE r.name = 'editor');

-- Teacher - class and volunteer management
INSERT INTO ssts_roles (name, description, is_active, can_create_users, can_edit_users, can_delete_users, can_view_users, can_manage_content, can_edit_pages, can_publish_content, can_manage_levels, can_manage_classes, can_manage_donors, can_view_donors, can_manage_events, can_view_calendar, can_manage_volunteers, can_view_financials, can_view_reports, can_manage_settings, can_view_audit_logs, can_send_announcements, can_send_newsletters, can_view_dashboard)
SELECT 'teacher', 'Teacher access - manages classes, volunteers, and calendar', true, false, false, false, false, false, false, false, false, true, false, false, false, true, true, false, false, false, false, false, false, true
WHERE NOT EXISTS (SELECT 1 FROM ssts_roles r WHERE r.name = 'teacher');

-- Volunteer Coordinator - volunteer and event focused
INSERT INTO ssts_roles (name, description, is_active, can_create_users, can_edit_users, can_delete_users, can_view_users, can_manage_content, can_edit_pages, can_publish_content, can_manage_levels, can_manage_classes, can_manage_donors, can_view_donors, can_manage_events, can_view_calendar, can_manage_volunteers, can_view_financials, can_view_reports, can_manage_settings, can_view_audit_logs, can_send_announcements, can_send_newsletters, can_view_dashboard)
SELECT 'volunteer_coordinator', 'Volunteer and event management', true, false, false, false, false, false, false, false, false, false, false, false, true, true, true, false, false, false, false, false, false, true
WHERE NOT EXISTS (SELECT 1 FROM ssts_roles r WHERE r.name = 'volunteer_coordinator');

-- Read Only - view-only access
INSERT INTO ssts_roles (name, description, is_active, can_create_users, can_edit_users, can_delete_users, can_view_users, can_manage_content, can_edit_pages, can_publish_content, can_manage_levels, can_manage_classes, can_manage_donors, can_view_donors, can_manage_events, can_view_calendar, can_manage_volunteers, can_view_financials, can_view_reports, can_manage_settings, can_view_audit_logs, can_send_announcements, can_send_newsletters, can_view_dashboard)
SELECT 'read_only', 'View-only access to dashboard and public content', true, false, false, false, false, false, false, false, false, false, false, true, false, true, false, false, false, false, false, false, false, true
WHERE NOT EXISTS (SELECT 1 FROM ssts_roles r WHERE r.name = 'read_only');

-- ============================================================
-- SAMPLE USERS
-- ============================================================
-- One guarded INSERT per account: WHERE NOT EXISTS on username, so
-- re-running data.sql is a no-op instead of a unique-constraint error.
-- The password_hash is the PLACEHOLDER -- see the header note at the top
-- of this file. No seeded account can log in until you set a real hash.

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, is_active, email_verified, receive_newsletter, receive_volunteer_updates, created_at, updated_at)
SELECT 'ssts_admin', 'admin@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'SSTS Administrator',
       (SELECT id FROM ssts_roles WHERE name = 'super_admin'), 'admin', 'Super Admin',
       true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ssts_users x WHERE x.username = 'ssts_admin');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, is_active, email_verified, receive_newsletter, receive_volunteer_updates, created_at, updated_at)
SELECT 'school_admin', 'schooladmin@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'School Admin',
       (SELECT id FROM ssts_roles WHERE name = 'admin'), 'admin', 'School Administrator',
       true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ssts_users x WHERE x.username = 'school_admin');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, is_active, email_verified, receive_newsletter, receive_volunteer_updates, created_at, updated_at)
SELECT 'editor_user', 'editor@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'Content Editor',
       (SELECT id FROM ssts_roles WHERE name = 'editor'), 'staff', 'Editor',
       true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ssts_users x WHERE x.username = 'editor_user');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, is_active, email_verified, receive_newsletter, receive_volunteer_updates, created_at, updated_at)
SELECT 'teacher_user', 'teacher@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'Class Teacher',
       (SELECT id FROM ssts_roles WHERE name = 'teacher'), 'staff', 'Class Teacher',
       true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ssts_users x WHERE x.username = 'teacher_user');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, is_active, email_verified, receive_newsletter, receive_volunteer_updates, created_at, updated_at)
SELECT 'volunteer_lead', 'volunteer@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'Volunteer Coordinator',
       (SELECT id FROM ssts_roles WHERE name = 'volunteer_coordinator'), 'staff', 'Volunteer Coordinator',
       true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ssts_users x WHERE x.username = 'volunteer_lead');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, is_active, email_verified, receive_newsletter, receive_volunteer_updates, created_at, updated_at)
SELECT 'parent1', 'parent1@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'Parent One',
       (SELECT id FROM ssts_roles WHERE name = 'read_only'), 'parent', NULL,
       true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ssts_users x WHERE x.username = 'parent1');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, is_active, email_verified, receive_newsletter, receive_volunteer_updates, created_at, updated_at)
SELECT 'parent2', 'parent2@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'Parent Two',
       (SELECT id FROM ssts_roles WHERE name = 'read_only'), 'parent', NULL,
       true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ssts_users x WHERE x.username = 'parent2');

-- BEGIN calendar seed (2026-2027) -- from the MTS academic calendar page
-- (https://mariettatamilschool.com/mts-academic-calendar/). Backs /calendar.
-- Idempotent: re-running replaces this academic year's events. is_active is
-- set explicitly (the live table has no column default). Aug-Jul cycle.
-- ============================================================================
DELETE FROM ssts_calendar_events WHERE academic_year = '2026-2027';

-- Working days: classes, tests, terms, celebrations
INSERT INTO ssts_calendar_events (title, event_date, end_date, event_type, academic_year, description, is_active) VALUES
    ('First Term Begins',                   '2026-08-07', NULL, 'working', '2026-2027', 'Class 1', true),
    ('Class 2',                             '2026-08-21', NULL, 'working', '2026-2027', NULL, true),
    ('Class 3',                             '2026-08-28', NULL, 'working', '2026-2027', NULL, true),
    ('Test 1: Project',                     '2026-09-04', NULL, 'working', '2026-2027', 'Class 4', true),
    ('Class 5',                             '2026-09-11', NULL, 'working', '2026-2027', NULL, true),
    ('Class 6',                             '2026-09-18', NULL, 'working', '2026-2027', NULL, true),
    ('PT Conference Month',                 '2026-10-02', NULL, 'working', '2026-2027', 'Class 7', true),
    ('Test 2: Cover Classes 1-7',           '2026-10-09', NULL, 'working', '2026-2027', 'Class 8', true),
    ('Class 9',                             '2026-10-16', NULL, 'working', '2026-2027', NULL, true),
    ('Class 10',                            '2026-10-23', NULL, 'working', '2026-2027', NULL, true),
    ('Class 11',                            '2026-10-30', NULL, 'working', '2026-2027', NULL, true),
    ('Test 3: Term I Ends',                 '2026-11-06', NULL, 'working', '2026-2027', 'Covers Classes 8-11, Report Due', true),
    ('Second Term Begins',                  '2026-11-13', NULL, 'working', '2026-2027', 'Class 13', true),
    ('Class 14',                            '2026-11-20', NULL, 'working', '2026-2027', NULL, true),
    ('Class 15',                            '2026-12-04', NULL, 'working', '2026-2027', NULL, true),
    ('Test 4: Project',                     '2026-12-11', NULL, 'working', '2026-2027', 'Class 16', true),
    ('Class 17',                            '2026-12-18', NULL, 'working', '2026-2027', NULL, true),
    ('Class 18',                            '2027-01-08', NULL, 'working', '2026-2027', NULL, true),
    ('Virtual Learning',                    '2027-01-22', NULL, 'working', '2026-2027', 'Class 19', true),
    ('Test 5: Term II Ends',                '2027-01-29', NULL, 'working', '2026-2027', 'Covers Classes 13-19, Report Due', true),
    ('Pongal Day Celebration',              '2027-01-30', NULL, 'working', '2026-2027', NULL, true),
    ('Third Term Begins',                   '2027-02-05', NULL, 'working', '2026-2027', 'Class 21', true),
    ('Class 22',                            '2027-02-12', NULL, 'working', '2026-2027', NULL, true),
    ('Class 23',                            '2027-02-26', NULL, 'working', '2026-2027', NULL, true),
    ('Test 6: Cover Classes 20-23',         '2027-03-05', NULL, 'working', '2026-2027', 'Class 24', true),
    ('Class 25',                            '2027-03-12', NULL, 'working', '2026-2027', NULL, true),
    ('Class 26',                            '2027-03-19', NULL, 'working', '2026-2027', NULL, true),
    ('Class 27',                            '2027-03-26', NULL, 'working', '2026-2027', NULL, true),
    ('Test 7: Cover Classes 24-27',         '2027-04-02', NULL, 'working', '2026-2027', 'Class 28', true),
    ('Class 29',                            '2027-04-16', NULL, 'working', '2026-2027', NULL, true),
    ('Class 30',                            '2027-04-23', NULL, 'working', '2026-2027', NULL, true),
    ('Annual Day Celebration',              '2027-04-24', NULL, 'working', '2026-2027', NULL, true),
    ('Test 8: Final Exam - Term III Ends',   '2027-05-07', NULL, 'working', '2026-2027', 'All Portions, Final Report Due', true);

-- Holidays / breaks (no class)
INSERT INTO ssts_calendar_events (title, event_date, end_date, event_type, academic_year, description, is_active) VALUES
    ('School Holiday',   '2026-08-14', NULL,          'holiday', '2026-2027', 'No Class', true),
    ('Week Off',         '2026-09-21', '2026-09-25', 'holiday', '2026-2027', 'No Class', true),
    ('Week Off',         '2026-11-23', '2026-11-27', 'holiday', '2026-2027', 'No Class', true),
    ('Holiday Break',    '2026-12-21', '2027-01-01', 'holiday', '2026-2027', 'No Class', true),
    ('Pongal Holiday',   '2027-01-15', NULL,          'holiday', '2026-2027', 'No Class', true),
    ('Winter Break',     '2027-02-15', '2027-02-19', 'holiday', '2026-2027', 'No Class', true),
    ('Spring Break',     '2027-04-05', '2027-04-09', 'holiday', '2026-2027', 'No Class', true);
-- END calendar seed

-- ============================================================================
  -- BEGIN gallery seed
  -- Ported from the record list that used to be hardcoded in
  -- PageController.gallery(). Only 'Pongal Celebration' has real photo files,
  -- committed under static/images/gallery/pongal-celebration-1/ and served at
  -- /images/gallery/...; the folder name is the storage convention
  -- GalleryImageStorageService uses for event 1 (<title-slug>-<id>). The other
  -- six entries have no photos, so image_url is NULL and /gallery falls back to
  -- the title's first letter on a gradient tile.
  --
  -- The four files are the only distinct images in that folder; the folder once
  -- held 15 names, 11 of which were byte-identical duplicates.
  --
  -- Idempotent: replaces ONLY seeded rows, via seed_key. An earlier version
  -- deleted by `title`, which silently destroyed admin-created content on a
  -- re-run -- titles are user-supplied and collide. is_active/display_order are
  -- set explicitly because the live table has no column defaults.
  -- ============================================================================
  DELETE FROM ssts_gallery_events WHERE seed_key = 'ssts-gallery-2026';
  INSERT INTO ssts_gallery_events (title, event_date, image_url, image_urls, description, display_order, is_active, seed_key, created_at, updated_at) VALUES
      ('Pongal Celebration',         '2026-01-15',
       '/images/gallery/pongal-celebration-1/1.png',
       E'/images/gallery/pongal-celebration-1/1.png\n/images/gallery/pongal-celebration-1/2.png\n/images/gallery/pongal-celebration-1/3.jpg\n/images/gallery/pongal-celebration-1/4.jpg',
       'Harvest festival with pongal making, kolam art, and traditional songs from our students.', 10, true, 'ssts-gallery-2026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
      ('Deepavali Lights',           '2025-11-01', NULL, NULL, 'Festival of lights — diyas, rangoli, and a dazzling performance by our junior batch.',       20, true, 'ssts-gallery-2026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
      ('Annual Day',                 '2025-05-10', NULL, NULL, 'Bharatanatyam, group songs, and skits performed by every level on the big stage.',    30, true, 'ssts-gallery-2026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
      ('Tamil New Year',             '2025-04-14', NULL, NULL, 'Puthandu Vazthukal! Students presented classic poetry and a village fair-style exhibit.', 40, true, 'ssts-gallery-2026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
      ('First Day of School',        '2025-08-01', NULL, NULL, 'New faces, old friendships, and a fresh academic year of Tamil learning.',          50, true, 'ssts-gallery-2026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
      ('Volunteer Appreciation',     '2025-06-14', NULL, NULL, 'A heartfelt thank-you evening for the families and volunteers who keep SSTS running.', 60, true, 'ssts-gallery-2026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
      ('Storytelling Workshop',      '2025-10-04', NULL, NULL, 'Ramayana and Tenali Raman stories brought to life with puppets and student narration.', 70, true, 'ssts-gallery-2026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
  -- END gallery seed

-- ============================================================================
-- BEGIN announcements seed
-- Backs the "School Announcements" marquee on the public home page.
-- Idempotent: replaces ONLY seeded rows, via seed_key (see the gallery seed for
-- why title is not used).
--
-- expires_on is what makes old notices drop off automatically: NULL = never
-- expires, otherwise the row stops rendering the day after that date.
-- is_active/announce_date are set explicitly (the live table has no defaults).
-- ============================================================================
DELETE FROM ssts_announcements WHERE seed_key = 'ssts-announcements-2026';

INSERT INTO ssts_announcements (title, message, announce_date, expires_on, is_active, seed_key, created_at, updated_at) VALUES
    -- No expiry: stays up until withdrawn.
    ('Registration Open',  'Registration for the 2026-27 school year is now open. Please contact the front desk to enrol.', DATE '2026-08-01', NULL,                true, 'ssts-announcements-2026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Already expired: proves old notices drop off the marquee on their own.
    ('Pongal Celebration', 'Pongal celebration and student performances in the school hall.',                       DATE '2026-01-02', DATE '2026-01-20', true, 'ssts-announcements-2026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Volunteer Sign-ups', 'Volunteer sign-ups are open at the front desk on Friday evenings.',                      DATE '2026-09-01', NULL,                true, 'ssts-announcements-2026', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
-- END announcements seed


-- ============================================================================
-- STAFF PROFILES  (merged from the former sql/staff_profiles_seed.sql)
-- ============================================================================
-- Variety of staff users: 3 existing seed accounts get realistic names and
-- joining dates, and 7 more cover the roles the school actually needs
-- (vice principal, four grade-level teachers, art, music, school counselor).
-- Per-person columns (bio, avatar_url, occupation, phone, address) are written
-- DIRECTLY to ssts_users -- there is no profile table.
-- Avatars reference the portraits in src/main/resources/static/images/teachers/
--
-- IDEMPOTENT: the INSERTs are guarded with NOT EXISTS and the updates key on
-- username, so re-running data.sql is safe and will not duplicate anyone.
--
-- The password_hash below is the same shared dev hash for every seeded account
-- (see the header note at the top of this file). It is NOT a real credential --
-- replace it before any public deployment.
-- ============================================================================

-- ============================================================
-- 1) STAFF USERS
--    - 3 existing staff get realistic names/designations/joining dates
--    - 7 new staff users covering different roles and designations
-- ============================================================

UPDATE ssts_users SET full_name = 'Meera Krishnan', joining_date = DATE '2019-08-12', updated_at = CURRENT_TIMESTAMP WHERE username = 'editor_user';
UPDATE ssts_users SET full_name = 'Suresh Menon', joining_date = DATE '2016-08-15', updated_at = CURRENT_TIMESTAMP WHERE username = 'teacher_user';
UPDATE ssts_users SET full_name = 'Lakshmi Venkat', joining_date = DATE '2021-01-18', updated_at = CURRENT_TIMESTAMP WHERE username = 'volunteer_lead';

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'vice_principal', 'vp@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Dr. Arvind Kumar', r.id, 'admin', 'Vice Principal', DATE '2018-06-04', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'admin'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'vice_principal');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'nila_2_teacher', 'math@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Priya Anand', r.id, 'staff', 'Nila 2 Teacher', DATE '2020-08-10', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'nila_2_teacher');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'nila_1_teacher', 'science@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Dr. Raghavan Suresh', r.id, 'staff', 'Nila 1 Teacher', DATE '2017-08-07', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'nila_1_teacher');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'nilai_5_teacher', 'tamil@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Kamala Balamurali', r.id, 'staff', 'Nila 5 Teacher', DATE '2015-08-17', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'nilai_5_teacher');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'munmazhalai_teacher', 'art@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Nandini Saran', r.id, 'staff', 'Munmazhalai Teacher', DATE '2022-08-08', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'munmazhalai_teacher');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'nilai_3_teacher', 'music@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Shalini Pillai', r.id, 'staff', 'Nila 3 Teacher', DATE '2023-01-09', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'nilai_3_teacher');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'nilai_4_teacher', 'counselor@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Ananya Sivakumar', r.id, 'staff', 'Nilai 4 Counselor', DATE '2024-02-05', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'read_only'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'nilai_4_teacher');

-- ============================================================
-- 2) PROFILE DETAIL (bio/avatar/address/credentials, one row per staff user)
-- ============================================================

UPDATE ssts_users SET
    phone = '404-555-0101', address_line1 = '4820 River Ridge Dr', city = 'Sandy Springs',
    state = 'GA', zip_code = '30328', country = 'USA',
    bio = 'Meera keeps the school website and newsletters running. She has been with SSTS for over five years and loves bringing stories from our classrooms to the wider community.',
    avatar_url = '/images/teachers/teacher-01.jpg', occupation = 'Content Editor',
    employer = 'Sandy Springs Tamil School', years_in_community = 9,
    prior_education = 'M.A. in Mass Communication, Annamalai University',
    prior_tamil_experience = 'Grew up reading Tamil periodicals at home; edits the SSTS bilingual newsletter',
    prior_teaching_experience = '8 years editing school publications and online content',
    prior_volunteer_experience = 'Volunteered with the SSTS library drive for 3 years',
    certifications = 'Google Certified Educator Level 1',
    interests = 'Blogging, Carnatic vocal music, gardening',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'editor_user';
UPDATE ssts_users SET
    phone = '770-555-0102', address_line1 = '1150 Dunwoody Crossing', city = 'Dunwoody',
    state = 'GA', zip_code = '30338', country = 'USA',
    bio = 'Suresh has taught Sunday school for more than a decade and mentors new class teachers. He believes every child learns best when lessons feel like a conversation.',
    avatar_url = '/images/teachers/teacher-05.jpg', occupation = 'Class Teacher',
    employer = 'Sandy Springs Tamil School', years_in_community = 14,
    prior_education = 'B.Tech, Anna University',
    prior_tamil_experience = 'Tamil Saturday school alumnus; teaches Tamil folk songs in class',
    prior_teaching_experience = '12 years of Tamil and culture class instruction',
    prior_volunteer_experience = 'Coach for the SSTS quiz team since 2019',
    certifications = 'Georgia Educator Certification (Provisional)',
    interests = 'Cricket, storytelling, public speaking',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'teacher_user';
UPDATE ssts_users SET
    phone = '404-555-0103', address_line1 = '275 Johnston Ferry Rd', city = 'Atlanta',
    state = 'GA', zip_code = '30339', country = 'USA',
    bio = 'Lakshmi coordinates volunteers for every major school event, from Pongal celebrations to graduation. If you have an hour to give, she will find the perfect spot for it.',
    avatar_url = '/images/teachers/teacher-06.jpg', occupation = 'Volunteer Coordinator',
    employer = 'Sandy Springs Tamil School', years_in_community = 7,
    prior_education = 'B.S. in Hospitality Management, Osmania University',
    prior_tamil_experience = 'Organizes Tamil New Year and Pongal community events',
    prior_teaching_experience = '6 years coordinating after-school enrichment programs',
    prior_volunteer_experience = '10+ years volunteering with Tamil Sangam and temple youth groups',
    certifications = 'Certified Event Planner (CMP)',
    interests = 'Event planning, henna art, hiking',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'volunteer_lead';
UPDATE ssts_users SET
    phone = '770-555-0104', address_line1 = '89 Ashford Grove Ln', city = 'Alpharetta',
    state = 'GA', zip_code = '30004', country = 'USA',
    bio = 'Dr. Kulkarni leads the schools academic program and accreditation efforts. He mentors teachers across all levels and keeps the curriculum aligned with state standards.',
    avatar_url = '/images/teachers/teacher-03.jpg', occupation = 'Vice Principal',
    employer = 'Sandy Springs Tamil School', years_in_community = 16,
    prior_education = 'Ph.D. in Education, University of Madras',
    prior_tamil_experience = 'Raised in Chennai; oversees Tamil language curriculum standards',
    prior_teaching_experience = '20 years in education, 9 years in school leadership',
    prior_volunteer_experience = 'Board member of the Atlanta Tamil Association',
    certifications = 'Georgia Leadership Certification, Ed.D.',
    interests = 'Policy debate, chess, classical music',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'vice_principal';
UPDATE ssts_users SET
    phone = '404-555-0105', address_line1 = '6405 Windward Parkway', city = 'Johns Creek',
    state = 'GA', zip_code = '30022', country = 'USA',
    bio = 'Priya makes algebra approachable with games, puzzles, and plenty of patience. Her students consistently score among the highest on statewide math assessments.',
    avatar_url = '/images/teachers/teacher-04.jpg', occupation = 'Mathematics Teacher',
    employer = 'Sandy Springs Tamil School', years_in_community = 6,
    prior_education = 'M.Sc. in Mathematics, Presidency College',
    prior_tamil_experience = 'Tutoring Tamil-speaking students in math since college',
    prior_teaching_experience = '7 years teaching middle and high school math',
    prior_volunteer_experience = 'Math night volunteer for three school years',
    certifications = 'State Mathematics Certification (6-12)',
    interests = 'Sudoku, baking, classical dance',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'nila_2_teacher';
UPDATE ssts_users SET
    phone = '770-555-0106', address_line1 = '3105 Lake Forrest Ct', city = 'Roswell',
    state = 'GA', zip_code = '30075', country = 'USA',
    bio = 'Dr. Iyer runs the science fair and loves turning everyday questions into experiments. His classes regularly advance students to regional science competitions.',
    avatar_url = '/images/teachers/teacher-07.jpg', occupation = 'Science Teacher',
    employer = 'Sandy Springs Tamil School', years_in_community = 11,
    prior_education = 'Ph.D. in Chemistry, IIT Madras',
    prior_tamil_experience = 'Brings Tamil science terminology into lessons for heritage learners',
    prior_teaching_experience = '15 years teaching chemistry and physics',
    prior_volunteer_experience = 'Judges regional science olympiads across Georgia',
    certifications = 'AP Chemistry Certified, Georgia Science Certification',
    interests = 'Astronomy, gardening, cricket',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'nila_1_teacher';
UPDATE ssts_users SET
    phone = '404-555-0107', address_line1 = '1425 North Point Pkwy', city = 'Alpharetta',
    state = 'GA', zip_code = '30004', country = 'USA',
    bio = 'Kamala has taught Tamil for more than twenty years and trains new Tamil teachers at SSTS. She keeps the language alive through songs, drama, and conversation in her classroom.',
    avatar_url = '/images/teachers/teacher-08.jpg', occupation = 'Tamil Language Teacher',
    employer = 'Sandy Springs Tamil School', years_in_community = 21,
    prior_education = 'M.A. in Tamil Literature, Madurai Kamaraj University',
    prior_tamil_experience = 'Native Tamil speaker; authored the SSTS Grade 3-5 Tamil workbook',
    prior_teaching_experience = '22 years teaching Tamil as a heritage language',
    prior_volunteer_experience = 'Founder of the SSTS summer Tamil camp',
    certifications = 'Tamil Nadu Teacher Eligibility Test (TNTET)',
    interests = 'Poetry, Bharatanatyam, writing',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'nilai_5_teacher';
UPDATE ssts_users SET
    phone = '770-555-0108', address_line1 = '5010 Sandy Plains Rd', city = 'Marietta',
    state = 'GA', zip_code = '30060', country = 'USA',
    bio = 'Nandinis art room is where colours, kolam patterns, and creativity meet. She guides students through painting, craft, and traditional South Indian art forms.',
    avatar_url = '/images/teachers/teacher-09.jpg', occupation = 'Art & Craft Teacher',
    employer = 'Sandy Springs Tamil School', years_in_community = 4,
    prior_education = 'B.F.A. in Painting, Kalakshetra Foundation',
    prior_tamil_experience = 'Teaches kolam, tanjore-style painting, and festival crafts',
    prior_teaching_experience = '4 years running weekend art workshops',
    prior_volunteer_experience = 'Volunteer set designer for the SSTS annual day',
    certifications = 'Portfolio Certificate in Fine Arts',
    interests = 'Painting, pottery, photography',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'munmazhalai_teacher';
UPDATE ssts_users SET
    phone = '404-555-0109', address_line1 = '2200 Powers Ferry Rd', city = 'Smyrna',
    state = 'GA', zip_code = '30080', country = 'USA',
    bio = 'Shalini introduces students to both Carnatic and Western music. She prepares small ensembles for school functions and cultural showcases throughout the year.',
    avatar_url = '/images/teachers/teacher-10.jpg', occupation = 'Music Teacher',
    employer = 'Sandy Springs Tamil School', years_in_community = 3,
    prior_education = 'B.Mus. in Carnatic Music, Sri Krishna Sangeetha Vidyalaya',
    prior_tamil_experience = 'Trained in Carnatic vocal; teaches Tamil devotional songs',
    prior_teaching_experience = '3 years of group music instruction',
    prior_volunteer_experience = 'Accompanies the SSTS choir for charity events',
    certifications = 'Grade 1 Voice Certification (Trinity College)',
    interests = 'Carnatic singing, violin, film soundtracks',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'nilai_3_teacher';
UPDATE ssts_users SET
    phone = '770-555-0110', address_line1 = '900 Mansell Rd', city = 'Roswell', state = 'GA',
    zip_code = '30076', country = 'USA',
    bio = 'Ananya supports students and families with study skills, transitions, and a listening ear. She also runs the peer mentoring program for older students.',
    avatar_url = '/images/teachers/teacher-02.jpg', occupation = 'School Counselor',
    employer = 'Sandy Springs Tamil School', years_in_community = 2,
    prior_education = 'M.S. in School Counseling, Georgia State University',
    prior_tamil_experience = 'Bilingual counseling available in Tamil and English',
    prior_teaching_experience = '2 years of school counseling practice',
    prior_volunteer_experience = 'Facilitates the student volunteer recognition program',
    certifications = 'Licensed Professional Counselor (LPC)',
    interests = 'Journaling, yoga, community theater',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'nilai_4_teacher';

-- ============================================================
;
-- 3) DEPARTMENT (was ssts_staff_details)
--
-- Only `department` survived that table: SSTS has no employees, so employee_id,
-- salary, employment_type and availability are gone, and job_title,
-- qualification and teaching_certification were duplicates of designation /
-- prior_education / certifications. Keyed on username, so idempotent.
-- ============================================================

UPDATE ssts_users SET
    department = 'Communications', updated_at = CURRENT_TIMESTAMP
WHERE username = 'editor_user';

UPDATE ssts_users SET
    department = 'Elementary - Grade 5', updated_at = CURRENT_TIMESTAMP
WHERE username = 'teacher_user';

UPDATE ssts_users SET
    department = 'Student Affairs', updated_at = CURRENT_TIMESTAMP
WHERE username = 'volunteer_lead';

UPDATE ssts_users SET
    department = 'Administration', updated_at = CURRENT_TIMESTAMP
WHERE username = 'vice_principal';

UPDATE ssts_users SET
    department = 'Middle School - Math', updated_at = CURRENT_TIMESTAMP
WHERE username = 'nila_2_teacher';

UPDATE ssts_users SET
    department = 'High School - Science', updated_at = CURRENT_TIMESTAMP
WHERE username = 'nila_1_teacher';

UPDATE ssts_users SET
    department = 'Language - Tamil', updated_at = CURRENT_TIMESTAMP
WHERE username = 'nilai_5_teacher';

UPDATE ssts_users SET
    department = 'Arts & Culture', updated_at = CURRENT_TIMESTAMP
WHERE username = 'munmazhalai_teacher';

UPDATE ssts_users SET
    department = 'Arts & Culture', updated_at = CURRENT_TIMESTAMP
WHERE username = 'nilai_3_teacher';

UPDATE ssts_users SET
    department = 'Student Support', updated_at = CURRENT_TIMESTAMP
WHERE username = 'nilai_4_teacher';

-- ============================================================================
-- DONORS (education donors shown on the public home page)
-- ============================================================================
-- The seven donors below are the ones whose logos are COMMITTED to the repo as
-- static/images/<Name>.jpg. They are seeded so the public donor carousel is
-- real, editable data instead of hardcoded markup -- index.html previously
-- listed three of them inline.
--
-- Idempotency note: this block deliberately does NOT use the DELETE-by-seed_key
-- pattern that the gallery and announcement seeds use. Those seed rows are
-- event records the school replaces wholesale; a donor row is CURATED state an
-- admin edits -- most importantly is_public, the consent flag that decides
-- whether a donor's name appears on the public site. A DELETE+INSERT re-seed
-- would silently reset that consent flag and re-publish a donor the school had
-- deliberately unlisted. So each row is inserted only when that donor name is
-- absent, which makes a re-run a no-op and preserves every admin edit.
--
-- website_url is carried over from the old hardcoded markup so each card keeps
-- its "Visit website" link, which is how this section displayed before the
-- donors became database rows. Every one of them is an example.com PLACEHOLDER
-- -- they are the URLs the page shipped with, not verified real sites. Replace
-- them with the sponsors' real addresses via /superadmin/donors.
-- chk_ssts_donors_website still applies: https-only, and a query string or a
-- scheme-relative //evil.com is refused.
--
-- Names, taglines and photo paths below are carried over VERBATIM from the two
-- hardcoded systems index.html used to have (a 3-card server-rendered block and
-- a 9-donor JS rotation), so turning the carousel into database rows did not
-- quietly discard content the school had already written. Two differences worth
-- knowing:
--   * "Altanta Mojo Productions" in the 3-card block was a typo; the filename
--     and the 9-donor list both said "Atlanta", so the correct spelling won.
--   * Meena Family and Northside Foods pointed at campus-placeholder.jpg, which
--     is a generic campus photo rather than a donor logo. Showing it as their
--     logo would misrepresent them, so their photo_path is NULL and the card
--     renders with no image. Upload or commit a real logo via
--     /superadmin/donors to fill it in.
--
-- NO donations are seeded. Fabricating dollar figures for a real school would
-- put invented money on the public site; the ledger starts empty and the school
-- records real gifts through /superadmin/donations.

INSERT INTO ssts_donors (name, tagline, photo_path, website_url, display_order, is_active, is_public, seed_key, created_at, updated_at)
SELECT v.name, v.tagline, v.photo_path, v.website_url, v.display_order, v.is_active, v.is_public, v.seed_key,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('JJJ Fortune LLC',          'Human resources consulting services', '/images/JJJFortuneLLC.jpg',          'https://jjjfortune.example.com',       1, true, true, 'ssts-donors-2026'),
    ('Madras Photo Studios',     'Capturing Life, Unscripted.',         '/images/MadrasPhotStudios.jpg',       'https://madrasphoto.example.com',      2, true, true, 'ssts-donors-2026'),
    ('Rigel Spices',             'Farm-fresh Indian foods',             '/images/RigelSpices.jpg',             'https://rigelspices.example.com',      3, true, true, 'ssts-donors-2026'),
    ('Blue Oak Consulting',      'Technology Services',                 '/images/BlueOakConsulting.jpg',       'https://blueoak.example.com',          4, true, true, 'ssts-donors-2026'),
    ('Atlanta Mojo Productions', 'Audio and Sound Service',             '/images/AtlantaMojoProductions.jpg',   'https://amp.example.com',              5, true, true, 'ssts-donors-2026'),
    ('Peachtree Learning Co.',   'Tutoring & Childcare',                '/images/PeachtreeLearningCo.jpg',      'https://peachtreelearning.example.com',6, true, true, 'ssts-donors-2026'),
    ('Riverwood Dental',         'Family Dentistry',                   '/images/RiverwoodDental.jpg',         'https://riverwooddental.example.com',  7, true, true, 'ssts-donors-2026'),
    ('Meena Family',             'Meena Dance Academy',                NULL,                                  'https://meenadance.example.com',       8, true, true, 'ssts-donors-2026'),
    ('Northside Foods',          'Fresh Groceries',                    NULL,                                  'https://northsidefoods.example.com',   9, true, true, 'ssts-donors-2026')
) AS v(name, tagline, photo_path, website_url, display_order, is_active, is_public, seed_key)
WHERE NOT EXISTS (
    SELECT 1 FROM ssts_donors d WHERE lower(d.name) = lower(v.name)
);
