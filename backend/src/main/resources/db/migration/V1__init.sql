-- ============================================================================
-- VakilConnect — baseline schema (V1)
-- Source of truth for the database. Mirrors the JPA entity model exactly so
-- that Hibernate `ddl-auto: validate` passes with zero differences.
--
-- Type mapping (Hibernate 6 / PostgreSQL):
--   UUID                -> uuid
--   String (len=N)      -> varchar(N)   | String (no len) -> varchar(255)
--   LocalDateTime       -> timestamp(6)
--   LocalDate           -> date
--   LocalTime           -> time(6)
--   BigDecimal(p,s)     -> numeric(p,s)
--   Integer             -> integer
--   Double              -> double precision
--   boolean/Boolean     -> boolean
--   Enum (STRING)       -> varchar(255)
--
-- No secondary indexes are declared here: the entity model defines none
-- (no @Index / @Table(indexes=...)). Primary keys and UNIQUE constraints
-- create their own implicit indexes. Performance indexes are intentionally
-- out of scope for this baseline.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- users  (User) — every person on the platform (CLIENT / LAWYER / ADMIN)
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                 uuid          NOT NULL,
    created_at         timestamp(6)  NOT NULL,
    updated_at         timestamp(6)  NOT NULL,
    full_name          varchar(150)  NOT NULL,
    email              varchar(255)  NOT NULL,
    password_hash      varchar(255)  NOT NULL,
    phone_number       varchar(20),
    is_email_verified  boolean       NOT NULL,
    role               varchar(255)  NOT NULL,
    active             boolean       NOT NULL DEFAULT true,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ---------------------------------------------------------------------------
-- lawyers  (Lawyer) — professional profile, 1:1 with a LAWYER user
-- ---------------------------------------------------------------------------
CREATE TABLE lawyers (
    id                   uuid           NOT NULL,
    created_at           timestamp(6)   NOT NULL,
    updated_at           timestamp(6)   NOT NULL,
    user_id              uuid           NOT NULL,
    bar_council_number   varchar(255)   NOT NULL,
    experience_years     integer        NOT NULL,
    bio                  varchar(2000),
    consultation_fee     numeric(10,2)  NOT NULL,
    city                 varchar(255)   NOT NULL,
    office_address       varchar(255)   NOT NULL,
    verified             boolean        NOT NULL,
    rating               double precision NOT NULL,
    total_reviews        integer        NOT NULL,
    CONSTRAINT pk_lawyers PRIMARY KEY (id),
    CONSTRAINT uq_lawyers_user UNIQUE (user_id),
    CONSTRAINT uq_lawyers_bar_council_number UNIQUE (bar_council_number),
    CONSTRAINT fk_lawyers_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- ---------------------------------------------------------------------------
-- specializations  (Specialization) — legal practice areas
-- ---------------------------------------------------------------------------
CREATE TABLE specializations (
    id          uuid          NOT NULL,
    created_at  timestamp(6)  NOT NULL,
    updated_at  timestamp(6)  NOT NULL,
    name        varchar(100)  NOT NULL,
    CONSTRAINT pk_specializations PRIMARY KEY (id),
    CONSTRAINT uq_specializations_name UNIQUE (name)
);

-- ---------------------------------------------------------------------------
-- lawyer_specializations  (@ManyToMany join table Lawyer <-> Specialization)
-- ---------------------------------------------------------------------------
CREATE TABLE lawyer_specializations (
    lawyer_id          uuid  NOT NULL,
    specialization_id  uuid  NOT NULL,
    CONSTRAINT pk_lawyer_specializations PRIMARY KEY (lawyer_id, specialization_id),
    CONSTRAINT fk_lawspec_lawyer FOREIGN KEY (lawyer_id) REFERENCES lawyers (id),
    CONSTRAINT fk_lawspec_specialization FOREIGN KEY (specialization_id) REFERENCES specializations (id)
);

-- ---------------------------------------------------------------------------
-- availabilities  (Availability) — recurring weekly consultation windows
-- ---------------------------------------------------------------------------
CREATE TABLE availabilities (
    id            uuid          NOT NULL,
    created_at    timestamp(6)  NOT NULL,
    updated_at    timestamp(6)  NOT NULL,
    lawyer_id     uuid          NOT NULL,
    day_of_week   varchar(255)  NOT NULL,
    start_time    time(6)       NOT NULL,
    end_time      time(6)       NOT NULL,
    is_available  boolean       NOT NULL,
    CONSTRAINT pk_availabilities PRIMARY KEY (id),
    CONSTRAINT fk_availabilities_lawyer FOREIGN KEY (lawyer_id) REFERENCES lawyers (id)
);

-- ---------------------------------------------------------------------------
-- appointments  (Appointment) — a client's consultation booking with a lawyer
-- ---------------------------------------------------------------------------
CREATE TABLE appointments (
    id                 uuid          NOT NULL,
    created_at         timestamp(6)  NOT NULL,
    updated_at         timestamp(6)  NOT NULL,
    client_id          uuid          NOT NULL,
    lawyer_id          uuid          NOT NULL,
    appointment_date   date          NOT NULL,
    appointment_time   time(6)       NOT NULL,
    consultation_mode  varchar(255)  NOT NULL,
    status             varchar(255)  NOT NULL,
    notes              varchar(2000),
    CONSTRAINT pk_appointments PRIMARY KEY (id),
    CONSTRAINT fk_appointments_client FOREIGN KEY (client_id) REFERENCES users (id),
    CONSTRAINT fk_appointments_lawyer FOREIGN KEY (lawyer_id) REFERENCES lawyers (id)
);

-- ---------------------------------------------------------------------------
-- reviews  (Review) — one rating/comment per completed appointment
-- ---------------------------------------------------------------------------
CREATE TABLE reviews (
    id              uuid          NOT NULL,
    created_at      timestamp(6)  NOT NULL,
    updated_at      timestamp(6)  NOT NULL,
    appointment_id  uuid          NOT NULL,
    client_id       uuid          NOT NULL,
    lawyer_id       uuid          NOT NULL,
    rating          integer       NOT NULL,
    comment         varchar(2000),
    CONSTRAINT pk_reviews PRIMARY KEY (id),
    CONSTRAINT uq_reviews_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_reviews_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_reviews_client FOREIGN KEY (client_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_lawyer FOREIGN KEY (lawyer_id) REFERENCES lawyers (id)
);
