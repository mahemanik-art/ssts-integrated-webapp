-- Seed data: variety of staff users + profiles + staff details
-- Avatars reference the teacher portraits in src/main/resources/static/images/teachers/
-- Idempotent: safe to re-run (guarded with NOT EXISTS).
-- New staff password: staff123 (bcrypt $2a$10$ hash below).
--
-- Run with: psql "$DATABASE_URL" -f sql/staff_profiles_seed.sql

-- ============================================================
-- 1) STAFF USERS
--    - 3 existing staff get realistic names/designations/joining dates
--    - 7 new staff users covering different roles and designations
-- ============================================================

UPDATE ssts_users SET full_name = 'Meera Krishnan', joining_date = DATE '2019-08-12', updated_at = CURRENT_TIMESTAMP WHERE username = 'editor_user';
UPDATE ssts_users SET full_name = 'Suresh Menon', joining_date = DATE '2016-08-15', updated_at = CURRENT_TIMESTAMP WHERE username = 'teacher_user';
UPDATE ssts_users SET full_name = 'Lakshmi Venkat', joining_date = DATE '2021-01-18', updated_at = CURRENT_TIMESTAMP WHERE username = 'volunteer_lead';

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'vice_principal', 'vp@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Dr. Arvind Kulkarni', r.id, 'admin', 'Vice Principal', DATE '2018-06-04', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'admin'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'vice_principal');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'math_teacher', 'math@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Priya Nair', r.id, 'staff', 'Mathematics Teacher', DATE '2020-08-10', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'math_teacher');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'science_teacher', 'science@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Dr. Raghavan Iyer', r.id, 'staff', 'Science Teacher', DATE '2017-08-07', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'science_teacher');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'tamil_teacher', 'tamil@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Kamala Balamurali', r.id, 'staff', 'Tamil Language Teacher', DATE '2015-08-17', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'tamil_teacher');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'art_teacher', 'art@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Nandini Rao', r.id, 'staff', 'Art & Craft Teacher', DATE '2022-08-08', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'art_teacher');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'music_teacher', 'music@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Shalini Gupta', r.id, 'staff', 'Music Teacher', DATE '2023-01-09', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'music_teacher');

INSERT INTO ssts_users (username, email, password_hash, full_name, role_id, user_type, designation, joining_date, receive_newsletter, receive_volunteer_updates, is_active, email_verified, created_at, updated_at)
SELECT 'school_counselor', 'counselor@sstamschool.org', '$2a$10$QtfGm4SMPabsZbOO9juRlueehy1vIVOmEhKmAl2gWHtLFJL7ISQLy', 'Ananya Bose', r.id, 'staff', 'School Counselor', DATE '2024-02-05', true, true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_roles r WHERE r.name = 'read_only'
AND NOT EXISTS (SELECT 1 FROM ssts_users u WHERE u.username = 'school_counselor');

-- ============================================================
-- 2) USER PROFILES (one per staff user, each with a unique avatar)
-- ============================================================

INSERT INTO ssts_user_profiles (user_id, phone, address_line1, city, state, zip_code, country, bio, avatar_url, occupation, employer, years_in_community, prior_education, prior_tamil_experience, prior_teaching_experience, prior_volunteer_experience, certifications, interests, created_at, updated_at)
SELECT u.id, '404-555-0101', '4820 River Ridge Dr', 'Sandy Springs', 'GA', '30328', 'USA',
       'Meera keeps the school website and newsletters running. She has been with SSTS for over five years and loves bringing stories from our classrooms to the wider community.',
       '/images/teachers/teacher-01.jpg',
       'Content Editor', 'Sandy Springs Tamil School', 9,
       'M.A. in Mass Communication, Annamalai University',
       'Grew up reading Tamil periodicals at home; edits the SSTS bilingual newsletter',
       '8 years editing school publications and online content',
       'Volunteered with the SSTS library drive for 3 years',
       'Google Certified Educator Level 1',
       'Blogging, Carnatic vocal music, gardening', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'editor_user'
