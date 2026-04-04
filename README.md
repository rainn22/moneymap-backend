# MoneyMap Backend

MoneyMap Backend is a Spring Boot REST API for personal finance management. It provides authentication, transaction tracking, budgeting, saving goals, alerts, dashboard summaries, category management, and admin operations in a modular monolithic architecture.

## Tech Stack

- Java 17
- Spring Boot 4
- Spring Web MVC
- Spring Security with JWT
- Spring Data JPA
- PostgreSQL for production
- H2 for tests
- Maven Wrapper
- Jib for container image builds
- SMTP or Resend for email delivery

## Features

- User registration with email verification
- JWT access token authentication
- Refresh token rotation and logout support
- Role-based authorization for admin APIs
- CRUD operations for transactions
- Search and pagination for transaction lists
- Budget planning and monthly budget setup
- Saving goal creation and contribution tracking
- Automatic saving contribution transactions
- Budget alerts with optional email notifications
- Dashboard aggregation for financial overview
- Shared and personal category management
- Admin dashboard, user management, alert review, and shared category management

## Project Structure

The codebase uses a feature-based package structure. Each business module keeps its controller, service, repository, DTOs, and entities together.

```text
src/main/java/com/example/moneymap
├── common
│   ├── dto
│   ├── exception
│   └── security
├── config
├── features
│   ├── admin
│   ├── alert
│   ├── auth
│   ├── budget
│   ├── category
│   ├── dashboard
│   ├── saving
│   ├── transaction
│   └── user
└── MoneymapBackendApplication.java
```

## Architecture Overview

MoneyMap Backend follows a layered modular monolith design:

- Controllers expose REST endpoints under `/api/**`
- Security components validate JWTs and enforce access rules
- Services implement business logic
- Repositories persist data through Spring Data JPA
- PostgreSQL stores application data in production

High-level request flow:

```text
Client Application
-> REST Controllers
-> Spring Security + JWT Filter
-> Feature Services
-> JPA Repositories
-> PostgreSQL
```

Main business modules:

- `auth`: registration, login, refresh, logout, email verification
- `transaction`: income, expense, and saving transactions
- `budget`: budget setup, allocation, and tracking support
- `saving`: saving goals and contributions
- `alert`: budget threshold monitoring and notifications
- `dashboard`: aggregated user financial summary
- `category`: shared and personal categories
- `admin`: admin-only analytics and management tools

## API Summary

Main endpoint groups:

- `/api/auth` for authentication and email verification
- `/api/users` for user-related operations
- `/api/transactions` for transaction management
- `/api/budgets` for budgets and budget setup
- `/api/saving-goals` for saving goals
- `/api/categories` for personal and shared categories
- `/api/alerts` for alert retrieval and updates
- `/api/dashboard` for the user dashboard
- `/api/admin` for admin-only operations

Security rules:

- `/api/auth/**` is public
- `/api/admin/**` requires `ROLE_ADMIN`
- all other endpoints require authentication

For request examples, see [postman/MoneyMap.postman_collection.json](/Users/rany/moneymap-backend/postman/MoneyMap.postman_collection.json).

## Prerequisites

Before running the project, make sure you have:

- Java 17 installed
- PostgreSQL installed and running
- A database created for the app
- An email provider configured if you want verification emails enabled

## Local Setup

1. Clone the repository.
2. Copy `.env.example` values into your local environment or export them manually.
3. Set `SPRING_PROFILES_ACTIVE=production` if you want to use PostgreSQL-based production settings.
4. Create the PostgreSQL database defined by `PGDATABASE`.
5. Start the application with the Maven wrapper.

Example environment variables:

```bash
export SPRING_PROFILES_ACTIVE=production
export APP_JWT_SECRET=replace-with-a-long-random-secret-at-least-64-characters-long
export PGHOST=localhost
export PGPORT=5432
export PGDATABASE=moneymapdb
export PGUSER=postgres
export PGPASSWORD=replace-me
export MAIL_ENABLED=false
```

Run the app:

```bash
./mvnw spring-boot:run
```

The server starts on:

```text
http://localhost:8080
```

## Configuration

Core configuration comes from:

- [application.properties](/Users/rany/moneymap-backend/src/main/resources/application.properties)
- [application-production.properties](/Users/rany/moneymap-backend/src/main/resources/application-production.properties)
- [.env.example](/Users/rany/moneymap-backend/.env.example)

