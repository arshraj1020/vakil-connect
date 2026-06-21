# 8. System Entities

## 8.1 Overview

This document describes the core entities used in VakilConnect. These entities form the foundation of the relational database and represent the primary objects managed by the platform.

---

# 8.2 User

The User entity stores authentication and basic profile information for every person using the system.

### Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| id | UUID | Unique identifier |
| full_name | String | User's full name |
| email | String | Unique email address |
| password_hash | String | Encrypted password |
| phone_number | String | Contact number |
| role | Enum | CLIENT / LAWYER / ADMIN |
| is_email_verified | Boolean | Email verification status |
| created_at | Timestamp | Record creation time |
| updated_at | Timestamp | Last update time |

---

# 8.3 ClientProfile

Stores client-specific information.

### Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| id | UUID | Unique identifier |
| user_id | UUID | References User |
| date_of_birth | Date | Date of birth |
| gender | Enum | Gender |
| address | String | Residential address |

---

# 8.4 LawyerProfile

Stores professional information related to lawyers.

### Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| id | UUID | Unique identifier |
| user_id | UUID | References User |
| bar_council_number | String | Professional registration number |
| years_of_experience | Integer | Experience in years |
| consultation_fee | Decimal | Consultation fee |
| bio | Text | Professional biography |
| office_address | String | Office location |
| profile_photo_url | String | Profile image |
| is_verified | Boolean | Verification status |
| average_rating | Decimal | Average client rating |

---

# 8.5 Specialization

Represents legal practice areas.

Examples:

- Criminal Law
- Civil Law
- Family Law
- Corporate Law
- Cyber Law
- Property Law

### Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| id | UUID | Unique identifier |
| name | String | Specialization name |

---

# 8.6 LawyerSpecialization

Acts as a junction table between lawyers and specializations.

### Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| lawyer_id | UUID | References LawyerProfile |
| specialization_id | UUID | References Specialization |

---

# 8.7 Appointment

Represents scheduled consultations.

### Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| id | UUID | Unique identifier |
| client_id | UUID | References ClientProfile |
| lawyer_id | UUID | References LawyerProfile |
| appointment_date | Date | Consultation date |
| appointment_time | Time | Consultation time |
| consultation_mode | Enum | ONLINE / OFFLINE |
| status | Enum | PENDING / ACCEPTED / REJECTED / COMPLETED / CANCELLED |
| notes | Text | Additional notes |

---

# 8.8 Availability

Stores recurring consultation schedules for lawyers.

### Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| id | UUID | Unique identifier |
| lawyer_id | UUID | References LawyerProfile |
| day_of_week | Enum | Monday–Sunday |
| start_time | Time | Start time |
| end_time | Time | End time |
| is_available | Boolean | Availability status |

---

# 8.9 Review

Stores client feedback after completed consultations.

### Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| id | UUID | Unique identifier |
| appointment_id | UUID | References Appointment |
| client_id | UUID | References ClientProfile |
| lawyer_id | UUID | References LawyerProfile |
| rating | Integer | Rating (1–5) |
| comment | Text | Review comment |
| created_at | Timestamp | Review date |

---

# 8.10 Document

Stores metadata for uploaded legal documents.

### Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| id | UUID | Unique identifier |
| appointment_id | UUID | References Appointment |
| uploaded_by | UUID | References User |
| file_name | String | File name |
| file_url | String | Cloud storage URL |
| document_type | String | Document category |
| uploaded_at | Timestamp | Upload time |

---

# 8.11 Notification

Stores notifications sent to users.

### Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| id | UUID | Unique identifier |
| user_id | UUID | References User |
| title | String | Notification title |
| message | Text | Notification message |
| type | String | Notification type |
| is_read | Boolean | Read status |
| created_at | Timestamp | Creation time |