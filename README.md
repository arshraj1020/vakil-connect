<div align="center">

<!-- Animated Typing Header -->
<a href="#"><img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=700&size=32&pause=1000&color=D4AF37&center=true&vCenter=true&width=700&lines=%E2%9A%96%EF%B8%8F+VakilConnect;AI-Powered+Legal+Consultation;Find.+Book.+Consult.+Trust.;Building+in+Public+%E2%80%94+One+Commit+at+a+Time" alt="Typing SVG" />
</a>

<h3>AI-Powered Legal Consultation & Lawyer Discovery Platform</h3>

<p>A full-stack legal-tech platform that helps users discover verified lawyers, book consultations, securely manage legal documents, and receive AI-assisted legal guidance.</p>

<br/>

<!-- Tech Badges -->
<p>
 <img src="https://img.shields.io/badge/Java-17-007396?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"/>
 <img src="https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"/>
 <img src="https://img.shields.io/badge/PostgreSQL-Database-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"/>
 <img src="https://img.shields.io/badge/Next.js-Planned-000000?style=for-the-badge&logo=next.js&logoColor=white" alt="Next.js"/>
 <img src="https://img.shields.io/badge/FastAPI-Planned-009688?style=for-the-badge&logo=fastapi&logoColor=white" alt="FastAPI"/>
 <img src="https://img.shields.io/badge/Docker-Planned-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker"/>
</p>

<!-- Status Badges -->
<p>
 <img src="https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge&labelColor=0d1117" alt="License"/>
 <img src="https://img.shields.io/badge/Status-Active%20Development-yellow?style=for-the-badge&labelColor=0d1117" alt="Status"/>
 <img src="https://img.shields.io/badge/Repository-Private-critical?style=for-the-badge&labelColor=0d1117" alt="Visibility"/>
 <img src="https://img.shields.io/badge/Last%20Commit-Active-D4AF37?style=for-the-badge&labelColor=0d1117" alt="Last Commit"/>
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

The platform is being built using modern software engineering practices — clean architecture, layered backend design, secure-by-default authentication, and clearly documented APIs — with the long-term goal of evolving into a scalable, microservices-based legal-tech ecosystem.

<div align="center">

> **Project status:** VakilConnect is a **private, personal project** under active development. The backend foundation (Spring Boot, PostgreSQL, Flyway, Spring Security) is being built first, with authorization, lawyer/appointment modules, the frontend, AI service, and infrastructure layers planned in subsequent phases. This repository is **not intended for public use, deployment, or distribution.**

</div>

---

## Current Progress

<div align="center">

`Overall Backend Foundation: ▓▓▓▓░░░░░░ 40%`

</div>

A snapshot of what's actually working today in the backend:

- [x] Spring Boot project scaffolded with a clean layered architecture
- [x] PostgreSQL database integration
- [x] Flyway-managed database migrations for schema versioning
- [x] Spring Security configured for the application
- [x] Password hashing using BCrypt
- [x] User Registration API
- [x] User Login API
- [x] JWT token generation on successful authentication
- [x] Swagger / OpenAPI documentation integrated
- [x] DTO pattern for request/response contracts
- [x] Repository–Service–Controller layering in place

<details>
<summary><b> Not Yet Implemented — Click to expand</b></summary>
<br/>

- [] JWT authorization filter (route protection)
- [] Role-based access control
- [] Lawyer module
- [] Appointment module
- [] Document upload
- [] AI service
- [] Frontend
- [] Docker
- [] CI/CD

