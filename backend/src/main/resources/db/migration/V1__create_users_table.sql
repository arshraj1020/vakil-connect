CREATE TABLE users (

    id UUID PRIMARY KEY,

    created_at TIMESTAMP NOT NULL,

    updated_at TIMESTAMP NOT NULL,

    full_name VARCHAR(255) NOT NULL,

    email VARCHAR(255) UNIQUE NOT NULL,

    password VARCHAR(255) NOT NULL,

    phone_number VARCHAR(20) NOT NULL,

    enabled BOOLEAN NOT NULL

);