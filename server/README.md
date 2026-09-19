# Smart Expense Tracker - Backend

Backend REST API for the **Smart Expense Tracker** mobile application, built with Spring Boot 4.1.1, Spring Security 7, Spring Data MongoDB, and JJWT.

---

## 1. Project Description

The Smart Expense Tracker backend is a modular monolith designed to provide personal finance management services. 

This phase (**Phase 0**) establishes the backend foundation:
- Clean modular package architecture (`auth`, `user`, `common`, `config`).
- MongoDB user document model with unique email constraints and timestamp auditing.
- Robust stateless authentication using JSON Web Tokens (JWT) signed with HMAC-SHA256.
- Password hashing using BCrypt.
- Centralized exception handling with standard JSON error responses.
- CORS configuration tailored for React Native mobile and web development.

Future phases will incrementally introduce modules for expense tracking, income management, multi-source transaction ingestion (SMS, voice, e-passbooks), automated categorization, and investment insights without architectural rework.

---

## 2. Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 4.1.1
- **Security**: Spring Security 7 (Stateless, Lambda DSL, BCrypt password encoder)
- **Database**: MongoDB & Spring Data MongoDB 5.1
- **Validation**: Jakarta Bean Validation 3.1 (`hibernate-validator`)
- **JSON & JWT**: 
  - Java JWT (`io.jsonwebtoken:jjwt-api:0.13.0`)
  - Jackson 3 (`tools.jackson.core:jackson-databind:3.1.5`)
- **Build Tool**: Maven (`mvnw`)
- **Architecture**: Modular Monolith

---

## 3. Package Structure

```
in.expensetrackerapp
├── auth
│   ├── controller
│   │   └── AuthController.java             # POST /api/auth/register, POST /api/auth/login
│   ├── dto
│   │   ├── RegisterRequest.java            # Registration payload with Jakarta validation
│   │   ├── LoginRequest.java               # Login payload with Jakarta validation
│   │   └── AuthResponse.java               # JWT token, token type, expiry, and user profile
│   ├── security
│   │   ├── JwtService.java                 # Token generation, claims extraction, signature verification
│   │   ├── JwtAuthenticationFilter.java    # OncePerRequestFilter for Bearer token authorization
│   │   ├── JwtAuthenticationEntryPoint.java# Custom 401 Unauthorized JSON error response
│   │   └── SecurityConfig.java             # SecurityFilterChain and PasswordEncoder beans
│   └── service
│       ├── AuthService.java                # Authentication service interface
│       └── AuthServiceImpl.java            # Registration & login business logic
│
├── user
│   ├── controller
│   │   └── UserController.java             # GET /api/users/me (current authenticated user)
│   ├── dto
│   │   └── UserResponse.java               # Safe user representation (never exposes passwordHash)
│   ├── model
│   │   └── User.java                       # MongoDB @Document with @Indexed(unique = true) email
│   ├── repository
│   │   └── UserRepository.java             # MongoRepository for User documents
│   └── service
│       ├── UserService.java                # User service interface
│       └── UserServiceImpl.java            # User profile retrieval
│
├── common
│   ├── exception
│   │   ├── DuplicateEmailException.java    # 409 Conflict exception
│   │   ├── InvalidCredentialsException.java# 401 Unauthorized exception
│   │   ├── ResourceNotFoundException.java  # 404 Not Found exception
│   │   └── GlobalExceptionHandler.java     # Centralized @RestControllerAdvice
│   └── response
│       └── ErrorResponse.java              # Standardized API error response DTO
│
└── config
    ├── JwtProperties.java                  # Type-safe configuration binding for application.jwt.*
    ├── MongoConfig.java                    # MongoDB auditing (@EnableMongoAuditing)
    └── CorsConfig.java                     # Dynamic CORS configuration for frontend dev
```

---

## 4. Environment Variables & Configuration

Configuration is managed in `src/main/resources/application.properties` with environment variable overrides:

| Environment Variable | Default Value | Description |
| :--- | :--- | :--- |
| `PORT` | `8080` | HTTP server port |
| `MONGODB_URI` | `mongodb://localhost:27017/expensetrackerdb` | MongoDB connection URI |
| `JWT_SECRET` | *(Required - No default)* | 256-bit (minimum 32 bytes) secret key for HMAC-SHA256 signing |
| `JWT_EXPIRATION_MS` | `86400000` (24 hours) | JWT expiration time in milliseconds |
| `JWT_ISSUER` | `expensetrackerapp` | Token issuer identifier claim |
| `CORS_ALLOWED_ORIGINS`| `http://localhost:3000,http://localhost:8081,http://localhost:19006` | Comma-separated list of allowed origins |

### Configuring JWT_SECRET Locally
`JWT_SECRET` is strictly required for application startup and must **NEVER** be committed to source control.

To generate a secure 256-bit (32 bytes) secret:
```bash
openssl rand -base64 32
```

Provide it at runtime using one of the following methods:

- **PowerShell (Windows)**:
  ```powershell
  $env:JWT_SECRET = "your-generated-256-bit-secret"
  .\mvnw.cmd spring-boot:run
  ```
