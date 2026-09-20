TaskGrid — Distributed Job Scheduling System

<p align="center">
  <strong>A fault-tolerant distributed job scheduling and background processing engine built with Java 17 and Spring Boot 3.</strong>
</p><p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=openjdk" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=for-the-badge&logo=springboot" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/PostgreSQL-Database-336791?style=for-the-badge&logo=postgresql" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/Redis-Distributed%20Coordination-red?style=for-the-badge&logo=redis" alt="Redis"/>
  <img src="https://img.shields.io/badge/Docker-Containerized-2496ED?style=for-the-badge&logo=docker" alt="Docker"/>
</p><p align="center">
  <a href="#-overview">Overview</a> •
  <a href="#-features">Features</a> •
  <a href="#-architecture">Architecture</a> •
  <a href="#-system-design">System Design</a> •
  <a href="#-testing">Testing</a> •
  <a href="#-api-reference">API</a> •
  <a href="#-getting-started">Getting Started</a>
</p>---

📌 Overview

TaskGrid is a distributed job scheduling and background processing system designed to execute asynchronous workloads reliably across multiple worker nodes.

It focuses on the coordination problems that occur when the same workload is processed by multiple application instances:

- Duplicate task execution
- Worker crashes
- Stale or zombie workers
- Leader coordination
- Retry storms
- Task starvation
- Persistent failures
- Orphaned in-flight tasks
- Concurrent database updates
- Distributed state consistency

TaskGrid combines Redis-based distributed coordination with PostgreSQL persistence to provide a resilient execution model.

The architecture is inspired by concepts found in systems such as Apache Airflow, Celery, and Kubernetes Jobs.

---

✨ Features

Capability| Implementation
🔒 Distributed Mutex| Redis "SETNX" with TTL
🛡️ Safe Lock Release| Atomic Lua ownership validation
🚧 Zombie Protection| Monotonic fencing tokens
👑 Leader Election| Redis TTL-based leader lease
⚡ Priority Scheduling| Redis Sorted Sets ("ZSET")
⏳ Starvation Prevention| Dynamic wait-time priority bonus
🔁 Retry Handling| Exponential backoff + jitter
☠️ Dead Letter Queue| Persistent "DEAD_LETTER" state
❤️ Worker Monitoring| TTL-based heartbeat detection
♻️ Task Recovery| Automatic orphan-task reclamation
🔐 Concurrency Control| Redis pessimistic + JPA optimistic locking
📜 Execution History| Append-only execution audit trail
📊 Observability| Micrometer + Spring Boot Actuator
🧪 Integration Testing| JUnit 5 + Testcontainers
🐳 Deployment| Docker + Docker Compose
📖 API Documentation| Swagger / OpenAPI

---

🏗️ Architecture

flowchart TB

    Client["Clients / Webhooks / Dashboard"]
    
    API["TaskGrid API Service"]

    DB[("PostgreSQL")]
    Redis[("Redis")]

    Leader["Leader Scheduler"]

    W1["Worker Node 1"]
    W2["Worker Node 2"]
    WN["Worker Node N"]

    Metrics["Micrometer / Actuator"]
    Prom["Prometheus"]
    Grafana["Grafana"]

    Client -->|"POST /api/jobs"| API

    API -->|"Persist Job State"| DB
    API -->|"Queue / Coordination"| Redis

    Redis --> Leader

    Leader -->|"Dispatch"| W1
    Leader -->|"Dispatch"| W2
    Leader -->|"Dispatch"| WN

    W1 -->|"Locks / Heartbeats"| Redis
    W2 -->|"Locks / Heartbeats"| Redis
    WN -->|"Locks / Heartbeats"| Redis

    W1 -->|"State Updates"| DB
    W2 -->|"State Updates"| DB
    WN -->|"State Updates"| DB

    API --> Metrics
    Leader --> Metrics
    W1 --> Metrics
    W2 --> Metrics
    WN --> Metrics

    Metrics --> Prom
    Prom --> Grafana

Data Responsibilities

PostgreSQL — Source of Truth

PostgreSQL stores durable application state:

jobs
├── Job definition
├── Current state
├── Retry configuration
├── Priority
├── Fencing token
└── Scheduling metadata

job_executions
├── Attempt number
├── Start / end time
├── Duration
├── Result
├── Error
└── Stack trace

job_dependencies
└── DAG dependency relationships

Redis — Distributed Coordination

Redis handles high-frequency coordination:

scheduler:leader
        │
        └── Leader lease

job:{id}:lock
        │
        └── Worker mutex

fencing:token:job:{id}
        │
        └── Monotonic fencing token

priority:queue
        │
        └── ZSET-based scheduling

heartbeat:{workerId}
        │
        └── Worker liveness TTL

---

⚙️ Core System Design

1. Distributed Locking

Workers acquire a task-level distributed lock before executing a job.

SET job:{jobId}:lock {workerId} NX PX 30000

Why?

- "NX" ensures that only one worker can acquire the lock.
- "PX 30000" gives the lock a 30-second lease.
- Automatic expiration prevents permanent locks when a worker crashes.

Lock Lifecycle

Worker
   │
   ▼
Acquire Redis Lock
   │
   ├── Failed ──► Another worker owns task
   │
   ▼
Execute Job
   │
   ▼
Validate Ownership
   │
   ▼
Release Lock

---

2. Atomic Lock Release

A worker must never delete another worker's lock.

TaskGrid uses an atomic Redis Lua script:

if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end

The script verifies:

Current lock owner == Requesting worker

before deleting the key.

This prevents a delayed worker from accidentally deleting a lock that has already been acquired by a newer worker.

---

🛡️ 3. Fencing Tokens

A Redis TTL alone cannot completely solve the zombie worker problem.

Consider:

Worker A
   │
   ├── Acquires lock
   │
   ├── JVM pauses / network partition
   │
   ▼
Lock expires

Worker B
   │
   ├── Acquires new lock
   ├── Gets newer fencing token
   │
   ▼
Executes task

Worker A may eventually wake up and attempt to write stale data.

TaskGrid uses a monotonically increasing fencing token:

INCR fencing:token:job:{jobId}

Example:

Worker A → Token 41
Worker B → Token 42

Database updates only accept the current or newer token:

UPDATE jobs
SET
    status = 'COMPLETED',
    updated_at = NOW()
WHERE
    id = :jobId
    AND fencing_token <= :myToken;

Therefore:

Token 41 → stale
Token 42 → current

The stale worker's database update affects zero rows.

---

👑 4. Dynamic Leader Election

Only one scheduler instance should perform global scheduling responsibilities.

TaskGrid uses a Redis-based leader lease:

scheduler:leader

with an expiration time.

             Redis
               │
        scheduler:leader
               │
       ┌───────┴───────┐
       │               │
   Scheduler A     Scheduler B
       │
       └── Leader

The active leader is responsible for:

- Detecting missing worker heartbeats
- Reclaiming orphaned tasks
- Evaluating overdue scheduled jobs
- Publishing priority tasks
- Performing catch-up dispatching

If the leader fails and its lease expires, another scheduler can acquire leadership.

---

🎯 5. Priority Scheduling

TaskGrid uses Redis Sorted Sets ("ZSET") for priority scheduling.

Conceptually:

priority:queue

┌───────────────────────────────┐
│ Job A    Score = 10           │
│ Job B    Score = 20           │
│ Job C    Score = 35           │
│ Job D    Score = 50           │
└───────────────────────────────┘

Lower scores can be consumed first using:

ZPOPMIN

Starvation Prevention

A purely priority-based scheduler can starve low-priority jobs.

TaskGrid therefore incorporates a dynamic wait-time bonus:

Effective Priority
    =
Base Priority
    -
Wait-Time Bonus

As a job waits longer, its effective scheduling score improves.

---

🔁 6. Retry & Exponential Backoff

Failed jobs do not immediately retry.

TaskGrid uses exponential backoff with jitter:

Delay = BaseDelay × 2^attempt ± RandomJitter

Example:

Attempt 1 → 1s
Attempt 2 → 2s
Attempt 3 → 4s
Attempt 4 → 8s
Attempt 5 → 16s

