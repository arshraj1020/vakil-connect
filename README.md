<div align="center">

<!-- Animated Typing Header -->
<a href="#"><img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=700&size=32&pause=1000&color=D4AF37&center=true&vCenter=true&width=700&lines=%E2%9A%96%EF%B8%8F+VakilConnect;AI-Powered+Legal+Consultation;Find.+Book.+Consult.+Trust.;Building+in+Public+%E2%80%94+One+Commit+at+a+Time" alt="Typing SVG" />
</a>

<h3>AI-Powered Legal Consultation & Lawyer Discovery Platform</h3>

<p>A full-stack legal-tech platform that helps users discover verified lawyers, book consultations, securely manage legal documents, and receive AI-assisted legal guidance.</p>

<br/>

<!-- Tech Badges -->
<p>
 <img src="https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"/>
 <img src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"/>
 <img src="https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white" alt="Spring Security"/>
 <img src="https://img.shields.io/badge/PostgreSQL-Database-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"/>
 <img src="https://img.shields.io/badge/Next.js-Planned-000000?style=for-the-badge&logo=next.js&logoColor=white" alt="Next.js"/>
 <img src="https://img.shields.io/badge/FastAPI-Planned-009688?style=for-the-badge&logo=fastapi&logoColor=white" alt="FastAPI"/>
</p>

<!-- Status Badges -->
<p>
 <img src="https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge&labelColor=0d1117" alt="License"/>
 <img src="https://img.shields.io/badge/Status-Active%20Development-yellow?style=for-the-badge&labelColor=0d1117" alt="Status"/>
 <img src="https://img.shields.io/badge/Repository-Private-critical?style=for-the-badge&labelColor=0d1117" alt="Visibility"/>
 <img src="https://img.shields.io/badge/Backend-Core%20Complete-D4AF37?style=for-the-badge&labelColor=0d1117" alt="Backend"/>
</p>

<br/>

<!-- Hero Banner (SVG style) -->
<img src="https://capsule-render.vercel.app/api?type=waving&color=0:0d1117,100:D4AF37&height=200&section=header&text=VakilConnect&fontSize=60&fontColor=ffffff&animation=fadeIn&fontAlignY=38&desc=Connecting%20People%20With%20Trusted%20Legal%20Expertise&descAlignY=58&descSize=18" width="100%"/>

<br/>

<a href="#quick-start">Quick Start</a> •
<a href="#key-features">Features</a> •
<a href="#architecture-overview">Architecture</a> •
<a href="#development-roadmap">Roadmap</a> •
<a href="#author">Author</a>

</div>

<br/>

---

## Table of Contents

<details open>
<summary><b>Click to expand / collapse</b></summary>