AND NOT EXISTS (SELECT 1 FROM ssts_user_profiles p WHERE p.user_id = u.id);

INSERT INTO ssts_user_profiles (user_id, phone, address_line1, city, state, zip_code, country, bio, avatar_url, occupation, employer, years_in_community, prior_education, prior_tamil_experience, prior_teaching_experience, prior_volunteer_experience, certifications, interests, created_at, updated_at)
SELECT u.id, '770-555-0102', '1150 Dunwoody Crossing', 'Dunwoody', 'GA', '30338', 'USA',
       'Suresh has taught Sunday school for more than a decade and mentors new class teachers. He believes every child learns best when lessons feel like a conversation.',
       '/images/teachers/teacher-05.jpg',
       'Class Teacher', 'Sandy Springs Tamil School', 14,
       'B.Tech, Anna University',
       'Tamil Saturday school alumnus; teaches Tamil folk songs in class',
       '12 years of Tamil and culture class instruction',
       'Coach for the SSTS quiz team since 2019',
       'Georgia Educator Certification (Provisional)',
       'Cricket, storytelling, public speaking', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'teacher_user'
AND NOT EXISTS (SELECT 1 FROM ssts_user_profiles p WHERE p.user_id = u.id);

INSERT INTO ssts_user_profiles (user_id, phone, address_line1, city, state, zip_code, country, bio, avatar_url, occupation, employer, years_in_community, prior_education, prior_tamil_experience, prior_teaching_experience, prior_volunteer_experience, certifications, interests, created_at, updated_at)
SELECT u.id, '404-555-0103', '275 Johnston Ferry Rd', 'Atlanta', 'GA', '30339', 'USA',
       'Lakshmi coordinates volunteers for every major school event, from Pongal celebrations to graduation. If you have an hour to give, she will find the perfect spot for it.',
       '/images/teachers/teacher-06.jpg',
       'Volunteer Coordinator', 'Sandy Springs Tamil School', 7,
       'B.S. in Hospitality Management, Osmania University',
       'Organizes Tamil New Year and Pongal community events',
       '6 years coordinating after-school enrichment programs',
       '10+ years volunteering with Tamil Sangam and temple youth groups',
       'Certified Event Planner (CMP)',
       'Event planning, henna art, hiking', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'volunteer_lead'
AND NOT EXISTS (SELECT 1 FROM ssts_user_profiles p WHERE p.user_id = u.id);

INSERT INTO ssts_user_profiles (user_id, phone, address_line1, city, state, zip_code, country, bio, avatar_url, occupation, employer, years_in_community, prior_education, prior_tamil_experience, prior_teaching_experience, prior_volunteer_experience, certifications, interests, created_at, updated_at)
SELECT u.id, '770-555-0104', '89 Ashford Grove Ln', 'Alpharetta', 'GA', '30004', 'USA',
       'Dr. Kulkarni leads the schools academic program and accreditation efforts. He mentors teachers across all levels and keeps the curriculum aligned with state standards.',
       '/images/teachers/teacher-03.jpg',
       'Vice Principal', 'Sandy Springs Tamil School', 16,
       'Ph.D. in Education, University of Madras',
       'Raised in Chennai; oversees Tamil language curriculum standards',
       '20 years in education, 9 years in school leadership',
       'Board member of the Atlanta Tamil Association',
       'Georgia Leadership Certification, Ed.D.',
       'Policy debate, chess, classical music', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'vice_principal'
AND NOT EXISTS (SELECT 1 FROM ssts_user_profiles p WHERE p.user_id = u.id);

INSERT INTO ssts_user_profiles (user_id, phone, address_line1, city, state, zip_code, country, bio, avatar_url, occupation, employer, years_in_community, prior_education, prior_tamil_experience, prior_teaching_experience, prior_volunteer_experience, certifications, interests, created_at, updated_at)
SELECT u.id, '404-555-0105', '6405 Windward Parkway', 'Johns Creek', 'GA', '30022', 'USA',
       'Priya makes algebra approachable with games, puzzles, and plenty of patience. Her students consistently score among the highest on statewide math assessments.',
       '/images/teachers/teacher-04.jpg',
       'Mathematics Teacher', 'Sandy Springs Tamil School', 6,
       'M.Sc. in Mathematics, Presidency College',
       'Tutoring Tamil-speaking students in math since college',
       '7 years teaching middle and high school math',
       'Math night volunteer for three school years',
       'State Mathematics Certification (6-12)',
       'Sudoku, baking, classical dance', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'math_teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_user_profiles p WHERE p.user_id = u.id);

