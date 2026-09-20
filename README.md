# TaskGrid — Distributed Job Scheduling System

TaskGrid is a production-grade, fault-tolerant distributed job scheduling and background processing engine built with Java 17 and Spring Boot 3[span_0](start_span)[span_0](end_span). Inspired by modern workflow engines like Apache Airflow, Celery, and Kubernetes Jobs, TaskGrid solves critical distributed coordination challenges: preventing duplicate task execution via atomic locking, ensuring high availability through dynamic leader election, mitigating cascading failures via exponential backoff, and isolating persistent errors into dead-letter queues[span_1](start_span)[span_1](end_span).

---

## 1. Key Features

- **Distributed Mutex & Concurrency Control:** Prevents race conditions and duplicate task execution using Redis atomic primitives (`SETNX` with TTL)[span_2](start_span)[span_2](end_span).
- **Safe Lock Release & Zombie Protection:** Uses atomic Lua scripts for ownership verification before release and monotonic fencing tokens to discard stale writes from zombie workers[span_3](start_span)[span_3](end_span).
- **Dynamic Leader Election:** Elects a single active scheduler instance using Redis keys with automatic TTL expiration to coordinate dispatching and avoid split-brain issues[span_4](start_span)[span_4](end_span).
- **Priority-Based Dispatching:** Implements priority queues with Redis Sorted Sets (`ZSET`) and dynamic wait-time bonuses to prevent job starvation[span_5](start_span)[span_5](end_span).
- **Exponential Backoff with Jitter:** Avoids thundering-herd issues on downstream systems with randomized exponential retry delays[span_6](start_span)[span_6](end_span).
- **Dead Letter Queue (DLQ) & Replay:** Automatically transitions unrecoverable jobs to a `DEAD_LETTER` state after max retries, with APIs available for audit inspection and manual replay[span_7](start_span)[span_7](end_span).
- **Self-Healing Heartbeat Monitor:** Periodically scans worker heartbeats, detects unresponsive or crashed nodes, and safely reclaims orphaned in-flight tasks[span_8](start_span)[span_8](end_span).
- **Dual-Layer Concurrency Control:** Combines Redis-level pessimistic locking prior to task execution with Hibernate optimistic locking (`@Version`) on persistent entities[span_9](start_span)[span_9](end_span).
- **Audit Logging & Telemetry:** Append-only execution history tracking attempt counts, duration, and stack traces, integrated with Spring Boot Actuator and Micrometer for p50/p95/p99 latency tracking[span_10](start_span)[span_10](end_span).

---

## 2. Tech Stack

| Component | Technology Used | Description / Purpose |
| :--- | :--- | :--- |
| **Language** | Java 17[span_11](start_span)[span_11](end_span) | LTS release with modern language features and performance[span_12](start_span)[span_12](end_span). |
| **Framework** | Spring Boot 3[span_13](start_span)[span_13](end_span) | Application framework, scheduling, and Actuator metrics[span_14](start_span)[span_14](end_span). |
| **Persistence** | Spring Data JPA / Hibernate[span_15](start_span)[span_15](end_span) | Entity mapping, audit logging, and `@Version` optimistic locking[span_16](start_span)[span_16](end_span). |
| **Database** | PostgreSQL / H2 In-Memory[span_17](start_span)[span_17](end_span) | Relational persistence for state machine and execution history[span_18](start_span)[span_18](end_span). |
| **Distributed Coordination** | Redis (Lua, Strings, ZSET)[span_19](start_span)[span_19](end_span) | Mutex locks, leader election, worker heartbeats, and priority queues[span_20](start_span)[span_20](end_span). |
| **Testing** | JUnit 5 & Testcontainers[span_21](start_span)[span_21](end_span) | Integration tests running against real Redis and PostgreSQL containers[span_22](start_span)[span_22](end_span). |
| **API Documentation** | Swagger / OpenAPI[span_23](start_span)[span_23](end_span) | Interactive documentation and REST API testing[span_24](start_span)[span_24](end_span). |
| **Monitoring** | Micrometer & Spring Actuator[span_25](start_span)[span_25](end_span) | Metrics collection for Prometheus and Grafana dashboards[span_26](start_span)[span_26](end_span). |
| **Containerization** | Docker & Docker Compose[span_27](start_span)[span_27](end_span) | Multi-container setup for local execution and deployment[span_28](start_span)[span_28](end_span). |

---

## 3. Architecture & Data Flow