- **Bash (Linux/macOS)**:
  ```bash
  export JWT_SECRET="your-generated-256-bit-secret"
  ./mvnw spring-boot:run
  ```
- **IDE Run Configuration**:
  Add `JWT_SECRET=your-generated-256-bit-secret` into your IDE's Environment Variables (IntelliJ, VS Code, Eclipse).

### Configuring MongoDB
Ensure MongoDB is running locally:
```bash
# Verify MongoDB service status (Windows)
net start MongoDB
```
Or start MongoDB with Docker:
```bash
docker run -d --name mongodb -p 27017:27017 mongo:latest
```

---

## 5. How to Build & Run

### Prerequisites
- JDK 21+ installed
- Maven (or use the provided `mvnw` / `mvnw.cmd` wrapper)

### Build the Project
```bash
.\mvnw.cmd clean package
```

### Run Tests
```bash
.\mvnw.cmd test
```

### Run Locally
```bash
.\mvnw.cmd spring-boot:run
```

The application will start on port `8080` (or `PORT` specified).

---

## 6. Authentication Endpoints

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Public | Register a new user account |
| `POST` | `/api/auth/login` | Public | Authenticate user and receive JWT |
| `GET` | `/api/users/me` | Protected (Bearer Token) | Get current authenticated user profile |

---

## 7. Example Requests & Responses

### 1. Register User (`POST /api/auth/register`)

**Request**:
```http
POST /api/auth/register HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "name": "Alex Johnson",
  "email": "alex.johnson@example.com",
  "password": "Password123!"
}
```

*Password Requirements*:
- 8 to 100 characters.
- Must contain at least one letter and at least one number.
- Supports any valid special characters without arbitrary restrictions.

**Response (`201 Created`)**:
```json
{
  "id": "66f001abc45def1234567890",
  "name": "Alex Johnson",
  "email": "alex.johnson@example.com",
  "createdAt": "2026-09-20T01:30:00Z",
  "updatedAt": "2026-09-20T01:30:00Z"
}
```

---

### 2. Login User (`POST /api/auth/login`)

**Request**:
```http
POST /api/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "email": "alex.johnson@example.com",
  "password": "Password123!"
}
```

**Response (`200 OK`)**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": "66f001abc45def1234567890",
    "name": "Alex Johnson",
    "email": "alex.johnson@example.com",
    "createdAt": "2026-09-20T01:30:00Z",
    "updatedAt": "2026-09-20T01:30:00Z"
  }
}
```

---

### 3. Current User Profile (`GET /api/users/me`)

**Request**:
```http
GET /api/users/me HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (`200 OK`)**:
```json
{
  "id": "66f001abc45def1234567890",
  "name": "Alex Johnson",
  "email": "alex.johnson@example.com",
  "createdAt": "2026-09-20T01:30:00Z",
  "updatedAt": "2026-09-20T01:30:00Z"
}
```

---

### 4. Error Responses

**Validation Error (`400 Bad Request`)**:
```json
{
  "timestamp": "2026-09-20T01:31:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for request payload",
  "path": "/api/auth/register",
  "validationErrors": [
    {
      "field": "password",
      "message": "Password must contain at least one digit, one lowercase letter, one uppercase letter, and one special character (@#$%^&+=!_-)"
    },
    {
      "field": "email",
      "message": "Email must be a valid email address"
    }
  ]
}
```

**Duplicate Email (`409 Conflict`)**:
```json
{
  "timestamp": "2026-09-20T01:32:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "An account with email alex.johnson@example.com already exists",
  "path": "/api/auth/register"
}
```

**Unauthorized / Invalid Token (`401 Unauthorized`)**:
```json
{
  "timestamp": "2026-09-20T01:33:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Unauthorized: Authentication token is missing, invalid, or expired",
  "path": "/api/users/me"
}
```

---

## 8. How JWT Authentication Works

1. **Token Generation**: Upon valid credentials in `/api/auth/login`, `JwtService` issues a signed JWT containing:
   - Subject: user's normalized email.
   - Claims: `userId`, `name`.
   - Expiration timestamp.
   - HMAC-SHA256 signature using the configured 256-bit secret key.
2. **Stateless Request**: The mobile client attaches the token in the HTTP header:
   `Authorization: Bearer <token>`
3. **Filter Interception**: `JwtAuthenticationFilter` intercepts each incoming request:
   - Extracts and verifies the token signature and expiration.
   - Populates Spring Security's `SecurityContextHolder` with `UsernamePasswordAuthenticationToken`.
4. **Endpoint Resolution**: Controllers access the authenticated user directly from the security context (`Principal` / `Authentication`), preventing client-side identity spoofing.
5. **Rejection Handling**: If the token is invalid, missing, or expired on a protected endpoint, `JwtAuthenticationEntryPoint` responds with a standard 401 JSON error format.

---

## 9. How to Test the APIs

### Using cURL

#### 1. Register:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Alex Johnson\",\"email\":\"alex.johnson@example.com\",\"password\":\"Password123!\"}"
```

#### 2. Login:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"alex.johnson@example.com\",\"password\":\"Password123!\"}"
```

#### 3. Access Protected `/me`:
```bash
curl -X GET http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <TOKEN_RECEIVED_FROM_LOGIN>"
```
