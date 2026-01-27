---
name: Productivity Tracker Architecture
overview: ""
todos: []
---

# Productivity Tracking Application - Architecture Plan

## System Architecture Overview

The application follows a **three-tier architecture** with clear separation of concerns:

```
┌─────────────────┐
│   Next.js UI    │ (Frontend - Client)
└────────┬────────┘
         │ HTTPS/REST
         │ (JWT Token)
┌────────▼────────┐
│  Spring Boot    │ (Backend - Business Logic)
│     API         │
└────────┬────────┘
         │ JDBC
┌────────▼────────┐
│  Supabase       │ (Database + Auth)
│  PostgreSQL     │
└─────────────────┘
```

**Key Design Decisions:**

- **Next.js App Router**: Modern React patterns, SSR/SSG for better performance
- **Spring Boot**: Robust REST API with dependency injection, transaction management
- **Supabase Auth + PostgreSQL**: Managed authentication and database reduce operational overhead
- **JWT Token Validation**: Stateless authentication, Spring validates tokens from Supabase
- **Hybrid Time Tracking**: Real-time tracking with historical edit capabilities

## Project Structure

```

rotrack2/my-app/

├── frontend/              # Next.js 16 Application
│   ├── src/
│   │   ├── app/          # App Router pages
│   │   │   ├── (auth)/   # Auth route group
│   │   │   │   ├── login/
│   │   │   │   └── signup/
│   │   │   ├── dashboard/
│   │   │   │   ├── page.tsx
│   │   │   │   └── layout.tsx
│   │   │   ├── tracker/
│   │   │   │   └── page.tsx
│   │   │   └── layout.tsx
│   │   ├── components/   # React components
│   │   │   ├── ui/       # Reusable UI (buttons, cards, inputs)
│   │   │   ├── tracker/  # Time tracker components
│   │   │   │   ├── TimeTracker.tsx
│   │   │   │   ├── ActivityTimer.tsx
│   │   │   │   └── TimeLogEditor.tsx
│   │   │   ├── dashboard/ # Dashboard visualization components
│   │   │   │   ├── TimeChart.tsx
│   │   │   │   ├── ActivitySummary.tsx
│   │   │   │   └── WeeklyStats.tsx
│   │   ├── lib/          # Utility functions
│   │   │   ├── supabase.ts    # Supabase client
│   │   │   ├── api.ts         # API client for Spring backend
│   │   │   └── utils.ts       # Helper functions
│   │   ├── types/        # TypeScript type definitions
│   │   │   ├── time-entry.ts
│   │   │   └── user.ts
│   │   └── hooks/        # Custom React hooks
│   │       ├── useAuth.ts
│   │       └── useTimeTracking.ts
│   ├── public/           # Static assets
│   └── package.json
│
├── backend/              # Spring Boot Application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/
│   │   │   │       └── rotrack/
│   │   │   │           ├── RotrackApplication.java
│   │   │   │           ├── config/
│   │   │   │           │   ├── SecurityConfig.java
│   │   │   │           │   ├── WebConfig.java
│   │   │   │           │   └── DatabaseConfig.java
│   │   │   │           ├── controller/
│   │   │   │           │   ├── TimeEntryController.java
│   │   │   │           │   ├── UserController.java
│   │   │   │           │   └── DashboardController.java
│   │   │   │           ├── service/
│   │   │   │           │   ├── TimeEntryService.java
│   │   │   │           │   ├── UserService.java
│   │   │   │           │   ├── AuthService.java
│   │   │   │           │   └── DashboardService.java
│   │   │   │           ├── repository/
│   │   │   │           │   ├── TimeEntryRepository.java
│   │   │   │           │   └── UserRepository.java
│   │   │   │           ├── model/
│   │   │   │           │   ├── TimeEntry.java
│   │   │   │           │   ├── User.java
│   │   │   │           │   └── ActivityType.java
│   │   │   │           ├── dto/
│   │   │   │           │   ├── TimeEntryDTO.java
│   │   │   │           │   └── DashboardStatsDTO.java
│   │   │   │           └── exception/
│   │   │   │               └── GlobalExceptionHandler.java
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── application.yml
│   │   └── test/
│   ├── pom.xml           # Maven dependencies
│   └── README.md
│
└── database/             # Database migration scripts
    ├── schema.sql
    └── migrations/
```

## Database Schema Design

