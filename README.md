# ZhiGuang - Knowledge Sharing Platform

A full-stack knowledge sharing community where users can publish articles, interact through likes/favorites, follow other users, and leverage AI-powered features for content summarization and Q&A.

## Tech Stack

**Backend:** Java 21, Spring Boot 3.2, Spring Security, Spring AI, MyBatis, MySQL, Redis, Kafka, Caffeine, Alibaba Cloud OSS, Canal, Elasticsearch

**Frontend:** React 18, TypeScript, Vite

## Architecture Highlights

### Authentication
JWT dual-token system with RS256 signing. 15-minute access tokens paired with 7-day refresh tokens stored in a Redis whitelist. Supports instant token revocation.

### Counter System
Bitmap-based idempotent counting for likes and favorites. Writes go through atomic Lua `SETBIT` operations, producing events that are aggregated via Kafka and periodically flushed into a compact Redis SDS structure (20 bytes per entity). On read anomalies, the system automatically rebuilds counts from bitmap shards with distributed lock protection.

### Content Publishing
Progressive publish flow: create draft, presigned direct-upload to OSS, server-side confirmation, metadata completion, then publish. Integrated with DeepSeek AI for one-click article summarization.

### User Relations
Leader-follower architecture with event-driven consistency. The `following` table acts as the single source of truth; the `follower` table, counter cache, and list cache are all pseudo-replicas. Changes are captured via Canal (MySQL binlog) into an Outbox table, then published to Kafka for async downstream updates. Rate-limited with a per-user token bucket.

### Feed
Three-tier caching: local Caffeine + Redis page cache + Redis fragment cache. Custom hotkey detection dynamically extends TTLs based on access frequency, with random jitter to prevent cache stampedes. Single-flight locks prevent concurrent origin storms on the same page.

### Search
Elasticsearch-backed full-text search with keyword matching, tag filtering, and `search_after` cursor pagination for stable deep pagination. Ranking uses `function_score` to blend BM25 relevance with business signals (likes, favorites). Prefix suggestions powered by ES completion suggester.

### RAG Q&A
Per-article knowledge Q&A using Spring AI + Elasticsearch vector store. Documents are chunked (~800 chars, ~100 char overlap), embedded, and indexed. Questions trigger vector retrieval filtered by post ID, context assembly, and streamed generation via DeepSeek (SSE).

## Project Structure

```
zhiguang/
├── zhiguang_be/          # Backend (Spring Boot)
│   ├── src/main/java/    # Application source
│   ├── src/main/resources/
│   │   └── mapper/       # MyBatis XML mappers
│   ├── db/schema.sql     # Database schema
│   └── pom.xml
├── zhiguang_fe/          # Frontend (React + Vite)
│   ├── src/
│   │   ├── components/   # Reusable UI components
│   │   ├── pages/        # Page components
│   │   ├── services/     # API client layer
│   │   ├── context/      # React context (auth)
│   │   └── types/        # TypeScript type definitions
│   └── package.json
└── README.md
```

## Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 18+
- MySQL 8.0+
- Redis
- Kafka (KRaft mode)
- Elasticsearch 9.x
- Alibaba Cloud OSS account (for file uploads)

## Getting Started

### Backend

1. Create the database and import the schema:
```bash
mysql -u root -p -e "CREATE DATABASE zhiguang DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p zhiguang < zhiguang_be/db/schema.sql
```

2. Create `zhiguang_be/src/main/resources/application.yml` with your database credentials, Redis, Kafka, Elasticsearch, and OSS configuration.

3. Start the backend:
```bash
cd zhiguang_be
mvn spring-boot:run
```

### Frontend

```bash
cd zhiguang_fe
npm install
npm run dev
```

The frontend dev server runs at `http://localhost:5173` and proxies API requests to the backend at `http://localhost:8080`.
