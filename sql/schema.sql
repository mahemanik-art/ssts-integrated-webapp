-- SSTS Role-Based Access Control Schema
-- Supports granular privileges for different user types
-- Single login system with separate data and UI modules

-- Roles table with privilege columns
CREATE TABLE IF NOT EXISTS ssts_roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    -- User management privileges
    can_create_users BOOLEAN NOT NULL DEFAULT false,
    can_edit_users BOOLEAN NOT NULL DEFAULT false,
    can_delete_users BOOLEAN NOT NULL DEFAULT false,
    can_view_users BOOLEAN NOT NULL DEFAULT false,
    -- Content management privileges
    can_manage_content BOOLEAN NOT NULL DEFAULT false,
    can_edit_pages BOOLEAN NOT NULL DEFAULT false,
    can_publish_content BOOLEAN NOT NULL DEFAULT false,
    -- Class/Level management
    can_manage_levels BOOLEAN NOT NULL DEFAULT false,
    can_manage_classes BOOLEAN NOT NULL DEFAULT false,
    -- Donor management
    can_manage_donors BOOLEAN NOT NULL DEFAULT false,
    can_view_donors BOOLEAN NOT NULL DEFAULT false,
    -- Calendar & Events
    can_manage_events BOOLEAN NOT NULL DEFAULT false,
    can_view_calendar BOOLEAN NOT NULL DEFAULT false,
    -- Volunteer management
    can_manage_volunteers BOOLEAN NOT NULL DEFAULT false,
    -- Financial & Reporting
    can_view_financials BOOLEAN NOT NULL DEFAULT false,
    can_view_reports BOOLEAN NOT NULL DEFAULT false,
    -- System settings
    can_manage_settings BOOLEAN NOT NULL DEFAULT false,
    can_view_audit_logs BOOLEAN NOT NULL DEFAULT false,
    -- Communication
    can_send_announcements BOOLEAN NOT NULL DEFAULT false,
    can_send_newsletters BOOLEAN NOT NULL DEFAULT false,
    -- Read-only fallback
    can_view_dashboard BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Users table (core account info only - shared login system)
CREATE TABLE IF NOT EXISTS ssts_users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role_id INTEGER NOT NULL REFERENCES ssts_roles(id),
    -- User type: parent, staff, volunteer, admin
    user_type VARCHAR(20) NOT NULL DEFAULT 'parent' CHECK (user_type IN ('parent', 'staff', 'volunteer', 'admin')),
    -- School-specific designation (e.g. Volunteer Coordinator, Class Teacher)
    designation VARCHAR(100),
    joining_date DATE,
    last_date DATE,
    receive_newsletter BOOLEAN NOT NULL DEFAULT true,
    receive_volunteer_updates BOOLEAN NOT NULL DEFAULT true,
    is_active BOOLEAN NOT NULL DEFAULT true,
    email_verified BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP WITH TIME ZONE
);

