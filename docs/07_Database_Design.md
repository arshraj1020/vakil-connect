# 7. Database Design

## Core Entities

- User
- ClientProfile
- LawyerProfile
- Appointment
- Availability
- Review
- Document
- Notification

## Design Decisions

### User
Stores authentication and basic profile information.

### ClientProfile
Stores client-specific information.

### LawyerProfile
Stores lawyer-specific professional information.

### Appointment
Represents consultations between clients and lawyers.

### Availability
Defines recurring consultation slots for lawyers.

### Review
Stores client feedback after completed appointments.

### Document
Stores metadata of uploaded legal documents.

### Notification
Stores in-app notification records.
