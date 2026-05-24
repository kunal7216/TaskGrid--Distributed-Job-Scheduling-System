# TaskGrid -- Distributed Job Scheduling System

A Spring Boot backend system that simulates a distributed job scheduling platform capable of queuing, scheduling, executing, retrying, monitoring and dead-lettering background jobs.

This project is inspired by systems such as Airflow, Celery, Kubernetes Jobs and distributed worker queues.

## Features

- Submit background jobs through REST APIs
- Simulated Kafka-style job events
- Worker execution loop using scheduler polling
- Distributed lock simulation to prevent duplicate execution
- Retry with exponential backoff
- Dead Letter Queue behavior after max retries
- Leader election simulation
- Priority-based job dispatching
- Job timeline/event logs
- Dashboard metrics
- Swagger API documentation
- H2 database for local execution
- Docker and Docker Compose support

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java 17 |
| Backend | Spring Boot 3 |
| APIs | REST APIs |
| Persistence | Spring Data JPA / Hibernate |
| Database | H2 In-Memory DB |
| Scheduling | Spring Scheduling |
| Documentation | Swagger / OpenAPI |
| Monitoring | Spring Boot Actuator |
| Deployment | Docker, Docker Compose |

## Architecture

```text
Client
  |
  v
Job API Service
  |
  v
Job Queue Simulation / Event Logs
  |
  v
Leader Scheduler
  |
  v
Worker Service
  |
  +--> Distributed Lock Simulation
  +--> Job Executor
  +--> Retry Handler
  +--> DLQ Handler
  |
  v
Dashboard + Event Timeline
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/jobs` | Submit a new job |
| GET | `/api/jobs` | Get all jobs |
| GET | `/api/jobs/{jobId}` | Get job status |
| POST | `/api/jobs/{jobId}/retry` | Retry failed/DLQ job |
| GET | `/api/jobs/{jobId}/events` | Get job event timeline |
| GET | `/api/dashboard` | Get scheduler metrics |

## Run Locally

```bash
mvn spring-boot:run
```

Application:

```text
http://localhost:8080
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

H2 Console:

```text
http://localhost:8080/h2-console
```

H2 credentials:

```text
JDBC URL: jdbc:h2:mem:jobschedulerdb
Username: sa
Password:
```

## Example API Calls

### Submit successful job

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobType": "EMAIL",
    "payload": "send onboarding email",
    "priority": "HIGH",
    "maxRetries": 3
  }'
```

### Submit transient failure job

This fails once and then succeeds on retry.

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobType": "REPORT",
    "payload": "fail once then generate report",
    "priority": "CRITICAL",
    "maxRetries": 3
  }'
```

### Submit permanent failure job

This goes to dead letter after retries.

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobType": "PAYMENT",
    "payload": "always fail payment reconciliation",
    "priority": "HIGH",
    "maxRetries": 2
  }'
```

### Check job status

```bash
curl http://localhost:8080/api/jobs/{jobId}
```

### Check job events

```bash
curl http://localhost:8080/api/jobs/{jobId}/events
```

### Dashboard

```bash
curl http://localhost:8080/api/dashboard
```

## Run with Docker

```bash
docker compose up --build
```

## Important Design Concepts

### Producer-Consumer Pattern

The API service accepts jobs and writes job events. The worker service consumes runnable jobs and processes them asynchronously.

### Distributed Locking

The system simulates Redis-style locks using job lock fields:

```text
lockedBy
lockExpiresAt
```

This prevents multiple workers from processing the same job.

### Leader Election

A leader lock selects one scheduler node to dispatch runnable jobs.

```text
scheduler-leader -> node-1
```

### Retry with Exponential Backoff

Failed jobs are retried using exponential delay:

```text
2s, 4s, 8s, 16s...
```

### Dead Letter Queue

If max retries are exceeded, the job is moved to:

```text
DEAD_LETTER
```

### Priority Scheduling

Jobs are dispatched by priority:

```text
CRITICAL > HIGH > MEDIUM > LOW
```

## Future Enhancements

- Real Apache Kafka integration
- Redis-based distributed locks
- PostgreSQL persistence
- Multiple worker services
- Kubernetes deployment
- Prometheus/Grafana monitoring
- Cron-based scheduled jobs
- Workflow DAG support
- Job timeout handling
- Idempotency keys
- Multi-tenant queues

## Author

Kunal Kumar  
GitHub: https://github.com/kunal7216