-- User profiles table (personal details, 1:1 with users)
CREATE TABLE IF NOT EXISTS ssts_user_profiles (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE REFERENCES ssts_users(id) ON DELETE CASCADE,
    phone VARCHAR(20),
    alternate_email VARCHAR(100),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(50),
    zip_code VARCHAR(20),
    country VARCHAR(50) DEFAULT 'USA',
    bio TEXT,
    avatar_url VARCHAR(500),
    -- Professional info
    occupation VARCHAR(100),
    employer VARCHAR(100),
    years_in_community INTEGER DEFAULT 0,
    -- Prior experience relevant to school
    prior_education TEXT,
    prior_tamil_experience TEXT,
    prior_teaching_experience TEXT,
    prior_volunteer_experience TEXT,
    certifications TEXT,
    interests TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Parent-specific details (extends user_profiles for parent users)
CREATE TABLE IF NOT EXISTS ssts_parent_details (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE REFERENCES ssts_users(id) ON DELETE CASCADE,
    student_name VARCHAR(100),
    student_grade VARCHAR(20),
    student_class VARCHAR(50),
    emergency_contact VARCHAR(20),
    pickup_authorized TEXT,
    medical_conditions TEXT,
    allergies TEXT,
    insurance_info TEXT,
    parent_type VARCHAR(20) CHECK (parent_type IN ('mother', 'father', 'guardian')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Staff-specific details (extends user_profiles for staff users)
CREATE TABLE IF NOT EXISTS ssts_staff_details (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE REFERENCES ssts_users(id) ON DELETE CASCADE,
    employee_id VARCHAR(50) UNIQUE,
    department VARCHAR(100),
    job_title VARCHAR(100),
    salary NUMERIC(12,2),
    employment_type VARCHAR(20) CHECK (employment_type IN ('full_time', 'part_time', 'contract', 'volunteer')),
    qualification TEXT,
    teaching_certification VARCHAR(255),
    availability TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Login sessions table (for active user sessions)
CREATE TABLE IF NOT EXISTS ssts_user_sessions (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES ssts_users(id) ON DELETE CASCADE,
    session_token VARCHAR(255) NOT NULL UNIQUE,
    refresh_token VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Login attempts table (for rate limiting and security)
CREATE TABLE IF NOT EXISTS ssts_login_attempts (
    id SERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45),
    success BOOLEAN NOT NULL DEFAULT false,
    failure_reason VARCHAR(255),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Password reset tokens table
CREATE TABLE IF NOT EXISTS ssts_password_reset_tokens (
    id SERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Students table (enrollment records, independent of login accounts)
CREATE TABLE IF NOT EXISTS ssts_students (
    id SERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),
    date_of_birth DATE,
    grade VARCHAR(20),
    class_level VARCHAR(50),
    gender VARCHAR(20) CHECK (gender IN ('male', 'female', 'other', 'prefer_not_to_say')),
    enrollment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    parent_name VARCHAR(100),
    parent_phone VARCHAR(20),
    parent_email VARCHAR(100),
    parent_relationship VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_ssts_users_email ON ssts_users(email);
CREATE INDEX IF NOT EXISTS idx_ssts_users_role ON ssts_users(role_id);
CREATE INDEX IF NOT EXISTS idx_ssts_users_type ON ssts_users(user_type);
CREATE INDEX IF NOT EXISTS idx_ssts_users_active ON ssts_users(is_active);
CREATE INDEX IF NOT EXISTS idx_ssts_user_profiles_user ON ssts_user_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_ssts_parent_user ON ssts_parent_details(user_id);
CREATE INDEX IF NOT EXISTS idx_ssts_staff_user ON ssts_staff_details(user_id);
CREATE INDEX IF NOT EXISTS idx_ssts_sessions_user ON ssts_user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_ssts_sessions_token ON ssts_user_sessions(session_token);
CREATE INDEX IF NOT EXISTS idx_ssts_login_attempts_email ON ssts_login_attempts(email);
CREATE INDEX IF NOT EXISTS idx_ssts_login_attempts_time ON ssts_login_attempts(attempted_at);
CREATE INDEX IF NOT EXISTS idx_ssts_reset_tokens_token ON ssts_password_reset_tokens(token);


-- Calendar events table (school calendar; sourced by /calendar page)
CREATE TABLE IF NOT EXISTS ssts_calendar_events (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    event_date DATE NOT NULL,
    end_date DATE,
    -- working = class/session day, holiday = no school
    event_type VARCHAR(20) NOT NULL DEFAULT 'working' CHECK (event_type IN ('working', 'holiday')),
    -- Academic year this event belongs to, e.g. '2026-2027' (Aug-Jul cycle).
    -- Format is enforced so equality/string-ordering is safe; the value is always
    -- produced by util/AcademicYear.of() (never free-typed), and this CHECK is
    -- defense-in-depth against bad manual inserts.
    academic_year VARCHAR(9) NOT NULL
        CONSTRAINT chk_ssts_calendar_events_academic_year CHECK (academic_year ~ '^[0-9]{4}-[0-9]{4}$'),
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The live table may have been created by Hibernate (ddl-auto=update) without the
-- column defaults declared above. Bring it in line so inserts can omit those columns.
ALTER TABLE ssts_calendar_events ALTER COLUMN is_active  SET DEFAULT true;
ALTER TABLE ssts_calendar_events ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE ssts_calendar_events ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

-- Backfill the CHECK onto databases created before it existed (idempotent).
-- If this fails, some existing rows are malformed; find them with:
--   SELECT id, academic_year FROM ssts_calendar_events WHERE academic_year !~ '^[0-9]{4}-[0-9]{4}$';
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_ssts_calendar_events_academic_year'
    ) THEN
        ALTER TABLE ssts_calendar_events
            ADD CONSTRAINT chk_ssts_calendar_events_academic_year
            CHECK (academic_year ~ '^[0-9]{4}-[0-9]{4}$');
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ssts_calendar_events_year ON ssts_calendar_events(academic_year);
CREATE INDEX IF NOT EXISTS idx_ssts_calendar_events_date ON ssts_calendar_events(event_date);

CREATE INDEX IF NOT EXISTS idx_ssts_reset_tokens_email ON ssts_password_reset_tokens(email);