Important environment variables:

### Application

- `SPRING_PROFILES_ACTIVE`: active Spring profile, usually `production`
- `PORT`: server port, defaults to `8080`
- `CORS_ALLOWED_ORIGINS`: allowed frontend origins

### JWT

- `APP_JWT_SECRET`: signing secret for JWT tokens
- `APP_JWT_EXPIRATION`: access token lifetime in milliseconds
- `APP_JWT_REFRESH_EXPIRATION`: refresh token lifetime in milliseconds

### Database

- `PGHOST`
- `PGPORT`
- `PGDATABASE`
- `PGUSER`
- `PGPASSWORD`

### Mail

- `MAIL_ENABLED`: enables or disables email sending
- `MAIL_PROVIDER`: `resend` or `smtp`
- `MAIL_FROM`: sender address
- `MAIL_VERIFY_URL`: frontend verification URL
- `RESEND_API_KEY`: required when `MAIL_PROVIDER=resend`

### SMTP-only properties

If you choose `MAIL_PROVIDER=smtp`, also configure:

- `SPRING_MAIL_HOST`
- `SPRING_MAIL_PORT`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED`

Note: the current `.env.example` includes mail variables in `MAIL_*` form. The SMTP config class reads standard Spring Mail properties such as `spring.mail.host`, so if you use SMTP in your runtime environment, make sure those Spring Mail properties are actually provided.

## Email Options

The backend supports two email delivery modes:

### Resend

Use:

```bash
export MAIL_ENABLED=true
export MAIL_PROVIDER=resend
export RESEND_API_KEY=your-api-key
export MAIL_FROM=no-reply@your-domain.com
export MAIL_VERIFY_URL=https://your-frontend-domain/auth/verify
```

### SMTP

Use:

```bash
export MAIL_ENABLED=true
export MAIL_PROVIDER=smtp
export SPRING_MAIL_HOST=smtp.gmail.com
export SPRING_MAIL_PORT=587
export SPRING_MAIL_USERNAME=your-email
export SPRING_MAIL_PASSWORD=your-password
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true
export MAIL_FROM=no-reply@example.com
export MAIL_VERIFY_URL=https://your-frontend-domain/auth/verify
```

## Admin Bootstrap

The application can create an initial admin account at startup.

Enable it with:

```bash
export ADMIN_BOOTSTRAP_ENABLED=true
export ADMIN_USERNAME=admin
export ADMIN_EMAIL=admin@example.com
export ADMIN_PASSWORD=change-me
export ADMIN_FIRST_NAME=System
export ADMIN_LAST_NAME=Admin
```

This runs once on startup and only creates the admin if the email does not already exist.

## Running Tests

Run the test suite with:

```bash
./mvnw test
```

Tests use H2 in-memory database settings from [src/test/resources/application.properties](/Users/rany/moneymap-backend/src/test/resources/application.properties).

## Build

Package the project with:

```bash
./mvnw clean package
```

## Deployment

The project is designed for environment-variable-based deployment and includes Jib configuration for container image builds.

### Standard deployment flow

1. Provision a PostgreSQL database.
2. Set all required environment variables.
3. Set `SPRING_PROFILES_ACTIVE=production`.
4. Build the application jar or container image.
5. Run the application behind your preferred reverse proxy or platform service.

### Build container image with Jib

The Maven build contains a Jib configuration targeting:

```text
ghcr.io/rainn22/moneymap-backend
```

To build and push with Jib, provide:

- `GITHUB_USERNAME`
- `GITHUB_TOKEN`

Then run:

```bash
./mvnw compile jib:build
```

### Production considerations

- Use a strong `APP_JWT_SECRET`
- Restrict `CORS_ALLOWED_ORIGINS` to trusted frontend domains
- Disable admin bootstrap after initial setup
- Enable email only when provider credentials are configured
- Use managed PostgreSQL backups and monitoring

## Postman Collection

A Postman collection is included for manual API testing:

- [MoneyMap.postman_collection.json](/Users/rany/moneymap-backend/postman/MoneyMap.postman_collection.json)

## Documentation Notes

If you are preparing an academic report or project document, this backend can be described as:

- a modular monolithic architecture
- a layered RESTful backend
- a secure finance management API using JWT authentication

## License

No license file is currently included in this repository.