INSERT INTO ssts_user_profiles (user_id, phone, address_line1, city, state, zip_code, country, bio, avatar_url, occupation, employer, years_in_community, prior_education, prior_tamil_experience, prior_teaching_experience, prior_volunteer_experience, certifications, interests, created_at, updated_at)
SELECT u.id, '770-555-0106', '3105 Lake Forrest Ct', 'Roswell', 'GA', '30075', 'USA',
       'Dr. Iyer runs the science fair and loves turning everyday questions into experiments. His classes regularly advance students to regional science competitions.',
       '/images/teachers/teacher-07.jpg',
       'Science Teacher', 'Sandy Springs Tamil School', 11,
       'Ph.D. in Chemistry, IIT Madras',
       'Brings Tamil science terminology into lessons for heritage learners',
       '15 years teaching chemistry and physics',
       'Judges regional science olympiads across Georgia',
       'AP Chemistry Certified, Georgia Science Certification',
       'Astronomy, gardening, cricket', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'science_teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_user_profiles p WHERE p.user_id = u.id);

INSERT INTO ssts_user_profiles (user_id, phone, address_line1, city, state, zip_code, country, bio, avatar_url, occupation, employer, years_in_community, prior_education, prior_tamil_experience, prior_teaching_experience, prior_volunteer_experience, certifications, interests, created_at, updated_at)
SELECT u.id, '404-555-0107', '1425 North Point Pkwy', 'Alpharetta', 'GA', '30004', 'USA',
       'Kamala has taught Tamil for more than twenty years and trains new Tamil teachers at SSTS. She keeps the language alive through songs, drama, and conversation in her classroom.',
       '/images/teachers/teacher-08.jpg',
       'Tamil Language Teacher', 'Sandy Springs Tamil School', 21,
       'M.A. in Tamil Literature, Madurai Kamaraj University',
       'Native Tamil speaker; authored the SSTS Grade 3-5 Tamil workbook',
       '22 years teaching Tamil as a heritage language',
       'Founder of the SSTS summer Tamil camp',
       'Tamil Nadu Teacher Eligibility Test (TNTET)',
       'Poetry, Bharatanatyam, writing', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'tamil_teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_user_profiles p WHERE p.user_id = u.id);

INSERT INTO ssts_user_profiles (user_id, phone, address_line1, city, state, zip_code, country, bio, avatar_url, occupation, employer, years_in_community, prior_education, prior_tamil_experience, prior_teaching_experience, prior_volunteer_experience, certifications, interests, created_at, updated_at)
SELECT u.id, '770-555-0108', '5010 Sandy Plains Rd', 'Marietta', 'GA', '30060', 'USA',
       'Nandinis art room is where colours, kolam patterns, and creativity meet. She guides students through painting, craft, and traditional South Indian art forms.',
       '/images/teachers/teacher-09.jpg',
       'Art & Craft Teacher', 'Sandy Springs Tamil School', 4,
       'B.F.A. in Painting, Kalakshetra Foundation',
       'Teaches kolam, tanjore-style painting, and festival crafts',
       '4 years running weekend art workshops',
       'Volunteer set designer for the SSTS annual day',
       'Portfolio Certificate in Fine Arts',
       'Painting, pottery, photography', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'art_teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_user_profiles p WHERE p.user_id = u.id);