Jitter prevents multiple workers from retrying simultaneously and producing a thundering-herd effect against downstream services.

---

☠️ 7. Dead Letter Queue

After the configured retry limit is exhausted:

FAILED
   │
   │ retries < maxRetries
   ▼
RETRY_PENDING
   │
   └──────────────► QUEUED
                   
FAILED
   │
   │ retries >= maxRetries
   ▼
DEAD_LETTER

Dead-lettered jobs retain execution metadata such as:

- Attempt count
- Failure reason
- Stack trace
- Timestamps
- Worker information
- Execution history

Jobs can then be manually replayed through the REST API.

---

❤️ 8. Worker Heartbeats & Self-Healing

Workers periodically publish heartbeat keys:

heartbeat:{workerId}

Example configuration:

Heartbeat interval = 10 seconds
Heartbeat TTL      = 30 seconds

If a worker stops renewing its heartbeat, the scheduler identifies it as potentially unavailable.

Worker
   │
   ├── Heartbeat
   ├── Heartbeat
   ├── Heartbeat
   │
   X
   │
   ▼
TTL expires
   │
   ▼
Leader detects failure
   │
   ▼
Reclaim orphaned tasks
   │
   ▼
Requeue task

The 30-second lease provides tolerance for temporary JVM pauses and transient scheduling delays.

---

🔐 9. Dual-Layer Concurrency Control

TaskGrid uses two complementary mechanisms.

Layer 1 — Redis Pessimistic Lock

Before execution
       │
       ▼
Redis SETNX
       │
       ▼
Exclusive worker ownership

Layer 2 — Hibernate Optimistic Locking

Persistent entities use:

@Version
private Long version;

This protects against concurrent database modifications.

Together:

Redis Lock
    │
    ▼
Prevent duplicate execution
    │
    ▼
Business Logic
    │
    ▼
JPA @Version
    │
    ▼
Protect persistent state

---

🔄 Job State Machine

stateDiagram-v2

    [*] --> SCHEDULED
    SCHEDULED --> QUEUED

    QUEUED --> RUNNING

    RUNNING --> COMPLETED
    RUNNING --> FAILED

    FAILED --> RETRY_PENDING: retries < maxRetries
    RETRY_PENDING --> QUEUED

    FAILED --> DEAD_LETTER: retries >= maxRetries

    DEAD_LETTER --> QUEUED: Manual Replay

State Definitions

State| Meaning
"SCHEDULED"| Job has been accepted but is waiting for its schedule
"QUEUED"| Job is ready for dispatch
"RUNNING"| A worker is currently executing the job
"COMPLETED"| Execution completed successfully
"FAILED"| Current execution attempt failed
"RETRY_PENDING"| Waiting for the next retry window
"DEAD_LETTER"| Maximum retry count exceeded

---

🧩 Architectural Trade-offs

CAP Consideration

TaskGrid prioritizes consistency for critical coordination operations.

During certain Redis coordination failures or network partitions, dispatching can temporarily pause rather than allowing potentially conflicting execution.

Consistency
    ▲
    │
    │      TaskGrid
    │        ●
    │
    └──────────────────► Availability

The practical goal is to avoid duplicate execution of non-idempotent workloads when distributed coordination cannot be trusted.

---

Redis Sentinel vs Redis Cluster

TaskGrid uses Redis Sentinel for primary-replica monitoring and failover.

This simplifies coordination operations involving:

- Distributed locks
- Leader leases
- Global ZSET queues
- Lua scripts
- Multi-key coordination

Redis Cluster can provide greater horizontal scalability, but introduces key-slot and multi-key operation considerations.

---

Leader Dispatcher vs Distributed Polling

Distributed Polling

Worker A ──► DB
Worker B ──► DB
Worker C ──► DB
Worker D ──► DB

Potential issue:

High worker count
       ↓
High-frequency DB polling
       ↓
Row-lock / I/O pressure

Leader Dispatcher

              Leader
             /  |  \
            /   |   \
         Worker Worker Worker

The leader handles global scheduling decisions while workers focus primarily on execution.

