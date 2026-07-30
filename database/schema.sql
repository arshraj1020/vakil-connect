-- ==========================================================
-- ⚠️  HISTORICAL DESIGN ARTEFACT — DO NOT RUN
-- ==========================================================
--
-- This file predates Flyway and NO LONGER MATCHES the live schema. It is kept
-- only as a record of the original design.
--
-- The schema is owned by:
--     backend/src/main/resources/db/migration/V1__init.sql .. V6__*.sql
--
-- Flyway applies those on startup and Hibernate runs with `ddl-auto: validate`,
-- so a database built from THIS file will fail application startup with a
-- schema-validation error. Among other differences it has no reference-data
-- tables (V3), no reference links (V4), no seeded specializations (V5) and no
-- backfill (V6).
--
-- To create a database:  createdb vakilconnect  — then start the backend and
-- let Flyway build it. See DEPLOYMENT.md.
--
-- ==========================================================
-- VakilConnect Database Schema  (original design, superseded)
-- PostgreSQL
-- ==========================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==========================================================
-- ENUMS
-- ==========================================================

CREATE TYPE user_role AS ENUM (
    'CLIENT',
    'LAWYER',
    'ADMIN'
);

CREATE TYPE consultation_mode AS ENUM (
    'ONLINE',
    'OFFLINE'
);

CREATE TYPE appointment_status AS ENUM (
    'PENDING',
    'ACCEPTED',
    'REJECTED',
    'COMPLETED',
    'CANCELLED'
);

CREATE TYPE gender_type AS ENUM (
    'MALE',
    'FEMALE',
    'OTHER'
);

-- ==========================================================
-- USER
-- ==========================================================

CREATE TABLE users (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    full_name VARCHAR(150) NOT NULL,

    email VARCHAR(255) UNIQUE NOT NULL,

    password_hash TEXT NOT NULL,

    phone_number VARCHAR(20),

    role user_role NOT NULL,

    is_email_verified BOOLEAN DEFAULT FALSE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP

);

-- ==========================================================
-- CLIENT PROFILE
-- ==========================================================

CREATE TABLE client_profiles (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    date_of_birth DATE,

    gender gender_type,

    address TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP

);

-- ==========================================================
-- LAWYER PROFILE
-- ==========================================================

CREATE TABLE lawyer_profiles (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    bar_council_number VARCHAR(100) UNIQUE NOT NULL,

    years_of_experience INT,

    consultation_fee NUMERIC(10,2),

    bio TEXT,

    office_address TEXT,

    profile_photo_url TEXT,

    is_verified BOOLEAN DEFAULT FALSE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP

);

-- ==========================================================
-- SPECIALIZATION
-- ==========================================================

CREATE TABLE specializations (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(100) UNIQUE NOT NULL

);

-- ==========================================================
-- LAWYER SPECIALIZATION
-- ==========================================================

CREATE TABLE lawyer_specializations (

    lawyer_id UUID REFERENCES lawyer_profiles(id) ON DELETE CASCADE,

    specialization_id UUID REFERENCES specializations(id) ON DELETE CASCADE,

    PRIMARY KEY (lawyer_id, specialization_id)

);

-- ==========================================================
-- AVAILABILITY
-- ==========================================================

CREATE TABLE availabilities (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    lawyer_id UUID NOT NULL REFERENCES lawyer_profiles(id) ON DELETE CASCADE,

    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),

    start_time TIME NOT NULL,

    end_time TIME NOT NULL,

    is_available BOOLEAN DEFAULT TRUE

);

-- ==========================================================
-- APPOINTMENTS
-- ==========================================================

CREATE TABLE appointments (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    client_id UUID NOT NULL REFERENCES client_profiles(id),

    lawyer_id UUID NOT NULL REFERENCES lawyer_profiles(id),

    appointment_date DATE NOT NULL,

    appointment_time TIME NOT NULL,

    consultation_mode consultation_mode NOT NULL,

    status appointment_status DEFAULT 'PENDING',

    notes TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP

);

-- ==========================================================
-- REVIEWS
-- ==========================================================

CREATE TABLE reviews (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    appointment_id UUID UNIQUE REFERENCES appointments(id) ON DELETE CASCADE,

    client_id UUID REFERENCES client_profiles(id),

    lawyer_id UUID REFERENCES lawyer_profiles(id),

    rating INT CHECK (rating BETWEEN 1 AND 5),

    comment TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP

);

-- ==========================================================
-- DOCUMENTS
-- ==========================================================

CREATE TABLE documents (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    appointment_id UUID REFERENCES appointments(id) ON DELETE CASCADE,

    uploaded_by UUID REFERENCES users(id),

    file_name TEXT,

    file_url TEXT,

    document_type VARCHAR(100),

    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP

);

-- ==========================================================
-- NOTIFICATIONS
-- ==========================================================

CREATE TABLE notifications (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID REFERENCES users(id) ON DELETE CASCADE,

    title VARCHAR(255),

    message TEXT,

    type VARCHAR(50),

    is_read BOOLEAN DEFAULT FALSE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP

);

-- ==========================================================
-- INDEXES
-- ==========================================================

CREATE INDEX idx_users_email
ON users(email);

CREATE INDEX idx_users_role
ON users(role);

CREATE INDEX idx_appointments_client
ON appointments(client_id);

CREATE INDEX idx_appointments_lawyer
ON appointments(lawyer_id);

CREATE INDEX idx_appointments_date
ON appointments(appointment_date);

CREATE INDEX idx_documents_appointment
ON documents(appointment_id);

CREATE INDEX idx_notifications_user
ON notifications(user_id);