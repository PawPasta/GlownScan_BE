# GlowScan Backend

Backend service for **GlowScan — AI-Powered Skin Analysis & Personalized Skincare Assistant**.

GlowScan is a mobile-first skincare application that analyzes facial images to identify observable skin characteristics and combines them with user-provided information to generate personalized skincare guidance and product recommendations.

> GlowScan provides general skincare guidance only and is not intended for medical diagnosis or treatment.

---

## Overview

The backend is responsible for the core business logic of GlowScan, including:

* User authentication and profile management
* Skin assessment
* Facial skin analysis orchestration
* Skin profile management
* Personalized skincare recommendations
* Skincare routine management
* Progress tracking
* External product / affiliate integration

The AI service focuses on extracting observable skin characteristics, while the backend handles recommendation rules and application business logic.

---

## Tech Stack

* **Java**
* **Spring Boot**
* **Spring Security**
* **Spring Data JPA**
* **PostgreSQL**
* **JWT**
* **Swagger / OpenAPI**
* **JUnit 5**
* **Mockito**
* **Docker**

---

## System Architecture

```text
Flutter Mobile App
        |
        | REST API
        v
Spring Boot Backend
     /         \
    v           v
PostgreSQL    AI Service
                  |
                  v
          Skin Analysis Result

Spring Boot Backend
        |
        v
Product Provider
(Mock / Affiliate Platform)
```

---

## Core Flow

```text
User
 ↓
Skin Assessment
 ↓
Facial Scan
 ↓
AI Skin Analysis
 ↓
Skin Profile
 ↓
Personalized Skincare Guidance
 ↓
Product Recommendation
 ↓
External Affiliate Platform
```

GlowScan does not implement its own shopping cart, payment, order, shipping, or inventory system.

---

## Documentation

Detailed system requirements and technical design are maintained separately.

* **RDS — Requirements Definition Specification**
  [View RDS](YOUR_RDS_GOOGLE_DRIVE_LINK)

* **SDS — Software Design Specification**
  [View SDS](YOUR_SDS_GOOGLE_DRIVE_LINK)

---

## Related Repositories

```text
glowscan-backend
glowscan-ai-service
glowscan-mobile
glowscan-web
```

---

## Getting Started

### Prerequisites

* Java 21+
* Maven
* PostgreSQL
* Docker

### Run locally

```bash
./mvnw spring-boot:run
```

By default, the backend runs at:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

---

## Project Scope

The current project focuses on:

* AI-assisted facial skin analysis
* Personalized skincare guidance
* Skin progress tracking
* Product recommendation through external affiliate providers

It intentionally excludes building a full e-commerce platform.

---

## Disclaimer

GlowScan provides skincare-related information based on observable image characteristics and user-provided information.

It does not provide medical diagnosis, prescription, or treatment.