+-----------------------------------------------------------------------------+
|                           CLIENT / INGESTION LAYER                          |
|                 REST API Clients  |  Webhooks  |  Dashboard                 |
+-----------------------------------------------------------------------------+
│
│ HTTP POST /api/jobs
▼
+-----------------------------------------------------------------------------+
|                           TASKGRID API SERVICE                              |
|   Idempotency Validation ──► Payload Deserialization ──► Database Persistence|
+-----------------------------------------------------------------------------+
│
┌────────────────────────────┴────────────────────────────┐
▼                                                         ▼
+-----------------------------------+     +-----------------------------------+
|     PRIMARY PERSISTENCE LAYER     |     |  DISTRIBUTED COORDINATION LAYER   |
|            (PostgreSQL)           |     |              (Redis)              |
|                                   |     |                                   |
| * jobs (Master State Machine)   |     | * scheduler:leader (Leader Lock)|
| * job_executions (Audit Trails) |     | * job:{id}:lock (Worker Mutex)  |
| * job_dependencies (DAG Edges)  |     | * priority:queue (ZSET Scores)  |
|                                   |     | * heartbeat:{id} (Worker TTLs)  |
+-----------------------------------+     +-----------------------------------+
▲                                                         ▲
│ State Writes / Status Updates                           │ Atomic Locks & Heartbeats
│                                                         │
+─────────┴─────────────────────────────────────────────────────────┴─────────+
|                          DISTRIBUTED WORKER CLUSTER                         |
|                                                                             |
|  +-----------------------------------------------------------------------+  |
|  |                ACTIVE LEADER SCHEDULER (Redis-Elected)                |  |
|  |  * Scans missing heartbeats       * Reclaims orphaned tasks           |  |
|  |  * Evaluates overdue cron jobs    * Publishes priority tasks to ZSET  |  |
|  +-----------------------------------------------------------------------+  |
|                                      │                                      |
|                                      ▼ Dispatches In-Flight Tasks           |
|                                                                             |
|  +───────────────────────────────+       +───────────────────────────────+  |
|  |         WORKER NODE 1         |       |         WORKER NODE N         |  |
|  |  * Redis SETNX Lock Acquirer  |       |  * Redis SETNX Lock Acquirer  |  |
|  |  * Fencing Token Validator    |  ...  |  * Fencing Token Validator    |  |
|  |  * Bounded ThreadPool Worker  |       |  * Bounded ThreadPool Worker  |  |
|  |  * Backoff & DLQ Handlers     |       |  * Backoff & DLQ Handlers     |  |
|  +───────────────────────────────+       +───────────────────────────────+  |
+─────────────────────────────────────────────────────────────────────────────+
│
▼ Metrics & Telemetry
+-----------------------------------------------------------------------------+
|                        OBSERVABILITY & MONITORING                           |
|       Micrometer Metrics ──► Prometheus Scrape ──► Grafana Dashboards       |
+-----------------------------------------------------------------------------+

---

## 4. Core System Design Concepts