---

🧪 Testing & Verification

TaskGrid includes integration testing using:

- JUnit 5
- Testcontainers
- PostgreSQL
- Redis
- "CountDownLatch"
- "java.time.Clock"

10,000-Concurrency Verification

A concurrency stress test seeds:

10,000 jobs
       +
50 concurrent execution threads

The execution path is synchronized to test distributed locking and duplicate-prevention behavior.

Observed result:

Jobs submitted       : 10,000
Concurrent threads   : 50
Duplicate executions : 0

«These figures represent the project's recorded test scenario and should be reproduced before being treated as a benchmark for a new environment.»

---

Deterministic Time Testing

TaskGrid abstracts time using:

java.time.Clock

This makes it possible to simulate:

- Retry delays
- Backoff intervals
- Scheduling windows
- TTL-related scenarios

without waiting for real-world time to pass.

---

📊 Observability

TaskGrid exposes application metrics using:

Spring Boot Actuator
        │
        ▼
    Micrometer
        │
        ▼
    Prometheus
        │
        ▼
     Grafana

Example metrics:

taskgrid.queue.depth
taskgrid.jobs.completed
taskgrid.jobs.failed
taskgrid.jobs.retried
taskgrid.jobs.dead_lettered
taskgrid.worker.active
taskgrid.dispatch.latency

Recorded latency targets from the project's test environment:

Metric| Observed
p50 Dispatch Latency| ~12 ms
p95 Dispatch Latency| ~85 ms
p99 Latency| ~280 ms

These measurements are environment-dependent and are intended as project test observations rather than universal performance guarantees.

---

🧰 Tech Stack

Layer| Technology| Purpose
Language| Java 17| Application development
Framework| Spring Boot 3| Application framework
Persistence| Spring Data JPA / Hibernate| ORM + optimistic locking
Database| PostgreSQL| Durable job state
Development DB| H2| Local/in-memory testing
Coordination| Redis| Locks, queues, heartbeats, leader election
Redis Scripting| Lua| Atomic lock operations
Testing| JUnit 5| Unit and integration testing
Integration Testing| Testcontainers| Real PostgreSQL + Redis containers
API Docs| Swagger / OpenAPI| Interactive API documentation
Metrics| Micrometer| Application metrics
Monitoring| Spring Actuator| Health + metrics endpoints
Visualization| Prometheus + Grafana| Monitoring dashboards
Containerization| Docker| Application packaging
Orchestration| Docker Compose| Local multi-container environment

---

🌐 REST API Reference

Method| Endpoint| Description
"POST"| "/api/jobs"| Submit a new background job
"GET"| "/api/jobs"| Retrieve paginated jobs
"GET"| "/api/jobs/{jobId}"| Retrieve job status and metadata
"POST"| "/api/jobs/{jobId}/retry"| Replay a dead-lettered job
"GET"| "/api/jobs/{jobId}/events"| Retrieve execution history
"GET"| "/api/dashboard"| Scheduler and worker metrics

---

🚀 Getting Started

Prerequisites

Make sure the following are installed:

Java 17+
Maven 3.8+
Docker
Docker Compose

---

1. Clone the Repository

git clone https://github.com/kunal7216/TaskGrid--Distributed-Job-Scheduling-System.git

cd TaskGrid--Distributed-Job-Scheduling-System

---

2. Start with Docker Compose

docker compose up --build

This starts the required infrastructure and application containers defined by the project.

---

3. Run with Spring Boot

Alternatively:

mvn clean spring-boot:run

---

🔎 Local Endpoints

Service| URL
TaskGrid API| "http://localhost:8080"
Swagger UI| "http://localhost:8080/swagger-ui.html"
H2 Console| "http://localhost:8080/h2-console"
Actuator Metrics| "http://localhost:8080/actuator/metrics"

H2 JDBC URL

jdbc:h2:mem:jobschedulerdb

---

📮 Example Usage

Submit a Job

curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobType": "PAYMENT",
    "payload": "invoice_id=98721&amount=5000",
    "priority": "CRITICAL",
    "maxRetries": 3
  }'