INSERT INTO ssts_user_profiles (user_id, phone, address_line1, city, state, zip_code, country, bio, avatar_url, occupation, employer, years_in_community, prior_education, prior_tamil_experience, prior_teaching_experience, prior_volunteer_experience, certifications, interests, created_at, updated_at)
SELECT u.id, '404-555-0109', '2200 Powers Ferry Rd', 'Smyrna', 'GA', '30080', 'USA',
       'Shalini introduces students to both Carnatic and Western music. She prepares small ensembles for school functions and cultural showcases throughout the year.',
       '/images/teachers/teacher-10.jpg',
       'Music Teacher', 'Sandy Springs Tamil School', 3,
       'B.Mus. in Carnatic Music, Sri Krishna Sangeetha Vidyalaya',
       'Trained in Carnatic vocal; teaches Tamil devotional songs',
       '3 years of group music instruction',
       'Accompanies the SSTS choir for charity events',
       'Grade 1 Voice Certification (Trinity College)',
       'Carnatic singing, violin, film soundtracks', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'music_teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_user_profiles p WHERE p.user_id = u.id);

INSERT INTO ssts_user_profiles (user_id, phone, address_line1, city, state, zip_code, country, bio, avatar_url, occupation, employer, years_in_community, prior_education, prior_tamil_experience, prior_teaching_experience, prior_volunteer_experience, certifications, interests, created_at, updated_at)
SELECT u.id, '770-555-0110', '900 Mansell Rd', 'Roswell', 'GA', '30076', 'USA',
       'Ananya supports students and families with study skills, transitions, and a listening ear. She also runs the peer mentoring program for older students.',
       '/images/teachers/teacher-02.jpg',
       'School Counselor', 'Sandy Springs Tamil School', 2,
       'M.S. in School Counseling, Georgia State University',
       'Bilingual counseling available in Tamil and English',
       '2 years of school counseling practice',
       'Facilitates the student volunteer recognition program',
       'Licensed Professional Counselor (LPC)',
       'Journaling, yoga, community theater', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'school_counselor'
AND NOT EXISTS (SELECT 1 FROM ssts_user_profiles p WHERE p.user_id = u.id);

-- ============================================================
-- 3) LINK profiles onto ssts_users.profile_id
--    (JPA: SstsUser.profile -> ssts_users.profile_id; used by the team page query)
-- ============================================================

UPDATE ssts_users u
SET profile_id = p.id, updated_at = CURRENT_TIMESTAMP
FROM ssts_user_profiles p
WHERE p.user_id = u.id
  AND u.profile_id IS DISTINCT FROM p.id;

-- ============================================================
-- 4) STAFF DETAILS (departments, employment types, qualifications)
-- ============================================================

INSERT INTO ssts_staff_details (user_id, employee_id, department, job_title, salary, employment_type, qualification, teaching_certification, availability, created_at, updated_at)
SELECT u.id, 'SSTS-EMP-001', 'Communications', 'Content Editor', 58000.00, 'full_time',
       'M.A. Mass Communication', 'Google Certified Educator Level 1', 'Mon-Fri, 9:00 AM - 5:00 PM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'editor_user'
AND NOT EXISTS (SELECT 1 FROM ssts_staff_details s WHERE s.user_id = u.id);

INSERT INTO ssts_staff_details (user_id, employee_id, department, job_title, salary, employment_type, qualification, teaching_certification, availability, created_at, updated_at)
SELECT u.id, 'SSTS-EMP-002', 'Elementary - Grade 5', 'Class Teacher', 64000.00, 'full_time',
       'B.Tech, Anna University', 'Georgia Educator Certification (Provisional)', 'Sat 9:00 AM - 1:00 PM; weekdays by appointment', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'teacher_user'
AND NOT EXISTS (SELECT 1 FROM ssts_staff_details s WHERE s.user_id = u.id);

INSERT INTO ssts_staff_details (user_id, employee_id, department, job_title, salary, employment_type, qualification, teaching_certification, availability, created_at, updated_at)
SELECT u.id, 'SSTS-EMP-003', 'Student Affairs', 'Volunteer Coordinator', NULL, 'volunteer',
       'B.S. Hospitality Management', 'Certified Event Planner (CMP)', 'Flexible; event weekends', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'volunteer_lead'
AND NOT EXISTS (SELECT 1 FROM ssts_staff_details s WHERE s.user_id = u.id);