### A. Atomic Locking & Safe Lua Deletion
Workers acquire task-level mutual exclusion using:
```text
SET job:{job_id}:lock {worker_id} NX PX 30000

 * NX: Ensures the key is written only if it does not already exist.
 * PX 30000: Applies an automatic 30-second TTL to avoid indefinite deadlocks if a worker crashes.
Locks are released safely via an atomic Lua script to guarantee that a worker only deletes its own lock:
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end

B. Fencing Tokens (Zombie Protection)
To resolve edge cases where a worker freezes during a Stop-The-World (STW) GC pause or network partition, each lock acquisition increments a monotonic sequence token:

Database mutations enforce token freshness:
UPDATE jobs 
SET status = 'COMPLETED', updated_at = NOW() 
WHERE id = :jobId AND fencing_token <= :myToken;

If a newer worker has already taken over and incremented the token, stale writes update zero rows and are safely rejected.
C. State Machine & Resiliency Flow
Tasks transition through explicit finite states:
[ SCHEDULED ] ──► [ QUEUED ] ──► [ RUNNING ] ──► [ COMPLETED ]
                      ▲                │
                      │                ▼
                      │           [ FAILED ]
                      │                │
                      │ (Retries < Max)│ (Retries >= Max)
                      │                ▼
              [ RETRY_PENDING ]  [ DEAD_LETTER ]
         (Exponential Backoff)         │
                      ▲                │
                      └─ Manual Replay ┘

 * Exponential Backoff + Jitter: Failed attempts delay subsequent retries exponentially (2^n \times \text{Base Delay} \pm \text{Jitter}) to avoid downstream thundering-herd issues.
 * Dead Letter Queue (DLQ): Once max_retries is reached, tasks transition to DEAD_LETTER with complete stack traces and execution metadata preserved for inspection.
5. Architectural Trade-offs & Design Decisions
 * CAP Theorem (Consistency over Availability): Chosen CP configuration. During network partitions or Redis primary failures, task dispatching pauses temporarily rather than risking duplicate execution of non-idempotent workflows.
 * Hybrid Concurrency Control: Uses Redis pessimistic locking prior to expensive business logic execution and Hibernate @Version optimistic locking at the database layer to resolve concurrent state conflicts.
 * Redis Sentinel vs. Redis Cluster: Utilizes Redis Sentinel for primary-replica automated failover without encountering cross-slot transaction limitations on multi-key Lua scripts or global sorted sets.
 * Heartbeat Tuning (10s Pulse / 30s TTL): A 3-miss threshold prevents healthy workers undergoing normal JVM GC pauses from being falsely declared dead.
 * Leader Dispatcher vs. Distributed Polling: Employs a Redis-elected leader to centrally handle prioritization, starvation prevention, and catch-up dispatching, avoiding database row-lock saturation.
 * Redis ZSET vs. DB Polling: Offloads high-frequency priority sorting from PostgreSQL disk I/O to in-memory Redis Sorted Sets (ZPOPMIN).
6. Verification & Stress Testing
 * 10K Concurrency Verification: Tested with JUnit 5 and Testcontainers (PostgreSQL & Redis). Seeding 10,000 tasks executed by 50 concurrent threads synchronized via CountDownLatch yielded 0 duplicate executions across all 10,000 runs.
 * Deterministic Clock Testing: Implements java.time.Clock abstraction, enabling fast simulation of multi-hour exponential backoff intervals without real-time delays.
 * Observed Telemetry Targets:
   * p50 Dispatch Latency: ~12ms
   * p95 Dispatch Latency: ~85ms
   * p99 Latency: ~280ms (under simulated failover conditions)
7. REST API Reference
| Method | Endpoint | Description |
|---|---|---|
| POST | /api/jobs | Submit a new background job with priority and retry configuration. |
| GET | /api/jobs | Retrieve paginated list of all submitted jobs. |
| GET | /api/jobs/{jobId} | Retrieve current status and execution metadata of a job. |
| POST | /api/jobs/{jobId}/retry | Manually reset retry limits and re-enqueue a dead-lettered job. |
| GET | /api/jobs/{jobId}/events | Fetch execution event timeline and error stack traces. |
| GET | /api/dashboard | Expose real-time scheduler metrics and worker counts. |
8. Getting Started
Prerequisites
 * Java 17+
 * Maven 3.8+
 * Docker & Docker Compose
Local Setup
 * Clone the repository:
   git clone [https://github.com/kunal7216/TaskGrid--Distributed-Job-Scheduling-System.git](https://github.com/kunal7216/TaskGrid--Distributed-Job-Scheduling-System.git)
cd TaskGrid--Distributed-Job-Scheduling-System

 * Run with Docker Compose:
   docker compose up --build[span_62](start_span)[span_62](end_span)

 * Run as Spring Boot application:
   mvn clean spring-boot:run[span_63](start_span)[span_63](end_span)

Endpoints
 * API Base: http://localhost:8080
 * Swagger Documentation: http://localhost:8080/swagger-ui.html
 * H2 Console: http://localhost:8080/h2-console (JDBC URL: jdbc:h2:mem:jobschedulerdb)
 * Actuator Health & Metrics: http://localhost:8080/actuator/metrics
9. Example Usage
Submit a Job:
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobType": "PAYMENT",
    "payload": "invoice_id=98721&amount=5000",
    "priority": "CRITICAL",
    "maxRetries": 3
  }'

Replay a Dead-Lettered Job:
curl -X POST http://localhost:8080/api/jobs/1/retry[span_68](start_span)[span_68](end_span)

10. Future Scalability Roadmap
 * Apache Kafka Integration: Replace the simulated event loop with dedicated partition-backed Kafka consumer groups.
 * Dynamic DAG Engine: Provide native workflow dependency execution with cycle detection via topological sorting.
 * Cloud Auto-Scaling: Auto-scale worker nodes dynamically on AWS using CloudWatch alarms tracking the taskgrid.queue.depth metric.
 * Distributed Tracing: Add OpenTelemetry instrumentation across API controllers, Redis operations, and DB transactions.
Author
Kunal Kumar
GitHub: @kunal7216

