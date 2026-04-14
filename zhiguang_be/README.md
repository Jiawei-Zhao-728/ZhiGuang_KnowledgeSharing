# ZhiGuang Backend

Spring Boot backend service for the ZhiGuang knowledge sharing platform.

## Tech Stack

- Java 21, Spring Boot 3.2.4, Spring Security (JWT + OAuth2)
- MyBatis + MySQL 8.0+
- Redis + Redisson (distributed locks, caching, bitmap counters)
- Kafka (KRaft mode, async event processing)
- Elasticsearch 9.x (full-text search, vector store for RAG)
- Spring AI (DeepSeek / OpenAI integration)
- Alibaba Cloud OSS (object storage)
- Canal (MySQL binlog subscription)
- Caffeine (local cache)

## Setup

1. Ensure MySQL, Redis, Kafka, and Elasticsearch are running.
2. Create the database and import the schema:
```bash
mysql -u root -p -e "CREATE DATABASE zhiguang DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p zhiguang < db/schema.sql
```
3. Create `src/main/resources/application.yml` with your configuration (see root README for details).
4. Run the application:
```bash
mvn spring-boot:run
```

The server starts on port `8080` by default.
