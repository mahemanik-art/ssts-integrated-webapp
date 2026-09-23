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