---

Replay a Dead-Lettered Job

curl -X POST http://localhost:8080/api/jobs/1/retry

---

🗺️ Scalability Roadmap

Kafka Integration

Replace the simulated event loop with Kafka-backed event streams and dedicated consumer groups.

TaskGrid
   │
   ▼
Kafka Topics
   │
   ├── Consumer Group A
   ├── Consumer Group B
   └── Consumer Group C

Dynamic DAG Engine

Add native workflow dependencies with:

- DAG validation
- Cycle detection
- Topological sorting
- Dependency-aware execution

Cloud Auto-Scaling

Use AWS CloudWatch metrics such as:

taskgrid.queue.depth

to dynamically scale worker capacity.

Distributed Tracing

Add OpenTelemetry instrumentation across:

API
 │
 ├── Redis
 │
 ├── PostgreSQL
 │
 └── Worker Execution

---

📈 Future Architecture

flowchart LR

    API["TaskGrid API"]

    Kafka[("Apache Kafka")]

    Scheduler["Scheduler Cluster"]

    Redis[("Redis")]
    DB[("PostgreSQL")]

    Workers["Auto-Scaled Worker Cluster"]

    OTEL["OpenTelemetry"]
    Prom["Prometheus"]
    Grafana["Grafana"]

    API --> Kafka
    Kafka --> Scheduler

    Scheduler --> Redis
    Scheduler --> DB

    Scheduler --> Workers

    Workers --> Redis
    Workers --> DB

    API --> OTEL
    Scheduler --> OTEL
    Workers --> OTEL

    OTEL --> Prom
    Prom --> Grafana

---

📁 Project Structure

TaskGrid/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── ...
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── ...
│   │
│   └── test/
│       └── ...
│
├── docker/
│   └── ...
│
├── docker-compose.yml
├── pom.xml
├── README.md
└── .gitignore

---

🔒 Reliability Model

TaskGrid combines multiple protection mechanisms instead of relying on a single distributed-systems primitive:

                ┌───────────────────────┐
                │   Job State Machine   │
                └───────────┬───────────┘
                            │
                ┌───────────▼───────────┐
                │    Leader Election    │
                └───────────┬───────────┘
                            │
                ┌───────────▼───────────┐
                │    Redis Job Lock     │
                └───────────┬───────────┘
                            │
                ┌───────────▼───────────┐
                │    Fencing Token      │
                └───────────┬───────────┘
                            │
                ┌───────────▼───────────┐
                │ Worker Execution      │
                └───────────┬───────────┘
                            │
                ┌───────────▼───────────┐
                │ JPA Optimistic Lock   │
                └───────────┬───────────┘
                            │
                ┌───────────▼───────────┐
                │ Retry / Backoff / DLQ │
                └───────────────────────┘

This layered design addresses different failure modes:

Failure| Protection
Duplicate worker execution| Redis mutex
Worker crash| Lock TTL
Incorrect lock deletion| Lua ownership check
Zombie worker| Fencing token
Scheduler failure| Leader lease
Retry storm| Exponential backoff + jitter
Permanent failure| DLQ
Worker disappearance| Heartbeat monitoring
Concurrent DB updates| JPA "@Version"
Task starvation| Dynamic priority aging

---

🎯 Design Goals

TaskGrid is designed around four primary goals:

1. Correctness

Prevent duplicate or stale state transitions during concurrent execution.

2. Fault Tolerance

Recover from worker failures, scheduler failures, retries, and orphaned tasks.

3. Scalability

Move high-frequency coordination workloads to Redis while keeping durable state in PostgreSQL.

4. Observability

Expose execution history and operational metrics for debugging and production monitoring.

---

👨‍💻 Author

Kunal Kumar

- GitHub: "@kunal7216" (https://github.com/kunal7216)
- Repository: "TaskGrid — Distributed Job Scheduling System" (https://github.com/kunal7216/TaskGrid--Distributed-Job-Scheduling-System)

---

<p align="center">
  <strong>TaskGrid — Distributed Scheduling with Reliability, Coordination & Fault Tolerance.</strong>
</p>