See the [Development Roadmap](#development-roadmap) for full sequencing.

</details>

---

## Key Features

> The features below describe the intended scope of the platform. Items already implemented are marked accordingly; everything else is planned and tracked in the [Development Roadmap](#development-roadmap).

<div align="center">

| For Clients | For Lawyers | For Admins | AI-Assisted *(Planned)* |
|:---|:---|:---|:---|
| Account registration & secure login | Registration & profile verification | Verify & approve lawyer registrations | Conversational AI legal assistant |
| Search & discover lawyers | Profile & credential management | Manage client/lawyer accounts | Smart lawyer recommendation engine |
| Filter by specialization & availability | Availability & scheduling config | Platform-wide analytics | Automated document summarization |
| Book & manage appointments | Accept/decline appointment requests | Content & review moderation | AI-driven legal Q&A |
| Upload & store legal documents | Manage active/past consultations | | Contextual legal guidance |
| View appointment history | | | |
| Leave reviews & ratings | | | |

</div>

<details>
<summary><b> Client Features — Click to expand</b></summary>
<br/>

- [x] Account registration and secure login
- [] Search and discover lawyers by specialization, location, and rating
- [] Filter lawyers by practice area and availability
- [] Book and manage appointments
- [] Upload and store legal documents securely
- [] View complete appointment history
- [] Leave reviews and ratings for consultations

</details>

<details>
<summary><b> Lawyer Features — Click to expand</b></summary>
<br/>

- [] Lawyer registration and profile verification
- [] Profile and credential management
- [] Availability and scheduling configuration
- [] Accept or decline incoming appointment requests
- [] Manage active and past consultations

</details>

<details>
<summary><b> Admin Features — Click to expand</b></summary>
<br/>

- [] Verify and approve lawyer registrations
- [] Manage client and lawyer accounts
- [] Access platform-wide analytics and reporting
- [] Moderate content and reviews

</details>

<details>
<summary><b> AI-Assisted Capabilities — Click to expand</b></summary>
<br/>

- [] Conversational AI legal assistant for preliminary guidance
- [] Smart lawyer recommendation engine based on case type
- [] Automated legal document summarization
- [] AI-driven legal Q&A
- [] Contextual legal guidance to help users prepare for consultations

</details>

---

## Architecture Overview

VakilConnect follows a modular, service-oriented architecture designed to separate concerns across distinct layers — making the system easier to scale, test, and maintain independently.

```mermaid
flowchart TD
 A[" Next.js Frontend<br/>Client / Lawyer / Admin Dashboards<br/><i>(Planned)</i>"] -->|REST / HTTPS| B[" Spring Boot API<br/>Auth · Bookings · Profiles · Reviews<br/><i>(In Development)</i>"]
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

> This diagram represents the target end-state architecture. Currently, only the **Spring Boot API** and **PostgreSQL** layers are implemented; the frontend, AI layer, and document storage are planned. Detailed architecture diagrams (component, sequence, and deployment views) will be added as each service layer is implemented.

---

## Request Flow

```mermaid
sequenceDiagram
 actor User
 participant API as Spring Boot API
 participant Sec as Spring Security
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

 Note over User,API: Protected routes not yet enforced —<br/>JWT authorization filter in development
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
| **Backend** | Spring Boot (Java) | In Development |
| **Database** | PostgreSQL | Implemented |
| **DB Migrations** | Flyway | Implemented |
| **Authentication** | Spring Security + JWT (token generation) | In Development |
| **Authorization** | JWT Filter + Role-Based Access Control | Planned |
| **API Documentation** | Swagger / OpenAPI | Implemented |
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

This structure keeps business logic isolated from infrastructure concerns, making the codebase easier to test, extend, and eventually decompose into independent microservices.

---

## Planned Microservices

As the platform matures, the monolithic Spring Boot backend is intended to evolve into a set of focused, independently deployable services:

<div align="center">

| Service | Responsibility |
|:---|:---|
| **Auth Service** | User authentication, JWT issuance, and role-based access control |
| **User Service** | Client and lawyer profile management |
| **Appointment Service** | Booking, scheduling, and availability management |
| **Document Service** | Secure document upload, storage, and retrieval via AWS S3 |
| **Review Service** | Ratings, feedback, and reputation management |
| **AI Service** | FastAPI-based service for recommendations, summarization, and legal Q&A |
| **Notification Service** | Email and in-app notifications for bookings and updates |

</div>

> This is a **forward-looking design goal**, not a current implementation. The platform begins as a well-structured monolith and will be decomposed incrementally as features stabilize.

---

## Security Features

Security is treated as a first-class concern throughout the platform's design:

- [x] **Spring Security** configured for authentication
- [x] **BCrypt password hashing** for all stored user credentials
- [x] **JWT token generation** issued on successful login for stateless authentication
- [] **JWT authorization filter** to protect routes using issued tokens
- [] **Role-based access control** distinguishing Client, Lawyer, and Admin permissions
- [~] **Input validation** at the controller and service layers *(in progress)*
- [~] **Centralized exception handling** to avoid leaking internal implementation details *(in progress)*
- [] **HTTPS-only communication** in production deployments
- [] **Secure document storage** via AWS S3 with access-controlled URLs

---

## Project Structure

```text
vakil-connect/
├── backend/ # Spring Boot application
│ ├── src/main/java/
│ │ ├── controller/ # REST API endpoints
│ │ ├── service/ # Business logic
│ │ ├── repository/ # Spring Data JPA repositories
│ │ ├── entity/ # JPA entities / domain models
│ │ ├── dto/ # Data Transfer Objects
│ │ ├── security/ # Spring Security & JWT config
│ │ ├── exception/ # Custom exceptions & handlers
│ │ └── config/ # App, Swagger, and bean configuration
│ ├── src/main/resources/
│ │ ├── application.yml # Application configuration
│ │ └── db/migration/ # Flyway database migration scripts
│ └── pom.xml
│
├── frontend/ # Next.js application (planned)
│ ├── app/
│ ├── components/
│ └── lib/
│
├── ai-service/ # FastAPI AI microservice (planned)
│ ├── app/
│ ├── models/
│ └── requirements.txt
│
├── database/ # Schema design & ER diagrams
│
├── docker/ # Docker & Compose configurations (planned)
│
├── docs/ # Architecture docs, API specs, diagrams
│
├── assets/ # Images, diagrams, branding assets
│
└── README.md
```

---

## API Documentation

API documentation is generated using **Swagger / OpenAPI** and is integrated directly into the Spring Boot backend.

- Interactive Swagger UI is available at `/swagger-ui.html` when the backend is run locally
- A full hosted API reference (Postman collection / OpenAPI spec) is **coming soon**

<div align="center">

### Currently Available Endpoints

| Endpoint | Method | Description | Status |
|:---|:---:|:---|:---:|
| `/api/auth/register` | `POST` | Registers a new user with hashed (BCrypt) password storage | Implemented |
| `/api/auth/login` | `POST` | Authenticates a user and returns a signed JWT access token | Implemented |

</div>

<details>
<summary><b> Planned Endpoints — Click to expand</b></summary>
<br/>

| Endpoint | Method | Description |
|:---|:---:|:---|
| `/api/lawyers` | `GET` | List/search verified lawyers |
| `/api/lawyers/{id}` | `GET` | Get lawyer profile detail |
| `/api/appointments` | `POST` | Book a new appointment |
| `/api/appointments/{id}` | `PATCH` | Update/cancel an appointment |
| `/api/documents` | `POST` | Upload a legal document |
| `/api/reviews` | `POST` | Submit a review/rating |

</details>

> Note: Issued JWTs are **not yet enforced** on protected routes, since the authorization filter is still in development.

---

## Screenshots

<div align="center">

![Screenshots Placeholder](https://via.placeholder.com/800x400/0d1117/D4AF37?text=%F0%9F%96%A5%EF%B8%8F+UI+Coming+Soon)

*Application screenshots and UI walkthroughs will be added here once the Next.js frontend reaches a demonstrable state.*

</div>

**Coming soon:**
- [] Client dashboard preview
- [] Lawyer profile and availability view
- [] Appointment booking flow
- [] Admin analytics panel

---

## Quick Start

### Prerequisites

<div align="center">

| Requirement | Minimum Version |
|:---|:---:|
| Java (JDK) | 17+ |
| Maven | 3.8+ |
| PostgreSQL | 14+ |
| Git | Latest |

</div>

### Backend Setup

```bash
git clone <repository-url>
cd vakil-connect/backend

# Configure database credentials in application.yml
# (see Environment Variables section below)

mvn clean install
```

Flyway will automatically run pending migrations against the configured database on application startup.

### ▶ Running the Project

<details open>
<summary><b>Run the Backend</b></summary>

```bash
cd backend
mvn spring-boot:run
```

The API server will start on:

```text
http://localhost:8080
```

Swagger documentation is available at:

```text
http://localhost:8080/swagger-ui.html
```

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

Create an `application.yml` (or `.env` for frontend/AI service) using the template below. **Never commit real credentials to version control.**

<details open>
<summary><b>Backend (application.yml)</b></summary>

```yaml
spring:
 datasource:
 url: jdbc:postgresql://localhost:5432/vakilconnect
 username: your_db_username
 password: your_db_password

 flyway:
 enabled: true
 locations: classpath:db/migration

 jpa:
 hibernate:
 ddl-auto: validate
 show-sql: true

jwt:
 secret: your_jwt_secret_key
 expiration: 86400000

server:
 port: 8080
```

> `ddl-auto` is set to `validate` (rather than `update`) since schema changes are now managed through Flyway migrations.

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
- [x] Flyway migration setup

<div align="center">

### Phase 2 — Core Backend Functionality
`Progress: ▓▓▓▓▓▓░░░░ 60%`

</div>

- [x] Spring Security configuration
- [x] Password hashing with BCrypt
- [x] User Registration API
- [x] User Login API
- [x] JWT token generation on login
- [] JWT authorization filter for protected routes
- [] Role-based access control (Client / Lawyer / Admin)
- [] Lawyer registration and profile management
- [] Appointment booking and scheduling system

<div align="center">

### Phase 3 — Platform Features
`Progress: ░░░░░░░░░░ 0%`

</div>

- [] Reviews and ratings module
- [] Notification system (email / in-app)
- [] Secure document upload and management
- [] Admin verification and moderation tools

<div align="center">

### Phase 4 — AI Integration
`Progress: ░░░░░░░░░░ 0%`

</div>

- [] FastAPI AI service scaffolding
- [] AI-powered lawyer recommendation engine
- [] AI legal assistant for preliminary Q&A
- [] Document summarization pipeline

<div align="center">

### Phase 5 — Frontend Development
`Progress: ░░░░░░░░░░ 0%`

</div>

- [] Next.js project setup with TypeScript
- [] Client-facing dashboard and search experience
- [] Lawyer dashboard and availability management
- [] Admin analytics dashboard

<div align="center">

### Phase 6 — Infrastructure & Deployment
`Progress: ░░░░░░░░░░ 0%`

</div>

- [] Unit and integration testing across services
- [] Dockerization of backend, frontend, and AI service
- [] CI/CD pipeline setup
- [] AWS S3 integration for document storage
- [] Production cloud deployment

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