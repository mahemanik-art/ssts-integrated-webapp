-- Sample data for SSTS roles and users
-- IMPORTANT: Replace the password_hash values with actual bcrypt hashes before running.
-- Generate a bcrypt hash with: bcrypt.encode("your_plain_password")

-- ============================================================
-- ROLES
-- ============================================================

-- Super Admin - full access to everything
INSERT INTO ssts_roles (name, description, is_active,
    can_create_users, can_edit_users, can_delete_users, can_view_users,
    can_manage_content, can_edit_pages, can_publish_content,
    can_manage_levels, can_manage_classes,
    can_manage_donors, can_view_donors,
    can_manage_events, can_view_calendar,
    can_manage_volunteers,
    can_view_financials, can_view_reports,
    can_manage_settings, can_view_audit_logs,
    can_send_announcements, can_send_newsletters,
    can_view_dashboard
) VALUES (
    'super_admin', 'Full system access - all privileges', true,
    true, true, true, true,
    true, true, true,
    true, true,
    true, true,
    true, true,
    true,
    true, true,
    true, true,
    true, true,
    true
);

-- Admin - most privileges except user deletion
INSERT INTO ssts_roles (name, description, is_active,
    can_create_users, can_edit_users, can_delete_users, can_view_users,
    can_manage_content, can_edit_pages, can_publish_content,
    can_manage_levels, can_manage_classes,
    can_manage_donors, can_view_donors,
    can_manage_events, can_view_calendar,
    can_manage_volunteers,
    can_view_financials, can_view_reports,
    can_manage_settings, can_view_audit_logs,
    can_send_announcements, can_send_newsletters,
    can_view_dashboard
) VALUES (
    'admin', 'Administrative access - can manage content, users, and view financials', true,
    true, true, false, true,
    true, true, true,
    true, true,
    true, true,
    true, true,
    true,
    true, true,
    true, true,
    true, true,
    true
);

-- Editor - content management focused
INSERT INTO ssts_roles (name, description, is_active,
    can_create_users, can_edit_users, can_delete_users, can_view_users,
    can_manage_content, can_edit_pages, can_publish_content,
    can_manage_levels, can_manage_classes,
    can_manage_donors, can_view_donors,
    can_manage_events, can_view_calendar,
    can_manage_volunteers,
    can_view_financials, can_view_reports,
    can_manage_settings, can_view_audit_logs,
    can_send_announcements, can_send_newsletters,
    can_view_dashboard
) VALUES (
    'editor', 'Content editor - manages pages, events, and announcements', true,
    false, false, false, false,
    true, true, true,
    false, false,
    false, true,
    true, true,
    false,
    false, false,
    false, false,
    true, true,
    true
);

-- Teacher - class and volunteer management
INSERT INTO ssts_roles (name, description, is_active,
    can_create_users, can_edit_users, can_delete_users, can_view_users,
    can_manage_content, can_edit_pages, can_publish_content,
    can_manage_levels, can_manage_classes,
    can_manage_donors, can_view_donors,
    can_manage_events, can_view_calendar,
    can_manage_volunteers,
    can_view_financials, can_view_reports,
    can_manage_settings, can_view_audit_logs,
    can_send_announcements, can_send_newsletters,
    can_view_dashboard
) VALUES (
    'teacher', 'Teacher access - manages classes, volunteers, and calendar', true,
    false, false, false, false,
    false, false, false,
    false, true,
    false, false,
    false, true,
    true,
    false, false,
    false, false,
    false, false,
    true
);

-- Volunteer Coordinator - volunteer and event focused
INSERT INTO ssts_roles (name, description, is_active,
    can_create_users, can_edit_users, can_delete_users, can_view_users,
    can_manage_content, can_edit_pages, can_publish_content,
    can_manage_levels, can_manage_classes,
    can_manage_donors, can_view_donors,
    can_manage_events, can_view_calendar,
    can_manage_volunteers,
    can_view_financials, can_view_reports,
    can_manage_settings, can_view_audit_logs,
    can_send_announcements, can_send_newsletters,
    can_view_dashboard
) VALUES (
    'volunteer_coordinator', 'Volunteer and event management', true,
    false, false, false, false,
    false, false, false,
    false, false,
    false, false,
    true, true,
    true,
    false, false,
    false, false,
    false, false,
    true
);

-- Read Only - view-only access
INSERT INTO ssts_roles (name, description, is_active,
    can_create_users, can_edit_users, can_delete_users, can_view_users,
    can_manage_content, can_edit_pages, can_publish_content,
    can_manage_levels, can_manage_classes,
    can_manage_donors, can_view_donors,
    can_manage_events, can_view_calendar,
    can_manage_volunteers,
    can_view_financials, can_view_reports,
    can_manage_settings, can_view_audit_logs,
    can_send_announcements, can_send_newsletters,
    can_view_dashboard
) VALUES (
    'read_only', 'View-only access to dashboard and public content', true,
    false, false, false, false,
    false, false, false,
    false, false,
    false, true,
    false, true,
    false,
    false, false,
    false, false,
    false, false,
    true
);

-- ============================================================
-- SAMPLE USERS
-- ============================================================
-- Replace password_hash with actual bcrypt.encode("your_password")

-- Admin users
INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, is_active, email_verified, receive_newsletter, receive_volunteer_updates, created_at, updated_at) VALUES
    ('ssts_admin', 'admin@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'SSTS Administrator', (SELECT id FROM ssts_roles WHERE name = 'super_admin'), 'admin', 'Super Admin', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('school_admin', 'schooladmin@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'School Admin', (SELECT id FROM ssts_roles WHERE name = 'admin'), 'admin', 'School Administrator', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Staff users
INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, is_active, email_verified, receive_newsletter, receive_volunteer_updates, created_at, updated_at) VALUES
    ('editor_user', 'editor@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'Content Editor', (SELECT id FROM ssts_roles WHERE name = 'editor'), 'staff', 'Editor', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('teacher_user', 'teacher@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'Class Teacher', (SELECT id FROM ssts_roles WHERE name = 'teacher'), 'staff', 'Class Teacher', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('volunteer_lead', 'volunteer@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'Volunteer Coordinator', (SELECT id FROM ssts_roles WHERE name = 'volunteer_coordinator'), 'staff', 'Volunteer Coordinator', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Parent users
INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, is_active, email_verified, receive_newsletter, receive_volunteer_updates, created_at, updated_at) VALUES
    ('parent1', 'parent1@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'Parent One', (SELECT id FROM ssts_roles WHERE name = 'read_only'), 'parent', NULL, true, false, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('parent2', 'parent2@sstamschool.org', '$2a$10$REPLACE_WITH_ACTUAL_BCRYPT_HASH', 'Parent Two', (SELECT id FROM ssts_roles WHERE name = 'read_only'), 'parent', NULL, true, false, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================================
-- BEGIN calendar seed (2026-2027) -- sourced from the MTS academic calendar page
-- (https://mariettatamilschool.com/mts-academic-calendar/). Loaded into
-- ssts_calendar_events to back the /calendar page. Idempotent: re-running
-- replaces this academic year's events. is_active is set explicitly (the live
-- Hibernate-created table has no column default). academic_year = Aug-Jul cycle.
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