- [Overview](#overview)
- [Current Progress](#current-progress)
- [Key Features](#key-features)
- [Architecture Overview](#architecture-overview)
- [Request Flow](#request-flow)
- [Tech Stack](#tech-stack)
- [Backend Architecture](#backend-architecture)
- [Planned Microservices](#planned-microservices)
- [Security Features](#security-features)
- [Project Structure](#project-structure)
- [API Documentation](#api-documentation)
- [Screenshots](#screenshots)
- [Quick Start](#quick-start)
- [Environment Variables](#environment-variables)
- [Development Roadmap](#development-roadmap)
- [Future Scope](#future-scope)
- [Contributing](#contributing)
- [License](#license)
- [Author](#author)

</details>

---

## Overview

> **VakilConnect** is a full-stack legal-tech platform engineered to simplify the process of finding, evaluating, and consulting lawyers. It connects clients with verified legal professionals, enables secure scheduling and document handling, and layers in AI-assisted guidance to help users understand legal matters before they ever step into a consultation.

The platform is being built using modern software engineering practices — clean architecture, layered backend design, secure-by-default authentication, role-based authorization, and clearly documented APIs — with the long-term goal of evolving into a scalable, microservices-based legal-tech ecosystem.

<div align="center">

> **Project status:** VakilConnect is a **private, personal project** under active development. The **core backend is functionally complete** — JWT authentication and role-based authorization, the lawyer, appointment, review, and admin modules, centralized exception handling, and OpenAPI docs are all implemented. The frontend, AI service, document storage, and infrastructure layers are planned in subsequent phases. This repository is **not intended for public use, deployment, or distribution.**

</div>

---

## Current Progress

<div align="center">

`Overall Backend: ▓▓▓▓▓▓▓▓░░ 80%`

</div>

A snapshot of what's actually working today in the backend:

- [x] Spring Boot project scaffolded with a clean layered architecture
- [x] PostgreSQL database integration
- [x] Database migration scripts for schema versioning (Flyway)
- [x] Spring Security configured with a stateless JWT filter chain
- [x] Password hashing using BCrypt
- [x] User Registration API (role-aware: CLIENT / LAWYER)
- [x] User Login API with signed JWT issuance
- [x] **JWT authorization filter enforcing protected routes**
- [x] **Role-based access control (CLIENT / LAWYER / ADMIN)**
- [x] **Lawyer module** — profile creation, public search & filtering, profile detail
- [x] **Appointment module** — book, cancel, history, accept, reject, complete
- [x] **Review module** — post reviews on completed appointments, auto rating aggregation
- [x] **Admin module** — verify lawyers, manage users, moderate reviews, platform analytics
- [x] **Centralized exception handling** with correct HTTP status codes (400/401/403/404/409)
- [x] Bean validation on request DTOs
- [x] Swagger / OpenAPI documentation integrated
- [x] DTO pattern for request/response contracts
- [x] Repository–Service–Controller layering throughout

<details>
<summary><b> Not Yet Implemented — Click to expand</b></summary>
<br/>

- [ ] Lawyer availability / scheduling slots
- [ ] Secure document upload & storage
- [ ] Notification system (email / in-app)
- [ ] AI service (recommendations, summarization, Q&A)
- [ ] Automated test suite (unit / integration / security)
- [ ] Frontend (Next.js)
- [ ] Docker & CI/CD

See the [Development Roadmap](#development-roadmap) for full sequencing.

</details>

---

## Key Features

> The features below describe the intended scope of the platform. Items already implemented are marked accordingly; everything else is planned and tracked in the [Development Roadmap](#development-roadmap).

<div align="center">

| For Clients | For Lawyers | For Admins | AI-Assisted *(Planned)* |
|:---|:---|:---|:---|
| Account registration & secure login ✅ | Registration & profile creation ✅ | Verify & approve lawyers ✅ | Conversational AI legal assistant |
| Search & discover lawyers ✅ | Credential / profile management ✅ | Manage client/lawyer accounts ✅ | Smart lawyer recommendation engine |
| Filter by specialization, city, fee, rating ✅ | Accept/reject appointment requests ✅ | Moderate reviews ✅ | Automated document summarization |
| Book & manage appointments ✅ | View & manage schedule ✅ | Platform-wide analytics ✅ | AI-driven legal Q&A |
| Review completed consultations ✅ | Mark consultations complete ✅ | Activate / deactivate accounts ✅ | Contextual legal guidance |
| View appointment history ✅ | | | |

</div>

<details>
<summary><b> Client Features — Click to expand</b></summary>
<br/>

- [x] Account registration and secure login
- [x] Search and discover lawyers by keyword
- [x] Filter lawyers by specialization, city, consultation fee, experience, and rating
- [x] Book and manage appointments (book, cancel, view history)
- [x] Leave reviews and ratings for completed consultations
- [ ] Upload and store legal documents securely

</details>

<details>
<summary><b> Lawyer Features — Click to expand</b></summary>
<br/>

- [x] Lawyer registration (role-aware signup)
- [x] Professional profile creation with specializations
- [x] View appointment schedule
- [x] Accept, reject, or complete incoming appointment requests
- [ ] Availability and scheduling configuration
- [ ] Access uploaded client documents before consultations

</details>

<details>
<summary><b> Admin Features — Click to expand</b></summary>
<br/>

- [x] Verify and approve pending lawyer registrations
- [x] Manage client and lawyer accounts (list, activate, deactivate)
- [x] Moderate and remove inappropriate reviews (with rating recalculation)
- [x] Access platform-wide analytics (users, lawyers, appointments, reviews)

</details>

<details>
<summary><b> AI-Assisted Capabilities — Click to expand</b></summary>
<br/>

- [ ] Conversational AI legal assistant for preliminary guidance
- [ ] Smart lawyer recommendation engine based on case type
- [ ] Automated legal document summarization
- [ ] AI-driven legal Q&A
- [ ] Contextual legal guidance to help users prepare for consultations

</details>

---

## Architecture Overview

VakilConnect follows a modular, service-oriented architecture designed to separate concerns across distinct layers — making the system easier to scale, test, and maintain independently.

```mermaid
flowchart TD
 A[" Next.js Frontend<br/>Client / Lawyer / Admin Dashboards<br/><i>(Planned)</i>"] -->|REST / HTTPS| B[" Spring Boot API<br/>Auth · RBAC · Lawyers · Appointments · Reviews · Admin<br/><i>(Core Complete)</i>"]
 B --> C[" PostgreSQL DB<br/>Primary Store<br/><i>(Implemented)</i>"]
 B --> D[" FastAPI AI Layer<br/>Recommendations · Summarization<br/><i>(Planned)</i>"]
 B --> E[" AWS S3<br/>Document Storage<br/><i>(Planned)</i>"]

 style A fill:#0d1117,stroke:#666,stroke-width:2px,color:#fff,stroke-dasharray: 5 5
 style B fill:#0d1117,stroke:#D4AF37,stroke-width:3px,color:#fff
 style C fill:#0d1117,stroke:#D4AF37,stroke-width:3px,color:#fff
 style D fill:#0d1117,stroke:#666,stroke-width:2px,color:#fff,stroke-dasharray: 5 5
 style E fill:#0d1117,stroke:#666,stroke-width:2px,color:#fff,stroke-dasharray: 5 5
```

<div align="center">

**Gold, solid border** = Implemented &nbsp;&nbsp;|&nbsp;&nbsp; **Grey, dashed border** = Planned

</div>

> This diagram represents the target end-state architecture. Currently, the **Spring Boot API** (auth, authorization, lawyers, appointments, reviews, admin) and **PostgreSQL** layers are implemented; the frontend, AI layer, and document storage are planned.

---

## Request Flow

```mermaid
sequenceDiagram
 actor User
 participant API as Spring Boot API
 participant Sec as Spring Security
 participant JWT as JWT Filter
 participant DB as PostgreSQL

 User->>API: POST /api/auth/register
 API->>Sec: Hash password (BCrypt)
 Sec->>DB: Persist user record
 DB-->>API: User created
 API-->>User: 201 Created

 User->>API: POST /api/auth/login
 API->>DB: Validate credentials
 DB-->>API: Credentials valid
 API->>Sec: Generate JWT
 Sec-->>API: Signed token
 API-->>User: 200 OK + JWT

 User->>API: GET /api/client/appointments (Bearer token)
 API->>JWT: Validate & parse token
 JWT->>Sec: Set authentication + authorities
 Sec->>Sec: Authorize by role (CLIENT)
 Sec->>DB: Fetch data
 DB-->>API: Result
 API-->>User: 200 OK

 Note over User,API: Protected routes are enforced by the JWT<br/>filter and role-based access control.
```

---

## Tech Stack

<div align="center">

![Java](https://skillicons.dev/icons?i=java,spring,postgres)
&nbsp;&nbsp;
![Frontend](https://skillicons.dev/icons?i=nextjs,ts,fastapi)
&nbsp;&nbsp;
![Deploy](https://skillicons.dev/icons?i=docker,aws,git)

</div>

<div align="center">

| Layer | Technology | Status |
|:---|:---|:---:|
| **Frontend** | Next.js + TypeScript | Planned |
| **Backend** | Spring Boot 3.5 (Java 21) | Core Complete |
| **Database** | PostgreSQL | Implemented |
| **DB Migrations** | Flyway | Implemented |
| **Authentication** | Spring Security + JWT | Implemented |
| **Authorization** | JWT Filter + Role-Based Access Control | Implemented |
| **Validation & Errors** | Jakarta Validation + Global Exception Handler | Implemented |
| **API Documentation** | Swagger / OpenAPI (springdoc) | Implemented |
| **AI Service** | FastAPI + LangChain | Planned |
| **File Storage** | AWS S3 | Planned |
| **Containerization** | Docker | Planned |
| **CI/CD** | GitHub Actions (or equivalent) | Planned |
| **Version Control** | Git + GitHub | Active |

</div>

---

## Backend Architecture

The backend is built on **Spring Boot** following a layered architecture pattern that enforces clear separation of concerns:

```mermaid
flowchart LR
 A[" Controller Layer<br/>HTTP requests & routing"] --> B[" Service Layer<br/>Core business logic"]
 B --> C[" Repository Layer<br/>Spring Data JPA"]
 C --> D[" Entity / Model<br/>Domain objects"]
 B --> E[" DTO Layer<br/>API contracts"]
 A --> F[" Security Layer<br/>Spring Security + JWT"]
 C --> G[" Migration Layer<br/>Flyway"]
 A --> H[" Exception Layer<br/>Centralized handling"]
 A --> I[" Config Layer<br/>App / Security / Swagger"]

 style A fill:#0d1117,stroke:#D4AF37,color:#fff
 style B fill:#0d1117,stroke:#D4AF37,color:#fff
 style C fill:#0d1117,stroke:#D4AF37,color:#fff
 style D fill:#0d1117,stroke:#D4AF37,color:#fff
 style E fill:#0d1117,stroke:#D4AF37,color:#fff
 style F fill:#0d1117,stroke:#D4AF37,color:#fff
 style G fill:#0d1117,stroke:#D4AF37,color:#fff
 style H fill:#0d1117,stroke:#D4AF37,color:#fff
 style I fill:#0d1117,stroke:#D4AF37,color:#fff
```

The codebase is organized by **feature module** (`auth`, `user`, `lawyer`, `appointment`, `review`, `admin`, `security`, `common`), each with its own controllers, services, repositories, DTOs, and entities. This keeps business logic isolated from infrastructure concerns and makes the codebase easy to test, extend, and eventually decompose into independent microservices.

---

## Planned Microservices

As the platform matures, the modular Spring Boot backend is intended to evolve into a set of focused, independently deployable services:

<div align="center">

| Service | Responsibility | Current State |
|:---|:---|:---:|
| **Auth Service** | Authentication, JWT issuance, role-based access control | Implemented (in monolith) |
| **User Service** | Client and lawyer profile management | Implemented (in monolith) |
| **Appointment Service** | Booking, scheduling, and lifecycle management | Implemented (in monolith) |
| **Review Service** | Ratings, feedback, and reputation management | Implemented (in monolith) |
| **Admin Service** | Verification, moderation, and analytics | Implemented (in monolith) |
| **Document Service** | Secure document upload, storage, retrieval via AWS S3 | Planned |
| **AI Service** | FastAPI-based recommendations, summarization, legal Q&A | Planned |
| **Notification Service** | Email and in-app notifications | Planned |

</div>

> The platform begins as a well-structured modular monolith and will be decomposed incrementally as features stabilize.

---

## Security Features

Security is treated as a first-class concern throughout the platform's design:

- [x] **Spring Security** with a stateless (`SessionCreationPolicy.STATELESS`) filter chain
- [x] **BCrypt password hashing** for all stored user credentials
- [x] **JWT token generation** issued on successful login
- [x] **JWT authorization filter** validating and parsing tokens on every protected request
- [x] **Role-based access control** distinguishing CLIENT, LAWYER, and ADMIN permissions
- [x] **Account activation gate** — deactivated users are rejected at authentication
- [x] **Input validation** on request DTOs via Jakarta Bean Validation
- [x] **Centralized exception handling** returning correct status codes without leaking internals (no stack traces, SQL, or Hibernate details)
- [x] **Structured 401 / 403 responses** via custom `AuthenticationEntryPoint` and `AccessDeniedHandler`
- [ ] **HTTPS-only communication** in production deployments
- [ ] **CORS policy** for the browser frontend
- [ ] **Externalized secrets** (JWT key / DB credentials via environment)
- [ ] **Secure document storage** via AWS S3 with access-controlled URLs

---

## Project Structure

```text
vakil-connect/
├── backend/                         # Spring Boot application
│  ├── src/main/java/com/arshraj/vakilconnect/
│  │  ├── auth/                       # Registration, login, JWT issuance
│  │  ├── user/                       # User entity, current-user endpoint
│  │  ├── lawyer/                     # Lawyer profiles, search, specializations
│  │  ├── appointment/                # Booking lifecycle
│  │  ├── review/                     # Ratings & reviews
│  │  ├── admin/                      # Verification, moderation, analytics
│  │  ├── security/                   # JWT filter, handlers, user details
│  │  ├── common/                     # Base entity, exceptions, shared code
│  │  └── config/                     # Security & Swagger configuration
│  ├── src/main/resources/
│  │  ├── application.yaml            # Application configuration
│  │  └── db/migration/               # Flyway migration scripts
│  └── pom.xml
│
├── frontend/                        # Next.js application (planned)
├── ai-service/                      # FastAPI AI microservice (planned)
├── database/                        # Schema design & ER diagrams
├── docs/                            # Requirements, use cases, DB design
└── README.md
```

---

## API Documentation

API documentation is generated using **Swagger / OpenAPI** (springdoc) and is integrated directly into the Spring Boot backend.

- Interactive Swagger UI is available at `/swagger-ui/index.html` when the backend is run locally
- The OpenAPI spec is served at `/v3/api-docs`

<div align="center">

### Implemented Endpoints

| Endpoint | Method | Access | Description |
|:---|:---:|:---:|:---|
| `/api/auth/register` | `POST` | Public | Register a new CLIENT or LAWYER (BCrypt-hashed) |
| `/api/auth/login` | `POST` | Public | Authenticate and receive a signed JWT |
| `/api/users/me` | `GET` | Any auth | Current authenticated user profile |
| `/api/lawyers` | `GET` | Public | Search & filter verified lawyers (paged) |
| `/api/lawyers/{id}` | `GET` | Public | Lawyer profile detail |
| `/api/lawyers/{id}/reviews` | `GET` | Public | Paged reviews for a lawyer |
| `/api/lawyer/profile` | `POST` | LAWYER | Create the authenticated lawyer's profile |
| `/api/lawyer/appointments` | `GET` | LAWYER | View appointment schedule |
| `/api/lawyer/appointments/{id}/accept` | `PUT` | LAWYER | Accept a pending appointment |
| `/api/lawyer/appointments/{id}/reject` | `PUT` | LAWYER | Reject a pending appointment |
| `/api/lawyer/appointments/{id}/complete` | `PUT` | LAWYER | Mark an accepted appointment complete |
| `/api/client/appointments` | `POST` | CLIENT | Book an appointment |
| `/api/client/appointments` | `GET` | CLIENT | View appointment history |
| `/api/client/appointments/{id}/cancel` | `PUT` | CLIENT | Cancel an appointment |
| `/api/client/appointments/{id}/review` | `POST` | CLIENT | Review a completed appointment |
| `/api/admin/lawyers/pending` | `GET` | ADMIN | List lawyers awaiting verification |
| `/api/admin/lawyers/{id}/verify` | `PUT` | ADMIN | Verify a lawyer |
| `/api/admin/users` | `GET` | ADMIN | List users (optional role filter) |
| `/api/admin/users/{id}/activate` | `PUT` | ADMIN | Activate a user account |
| `/api/admin/users/{id}/deactivate` | `PUT` | ADMIN | Deactivate a user account |
| `/api/admin/reviews` | `GET` | ADMIN | List reviews for moderation |
| `/api/admin/reviews/{id}` | `DELETE` | ADMIN | Remove a review (recomputes rating) |
| `/api/admin/analytics` | `GET` | ADMIN | Platform-wide statistics |

</div>

<details>
<summary><b> Planned Endpoints — Click to expand</b></summary>
<br/>

| Endpoint | Method | Description |
|:---|:---:|:---|
| `/api/lawyer/availability` | `POST` | Configure consultation availability slots |
| `/api/documents` | `POST` | Upload a legal document |
| `/api/documents/{id}` | `GET` | Retrieve a stored document |
| `/api/notifications` | `GET` | Fetch user notifications |
| `/api/ai/recommend` | `POST` | AI lawyer recommendation |
| `/api/ai/summarize` | `POST` | AI document summarization |

</details>

---

## Screenshots

<div align="center">

![Screenshots Placeholder](https://via.placeholder.com/800x400/0d1117/D4AF37?text=%F0%9F%96%A5%EF%B8%8F+UI+Coming+Soon)

*Application screenshots and UI walkthroughs will be added here once the Next.js frontend reaches a demonstrable state. The backend can be explored today via Swagger UI.*

</div>

**Coming soon:**
- [ ] Client dashboard preview
- [ ] Lawyer profile and availability view
- [ ] Appointment booking flow
- [ ] Admin analytics panel

---

## Quick Start

### Prerequisites

<div align="center">

| Requirement | Minimum Version |
|:---|:---:|
| Java (JDK) | 21+ |
| Maven | 3.8+ |
| PostgreSQL | 14+ |
| Git | Latest |

</div>

### Backend Setup

```bash
git clone <repository-url>
cd vakil-connect/backend

# Create the database and configure credentials in application.yaml
# (see Environment Variables section below)
createdb vakilconnect

./mvnw clean install
```

### ▶ Running the Project

<details open>
<summary><b>Run the Backend</b></summary>

```bash
cd backend
./mvnw spring-boot:run
```

The API server will start on:

```text
http://localhost:8080
```

Swagger documentation is available at:

```text
http://localhost:8080/swagger-ui/index.html
```

> An admin account cannot be created through public registration by design. To create one, register a user and promote it directly in the database:
> `UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';`

</details>

<details>
<summary><b>Run the Frontend — Planned</b></summary>

```bash
cd frontend
npm install
npm run dev
```

</details>

<details>
<summary><b>Run the AI Service — Planned</b></summary>

```bash
cd ai-service
pip install -r requirements.txt
uvicorn app.main:app --reload
```

</details>

---

## Environment Variables

Configure `application.yaml` using the template below. **Never commit real credentials to version control.**

<details open>
<summary><b>Backend (application.yaml)</b></summary>

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/vakilconnect
    username: your_db_username
    password: your_db_password

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    open-in-view: false

jwt:
  secret: your_base64_encoded_secret
  expiration: 86400000

server:
  port: 8080
```

> **Note:** The current dev configuration uses Hibernate `ddl-auto: update`. Migration to Flyway-managed schema with `ddl-auto: validate` is planned for production hardening.

</details>

<details>
<summary><b>Frontend (.env.local) — Planned</b></summary>

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_APP_ENV=development
```

</details>

<details>
<summary><b>AI Service (.env) — Planned</b></summary>

```env
OPENAI_API_KEY=your_api_key
DATABASE_URL=postgresql://user:password@localhost:5432/vakilconnect
```

</details>

---

## Development Roadmap

The project is being developed in clearly scoped phases to ensure a stable foundation before layering on additional complexity.

<div align="center">

### Phase 1 — Foundation
`Progress: ▓▓▓▓▓▓▓▓▓▓ 100%`

</div>

- [x] Project setup and repository structure
- [x] Database schema design
- [x] Core backend architecture (layered Spring Boot setup)
- [x] Database migration setup

<div align="center">

### Phase 2 — Core Backend Functionality
`Progress: ▓▓▓▓▓▓▓▓▓░ 90%`

</div>

- [x] Spring Security configuration
- [x] Password hashing with BCrypt
- [x] User Registration API (role-aware)
- [x] User Login API
- [x] JWT token generation on login
- [x] JWT authorization filter for protected routes
- [x] Role-based access control (Client / Lawyer / Admin)
- [x] Lawyer registration and profile management
- [x] Lawyer search & filtering
- [x] Appointment booking and lifecycle system
- [x] Centralized exception handling & structured errors
- [ ] Lawyer availability / scheduling slots

<div align="center">

### Phase 3 — Platform Features
`Progress: ▓▓▓▓▓░░░░░ 50%`

</div>

- [x] Reviews and ratings module
- [x] Admin verification and moderation tools
- [x] Platform analytics
- [ ] Notification system (email / in-app)
- [ ] Secure document upload and management

<div align="center">

### Phase 4 — AI Integration
`Progress: ░░░░░░░░░░ 0%`

</div>

- [ ] FastAPI AI service scaffolding
- [ ] AI-powered lawyer recommendation engine
- [ ] AI legal assistant for preliminary Q&A
- [ ] Document summarization pipeline

<div align="center">

### Phase 5 — Frontend Development
`Progress: ░░░░░░░░░░ 0%`

</div>

- [ ] Next.js project setup with TypeScript
- [ ] Client-facing dashboard and search experience
- [ ] Lawyer dashboard and availability management
- [ ] Admin analytics dashboard

<div align="center">

### Phase 6 — Infrastructure & Deployment
`Progress: ░░░░░░░░░░ 0%`

</div>

- [ ] Unit and integration testing across services
- [ ] Dockerization of backend, frontend, and AI service
- [ ] CI/CD pipeline setup
- [ ] AWS S3 integration for document storage
- [ ] Production cloud deployment

---

## Future Scope

Beyond the current roadmap, the following directions are being considered as the platform matures:

- Decomposition of the backend into independently deployable microservices
- Real-time chat between clients and lawyers
- Video consultation integration
- Multi-language support for regional accessibility
- Payment gateway integration for paid consultations
- Advanced analytics and reporting dashboards for lawyers and admins
- Mobile application (React Native or Flutter)

These are exploratory goals and will be prioritized based on platform maturity and user feedback.

---

## Contributing

<div align="center">

**This is a private, personal project and is not open to external contributions.**

The repository is maintained solely by the author for personal learning, portfolio, and development purposes, and no contribution workflow (issues, pull requests, or forks) is currently accepted.

</div>

---

## License

<div align="center">

**Copyright © 2026 Arsh Raj. All rights reserved.**

<img src="https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge&labelColor=0d1117" alt="Proprietary License"/>

</div>

This is a **private, closed-source project**. It is not licensed for public, third-party, or commercial use. No part of this repository — including its source code, architecture, or documentation — may be copied, modified, redistributed, deployed, or used in any form without prior written permission from the author. This repository is maintained for personal and portfolio purposes only.

---

## Author

<div align="center">

### Arsh Raj

Building VakilConnect as a personal production-grade engineering project, with a focus on clean architecture, scalable backend design, and thoughtful AI integration.

<br/>

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:D4AF37,100:0d1117&height=120&section=footer" width="100%"/>

### VakilConnect — Connecting people with trusted legal expertise.

</div>