INSERT INTO ssts_staff_details (user_id, employee_id, department, job_title, salary, employment_type, qualification, teaching_certification, availability, created_at, updated_at)
SELECT u.id, 'SSTS-EMP-004', 'Administration', 'Vice Principal', 92000.00, 'full_time',
       'Ph.D. Education, University of Madras', 'Georgia Leadership Certification', 'Mon-Fri, 8:00 AM - 5:00 PM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'vice_principal'
AND NOT EXISTS (SELECT 1 FROM ssts_staff_details s WHERE s.user_id = u.id);

INSERT INTO ssts_staff_details (user_id, employee_id, department, job_title, salary, employment_type, qualification, teaching_certification, availability, created_at, updated_at)
SELECT u.id, 'SSTS-EMP-005', 'Middle School - Math', 'Mathematics Teacher', 67000.00, 'full_time',
       'M.Sc. Mathematics, Presidency College', 'State Mathematics Certification (6-12)', 'Sat 9:00 AM - 12:30 PM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'math_teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_staff_details s WHERE s.user_id = u.id);

INSERT INTO ssts_staff_details (user_id, employee_id, department, job_title, salary, employment_type, qualification, teaching_certification, availability, created_at, updated_at)
SELECT u.id, 'SSTS-EMP-006', 'High School - Science', 'Science Teacher', 74000.00, 'full_time',
       'Ph.D. Chemistry, IIT Madras', 'AP Chemistry Certified; Georgia Science Certification', 'Sat 9:00 AM - 1:00 PM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'science_teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_staff_details s WHERE s.user_id = u.id);

INSERT INTO ssts_staff_details (user_id, employee_id, department, job_title, salary, employment_type, qualification, teaching_certification, availability, created_at, updated_at)
SELECT u.id, 'SSTS-EMP-007', 'Language - Tamil', 'Tamil Language Teacher', 61000.00, 'part_time',
       'M.A. Tamil Literature, Madurai Kamaraj University', 'Tamil Nadu Teacher Eligibility Test (TNTET)', 'Sat 9:00 AM - 1:00 PM; Fri 5:00 PM - 7:00 PM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'tamil_teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_staff_details s WHERE s.user_id = u.id);

INSERT INTO ssts_staff_details (user_id, employee_id, department, job_title, salary, employment_type, qualification, teaching_certification, availability, created_at, updated_at)
SELECT u.id, 'SSTS-EMP-008', 'Arts & Culture', 'Art & Craft Teacher', 42000.00, 'part_time',
       'B.F.A. Painting, Kalakshetra Foundation', 'Portfolio Certificate in Fine Arts', 'Sat 10:00 AM - 1:00 PM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'art_teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_staff_details s WHERE s.user_id = u.id);

INSERT INTO ssts_staff_details (user_id, employee_id, department, job_title, salary, employment_type, qualification, teaching_certification, availability, created_at, updated_at)
SELECT u.id, 'SSTS-EMP-009', 'Arts & Culture', 'Music Teacher', 38000.00, 'contract',
       'B.Mus. Carnatic Music, Sri Krishna Sangeetha Vidyalaya', 'Grade 1 Voice Certification (Trinity College)', 'Sat 11:00 AM - 1:00 PM; rehearsal weeks as scheduled', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'music_teacher'
AND NOT EXISTS (SELECT 1 FROM ssts_staff_details s WHERE s.user_id = u.id);

INSERT INTO ssts_staff_details (user_id, employee_id, department, job_title, salary, employment_type, qualification, teaching_certification, availability, created_at, updated_at)
SELECT u.id, 'SSTS-EMP-010', 'Student Support', 'School Counselor', 71000.00, 'full_time',
       'M.S. School Counseling, Georgia State University', 'Licensed Professional Counselor (LPC)', 'Mon-Fri, 10:00 AM - 6:00 PM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ssts_users u WHERE u.username = 'school_counselor'
AND NOT EXISTS (SELECT 1 FROM ssts_staff_details s WHERE s.user_id = u.id);
