# GlowScan Backend

Backend service for **GlowScan — AI-Powered Skin Analysis & Personalized Skincare Assistant**.

GlowScan is a mobile-first skincare application designed to help users understand observable skin characteristics, receive personalized skincare guidance, build skincare routines, and monitor changes in their skin over time.

The system combines user-provided skin information with AI-assisted facial skin analysis to build a personalized skin profile and provide relevant skincare recommendations.

> GlowScan provides general skincare guidance and is not intended for medical diagnosis or treatment.

---

## Core Experience

GlowScan is built around four main user journeys:

### Skin Analysis & Personalized Guidance

Users complete a skin assessment and submit a facial image for AI-assisted analysis.

GlowScan combines both sources of information to build a personalized skin profile and generate relevant skincare guidance.

```text
Skin Assessment + Facial Analysis
                ↓
           Skin Profile
                ↓
     Personalized Guidance
```

### Personalized Skincare Routine

Based on the user's skin profile and recommendations, GlowScan helps users build and follow a personalized skincare routine.

```text
Skin Profile
     ↓
Recommendations
     ↓
Personalized Routine
     ↓
Daily Skincare
```

### Skin Journey & Progress Tracking

Users can perform additional skin analyses over time and compare observable skin characteristics across different analysis sessions.

```text
Initial Analysis
       ↓
Follow Routine
       ↓
New Analysis
       ↓
Compare Progress
       ↓
Updated Guidance
```

### Premium Subscription

GlowScan follows a freemium model where users can access core functionality for free and optionally subscribe to Premium for additional features.

Subscription and payment processing are used to monetize GlowScan itself and are independent from affiliate product purchases.

---

## Product Recommendations

GlowScan maintains a curated skincare product catalog managed by administrators.

Products can be associated with:

* Ingredients
* Skin types
* Skin concerns
* Product categories
* Affiliate links

The recommendation system selects relevant products from this catalog based on the user's skin profile and skincare recommendations.

```text
Admin
  ↓
Curated Product Catalog
  ↓
Recommendation Engine
  ↓
Recommended Products
  ↓
User
  ↓
External Marketplace
```

GlowScan does not process purchases made through affiliate links.

---

## System Roles

### User

Users can:

* Manage their profile
* Complete skin assessments
* Submit facial images for skin analysis
* View their personalized skin profile
* Receive skincare guidance
* Follow personalized skincare routines
* View recommended products
* Access external products through affiliate links
* Track skin changes over time
* Subscribe to Premium features

### Admin

Administrators are responsible for maintaining the skincare information used by the system, including:

* Skincare products
* Ingredients
* Skin concern relationships
* Skin type relationships
* Affiliate links
* Product availability

---

## AI-Assisted Skin Analysis

Skin analysis is a core capability of GlowScan.

The AI component focuses on extracting supported observable characteristics from facial images.

```text
Facial Image
     ↓
AI-Assisted Analysis
     ↓
Observable Skin Characteristics
     ↓
GlowScan Business Logic
     ↓
Personalized Recommendations
```

AI analysis does not directly determine skincare products or routines.

Recommendation decisions remain part of the GlowScan application business logic.

The implementation may use an existing or pretrained AI model rather than training a model from scratch.

---

## Tech Stack

### Backend

* Java
* Spring Boot
* Spring Security
* Spring Data JPA
* PostgreSQL
* JWT Authentication

### Client Applications

* Flutter
* React TypeScript

### Testing & Documentation

* JUnit 5
* Mockito
* Swagger / OpenAPI
* Postman

### Infrastructure

* Docker
* Docker Compose

---

## Project Scope

GlowScan focuses on:

* AI-assisted facial skin analysis
* Skin assessment
* Personalized skin profiles
* Personalized skincare guidance
* Skincare routine management
* Skin progress tracking
* Curated product recommendations
* Affiliate product linking
* Premium subscriptions
* Administrative product configuration

GlowScan intentionally excludes:

* Shopping cart
* Product checkout
* Product payment processing
* Order management
* Shipping
* Inventory management
* Full e-commerce functionality
* Training a large AI model from scratch

---

## Documentation

Detailed requirements and technical design are maintained separately.

### Requirements Definition Specification (RDS)

[View RDS](https://docs.google.com/document/d/18aKVWUxJ-qY0fQOIT7AsbGUse0AilTgc/edit?usp=sharing&ouid=109071155543487728544&rtpof=true&sd=true)

Contains the business context, actors, user requirements, business rules, use cases, functional requirements, and non-functional requirements.

### Software Design Specification (SDS)

[View SDS](https://docs.google.com/document/d/1mVzAxN7aKKnSq3cTJTjL6NmYEoi7T8EB/edit?usp=sharing&ouid=109071155543487728544&rtpof=true&sd=true)

Contains the system architecture, AI integration design, database design, API design, component design, design patterns, sequence diagrams, security design, and deployment architecture.

---

## Getting Started

### Prerequisites

* Java 21+
* Maven
* PostgreSQL
* Docker

### Run Locally

```bash
./mvnw spring-boot:run
```

The backend runs at:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

---

## Related Repositories

```text
glowscan-backend
glowscan-mobile
glowscan-web
```

An additional AI service repository may be introduced if the selected AI model requires a separately deployed inference service.

---

## Disclaimer

GlowScan provides general skincare information based on observable facial characteristics and user-provided skincare information.

It is not intended to diagnose skin diseases, prescribe medication, replace professional dermatological consultation, or provide medical treatment.