The application uses Supabase PostgreSQL. Schema leverages Supabase Auth for user management.

### Core Tables

**users** (extends Supabase auth.users)

- `id` (UUID, primary key, references auth.users.id)
- `email` (text, unique)
- `username` (text, unique)
- `created_at` (timestamp)
- `updated_at` (timestamp)
- Indexes: `idx_users_email`, `idx_users_username`

**time_entries**

- `id` (UUID, primary key, default gen_random_uuid())
- `user_id` (UUID, foreign key to users.id, NOT NULL)
- `activity_type` (enum: 'ROT', 'STAGNANT', 'WORKING', NOT NULL)
- `start_time` (timestamp, NOT NULL)
- `end_time` (timestamp, nullable for active sessions)
- `duration_minutes` (integer, calculated)
- `notes` (text, nullable)
- `created_at` (timestamp, default now())
- `updated_at` (timestamp, default now())
- Constraints: `end_time > start_time`, `duration_minutes >= 0`
- Indexes: `idx_time_entries_user_id`, `idx_time_entries_start_time`, `idx_time_entries_activity_type`

**user_preferences**

- `id` (UUID, primary key)
- `user_id` (UUID, foreign key to users.id, unique)
- `timezone` (text, default 'UTC')
- `daily_goal_hours` (numeric, nullable)
- `created_at` (timestamp)
- `updated_at` (timestamp)

### Future Extension Tables (for Phase 2)

**goals**

- `id` (UUID, primary key)
- `user_id` (UUID, foreign key to users.id)
- `title` (text)
- `target_hours` (numeric)
- `target_date` (date)
- `activity_type` (enum)
- `status` (enum: 'ACTIVE', 'COMPLETED', 'ARCHIVED')
- `created_at` (timestamp)

**study_groups**

- `id` (UUID, primary key)
- `name` (text)
- `description` (text, nullable)
- `created_by` (UUID, foreign key to users.id)
- `created_at` (timestamp)

**study_group_members**

- `group_id` (UUID, foreign key to study_groups.id)
- `user_id` (UUID, foreign key to users.id)
- `role` (enum: 'ADMIN', 'MEMBER')
- `joined_at` (timestamp)
- Primary key: (group_id, user_id)

**Row Level Security (RLS) Policies:**

- Users can only read/write their own time entries
- Users can only read/write their own preferences
- Study group members can view aggregated stats (no individual entry access)

## API Design

### Authentication Flow

1. User authenticates via Supabase Auth (frontend)
2. Supabase returns JWT token
3. Frontend stores token (httpOnly cookie or localStorage)
4. Frontend sends token in Authorization header: `Bearer <token>`
5. Spring Boot validates token with Supabase public key
6. Spring Boot extracts user_id from token claims
7. All subsequent requests include token for authorization

### REST API Endpoints

**Base URL:** `http://localhost:8080/api/v1`

**Authentication**

- `GET /health` - Health check (public)
- `POST /auth/validate` - Validate JWT token (internal)

**Time Entries**

- `GET /time-entries` - Get user's time entries (paginated, filtered by date range)
  - Query params: `startDate`, `endDate`, `activityType`, `page`, `size`
- `POST /time-entries` - Create new time entry
  - Body: `{ activityType, startTime, endTime?, notes? }`
- `GET /time-entries/{id}` - Get specific time entry
- `PUT /time-entries/{id}` - Update time entry
- `DELETE /time-entries/{id}` - Delete time entry
- `POST /time-entries/start` - Start new active session
  - Body: `{ activityType, notes? }`
- `PUT /time-entries/{id}/stop` - Stop active session
- `GET /time-entries/active` - Get user's currently active session

**Dashboard Statistics**

- `GET /dashboard/stats` - Get aggregated statistics
  - Query params: `startDate`, `endDate`, `granularity` (day/week/month)
  - Returns: Total hours per activity type, daily/weekly breakdowns, trends
- `GET /dashboard/summary` - Get current day/week summary
- `GET /dashboard/trends` - Get time series data for charts

**User Management**

- `GET /user/profile` - Get user profile
- `PUT /user/profile` - Update user profile
- `GET /user/preferences` - Get user preferences
- `PUT /user/preferences` - Update user preferences

### Response Format

All responses follow standard REST conventions:

```json
{
  "data": {...},
  "message": "Success",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

Error responses:

```json
{
  "error": "Error message",
  "code": "ERROR_CODE",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

## Frontend Component Architecture

### Page Components

**Login/Signup Pages** (`app/(auth)/login`, `app/(auth)/signup`)

- Use Supabase Auth client directly
- Handle email/password authentication
- Redirect to dashboard on success
- Store session token

**Dashboard Page** (`app/dashboard/page.tsx`)

- Fetches statistics from Spring backend
- Displays time charts (using Chart.js or Recharts)
- Shows daily/weekly summaries
- Activity type breakdown visualization

**Tracker Page** (`app/tracker/page.tsx`)

- Main time tracking interface
- Three activity type buttons (ROT, Stagnant, Working)
- Active timer display
- Recent entries list
- Edit/delete functionality

### Component Hierarchy

```
Dashboard
├── WeeklyStats (aggregate view)
├── TimeChart (line/bar chart)
└── ActivitySummary (pie chart, totals)

Tracker
├── TimeTracker (container)
│   ├── ActivityTimer (active session display)
│   ├── ActivityButtons (start/stop controls)
│   └── RecentEntriesList (time entry list)
└── TimeLogEditor (modal/form for editing)

Shared UI Components
├── Button
├── Card
├── Input
├── Modal
└── LoadingSpinner
```

### State Management

- **Authentication**: React Context (`AuthContext`) with Supabase session
- **Time Tracking**: React hooks (`useTimeTracking`) with local state + API calls
- **Dashboard Data**: React Query or SWR for server state caching
- **Form State**: React Hook Form for complex forms

## Technology Stack Details

### Frontend

- **Framework**: Next.js 16 (App Router)
- **Language**: TypeScript 5
- **Styling**: Tailwind CSS 4
- **Charts**: Recharts or Chart.js
- **HTTP Client**: Axios or fetch API
- **State Management**: React Context + React Query
- **Auth Client**: @supabase/supabase-js
- **Form Handling**: React Hook Form + Zod validation

### Backend

- **Framework**: Spring Boot 3.x
- **Language**: Java 17+
- **Build Tool**: Maven
- **Database Driver**: PostgreSQL JDBC Driver
- **JWT Validation**: jjwt or Spring Security with Supabase JWT
- **API Documentation**: SpringDoc OpenAPI (Swagger)
- **Validation**: Bean Validation (JSR-303)

### Database

- **Provider**: Supabase (Managed PostgreSQL)
- **Version**: PostgreSQL 15+
- **Migrations**: Supabase migrations or Flyway
- **Connection Pooling**: HikariCP (Spring Boot default)

### Infrastructure (Future AWS Deployment)

- **Frontend Hosting**: AWS Amplify or S3 + CloudFront
- **Backend Hosting**: AWS ECS (Fargate) or EC2
- **Database**: Supabase (managed) or AWS RDS PostgreSQL
- **API Gateway**: AWS API Gateway (optional)
- **Container Orchestration**: Docker + ECS or EKS

## Data Flow Diagrams

### Time Entry Creation Flow

```
User clicks "Start Working"
    ↓
Frontend: ActivityTimer component
    ↓
Frontend: POST /api/time-entries/start
    ↓
Spring Boot: Validate JWT token
    ↓
Spring Boot: Extract user_id from token
    ↓
Spring Boot: Create TimeEntry (startTime = now, endTime = null)
    ↓
Spring Boot: Save to PostgreSQL
    ↓
Spring Boot: Return TimeEntryDTO
    ↓
Frontend: Update UI with active session
```

### Dashboard Data Retrieval Flow

```
User navigates to Dashboard
    ↓
Frontend: Dashboard page loads
    ↓
Frontend: GET /api/dashboard/stats?startDate=X&endDate=Y
    ↓
Spring Boot: Validate JWT token
    ↓
Spring Boot: Query PostgreSQL (aggregate time_entries)
    ↓
PostgreSQL: Return aggregated results
    ↓
Spring Boot: Transform to DashboardStatsDTO
    ↓
Frontend: Render charts with Recharts
```

### Authentication Flow

```
User submits login form
    ↓
Frontend: Supabase Auth signInWithPassword()
    ↓
Supabase: Validate credentials
    ↓
Supabase: Generate JWT token
    ↓
Frontend: Store token + session
    ↓
Frontend: Redirect to dashboard
    ↓
Frontend: Include token in API requests (Authorization header)
    ↓
Spring Boot: Validate token on each request
```

## Implementation Approach

### Phase 1: Core Features (MVP)

1. **Database Setup**

   - Create Supabase project
   - Run schema.sql migrations
   - Set up RLS policies

2. **Spring Boot Backend**

   - Initialize Spring Boot project (Spring Initializr)
   - Configure PostgreSQL connection
   - Implement JWT validation filter
   - Create TimeEntry entity and repository
   - Implement REST controllers
   - Add exception handling

3. **Next.js Frontend**

   - Set up Supabase client
   - Create authentication pages
   - Implement API client utilities
   - Build tracker page with timer
   - Build dashboard page with basic charts
   - Add routing and protected routes

4. **Integration**

   - Connect frontend to backend API
   - Test end-to-end flows
   - Add error handling
   - Implement loading states

### Phase 2: Future Features

1. **Goal Tracking**

   - Add goals table
   - Create goal management UI
   - Track progress toward goals
   - Notifications for goal milestones

2. **Study Groups**

   - Add study_groups tables
   - Create group management UI
   - Shared statistics (aggregated, privacy-preserving)
   - Group challenges and competitions

3. **Advanced Analytics**

   - Weekly/monthly reports
   - Productivity insights
   - Time pattern analysis
   - Export functionality (CSV, PDF)

## Scalability Considerations

### Database

- Indexes on frequently queried columns (user_id, start_time, activity_type)
- Partitioning time_entries table by date (if volume grows)
- Connection pooling (HikariCP default 10 connections)
- Read replicas for analytics queries (future)

### Backend

- Stateless API design (enables horizontal scaling)
- Caching layer (Redis) for dashboard stats (future)
- Async processing for heavy aggregations (future)
- Rate limiting on API endpoints

### Frontend

- Static page generation where possible (Next.js SSG)
- Client-side caching (React Query)
- Code splitting and lazy loading
- CDN for static assets (AWS CloudFront)

## Security Considerations

1. **Authentication**: Supabase handles password hashing, token generation
2. **Authorization**: JWT token validation on every request
3. **SQL Injection**: Use parameterized queries (JPA/Hibernate)
4. **XSS Protection**: React escapes by default, validate inputs
5. **CORS**: Configure Spring Boot CORS for frontend domain only
6. **Rate Limiting**: Implement on API endpoints
7. **Environment Variables**: Store secrets in .env (not committed)
8. **HTTPS**: Enforce HTTPS in production
9. **RLS Policies**: Row-level security in PostgreSQL
10. **Input Validation**: Validate all inputs on backend

## Deployment Strategy

### Development

- Local development: Frontend (localhost:3000), Backend (localhost:8080)
- Use Supabase development project
- Hot reload for both frontend and backend

### Staging

- Deploy frontend to Vercel (or AWS Amplify)
- Deploy backend to AWS ECS (or EC2)
- Use Supabase staging project
- Environment variables configured in hosting platform

### Production

- Frontend: AWS Amplify or S3 + CloudFront
- Backend: AWS ECS Fargate (containerized) or EC2
- Database: Supabase (managed) or AWS RDS
- CI/CD: GitHub Actions or AWS CodePipeline
- Monitoring: CloudWatch logs and metrics
- SSL/TLS: AWS Certificate Manager

## Testing Strategy

### Frontend

- Unit tests: Jest + React Testing Library
- Component tests: Test user interactions
- Integration tests: Test API integration
- E2E tests: Playwright or Cypress (optional)

### Backend

- Unit tests: JUnit 5
- Integration tests: Spring Boot Test with TestContainers
- API tests: MockMvc for controller testing
- Repository tests: @DataJpaTest

### Database

- Migration tests: Verify schema changes
- Data integrity tests: Foreign keys, constraints

## Monitoring and Observability

- **Logging**: Structured logging (Logback in Spring Boot, console.log in Next.js)
- **Error Tracking**: Sentry or similar
- **Performance Monitoring**: Application Performance Monitoring (APM) tool
- **Database Monitoring**: Supabase dashboard or CloudWatch
- **User Analytics**: Optional Google Analytics or privacy-focused alternative

## Documentation

- **API Documentation**: SpringDoc OpenAPI (Swagger UI)
- **Code Comments**: Javadoc for Java, JSDoc for TypeScript
- **README Files**: Setup instructions for frontend and backend
- **Architecture Diagrams**: Keep diagrams updated as system evolves
- **User Guide**: Document key features for end users