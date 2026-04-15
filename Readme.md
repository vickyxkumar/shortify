# 🚀 Shortify – Scalable URL Shortening Service

Shortify is a high-performance URL shortening service designed to handle **read-heavy traffic at scale** using caching and event-driven architecture. It provides fast redirects, reliable analytics tracking, and a production-ready backend design.

---

## 🧠 Features

- 🔗 Generate short URLs using **Base62 + Snowflake ID**
- ⚡ Ultra-fast redirects using **Redis caching**
- 📊 Asynchronous analytics tracking using **Kafka**
- 🔁 Fault-tolerant processing with **Retry + Dead Letter Queue (DLQ)**
- 📦 Batch processing for efficient database writes
- 🔒 Collision-free URL generation
- 📈 Scalable architecture for high throughput systems

---

## 🏗️ System Architecture

    Client Request
         ↓
    Spring Boot API
         ↓
    Redis (Cache Layer)
         ↓
    Kafka (Event Streaming)
         ↓
    Consumer Service
         ↓
    PostgreSQL (Persistence)

---

## ⚙️ Tech Stack

- **Backend:** Java, Spring Boot, Spring Data JPA  
- **Database:** PostgreSQL  
- **Cache:** Redis  
- **Messaging Queue:** Apache Kafka  
- **Containerization:** Docker  
- **Build Tool:** Maven  
- **Version Control:** Git  

---

## 📌 Key Components

### 1. URL Shortening
- Uses **Snowflake ID** for unique ID generation
- Encodes IDs into short URLs via **Base62 encoding**

### 2. Caching Layer (Redis)
- Stores `shortUrl → originalUrl`
- Reduces database load significantly
- Improves redirect latency

### 3. Event-Driven Analytics (Kafka)
- Redirect events are published to Kafka
- Consumer processes events asynchronously
- Ensures non-blocking API performance

### 4. Batch Processing
- Click events are processed in batches
- Reduces database write overhead
- Improves throughput

### 5. Retry & Dead Letter Queue (DLQ)
- Failed events are retried automatically
- Permanently failing events are sent to DLQ
- Ensures reliability and fault tolerance

---

## 🔁 API Endpoints

### Create Short URL

    POST /api/urls/shorten

### Redirect

    GET /{shortCode}

---

## 🧪 Running Locally

### 1. Start Redis

    redis-server

### 2. Start Kafka & ZooKeeper (Docker)

    docker run -d --name zookeeper -p 2181:2181 -e ZOOKEEPER_CLIENT_PORT=2181 confluentinc/cp-zookeeper:7.3.0

    docker run -d --name kafka -p 9092:9092 --link zookeeper \
    -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
    -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
    -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    confluentinc/cp-kafka:7.3.0

### 3. Run Spring Boot App

    mvn spring-boot:run

---

## 🧪 Testing Failure Handling

- Simulate failure in consumer
- Verify retries
- Check DLQ topic:

    kafka-console-consumer --bootstrap-server localhost:9092 --topic click-events-dlq --from-beginning

---

## 📈 Scalability Considerations

- Horizontal scaling of application servers
- Redis clustering for high availability
- Kafka partitioning for parallel processing
- Read replicas for PostgreSQL

---

## 🚀 Future Improvements

- Idempotency handling for duplicate events
- API Gateway integration
- Microservices separation (URL, Analytics, User)
- Rate limiting (IP/User-based)
- Monitoring & Observability (Prometheus, Grafana)

---

## 👨‍💻 Author

**Vicky Kumar**