# Payment Service

결제 서비스 마이크로서비스입니다. Kotlin으로 작성되었으며, Spring Boot와 Spring Cloud를 기반으로 합니다.

## 📋 목차

- [개요](#개요)
- [아키텍처](#아키텍처)
- [기술 스택](#기술-스택)
- [주요 기능](#주요-기능)
- [프로젝트 구조](#프로젝트-구조)
- [시작하기](#시작하기)
- [API 문서](#api-문서)
- [테스트](#테스트)

## 🎯 개요

Payment Service는 S-Class Platform의 결제 기능을 담당하는 마이크로서비스입니다.

### 주요 특징

- **마이크로서비스 아키텍처**: 독립적으로 배포 가능한 서비스
- **Kotlin 기반**: 간결하고 안전한 코드
- **Rich Domain Model**: 도메인 로직을 엔티티에 포함
- **다중 PG사 지원**: NicePay, Toss, KakaoPay 등
- **비동기 처리**: WebClient를 사용한 비동기 API 호출

## 🏗️ 아키텍처

### 전체 마이크로서비스 아키텍처

```mermaid
graph TB
    subgraph "Client Layer"
        Web[Web Browser]
        Mobile[Mobile App]
    end

    subgraph "API Gateway"
        Gateway[Spring Cloud Gateway<br/>:8765]
    end

    subgraph "Service Discovery"
        Eureka[Eureka Server<br/>:8761]
    end

    subgraph "Microservices"
        PaymentService[Payment Service<br/>:8082<br/>Kotlin]
        OrderService[Order Service<br/>:8083<br/>Java]
        ProductService[Product Service<br/>:8084<br/>Java]
        UserService[User Service<br/>:8085<br/>Java]
    end

    subgraph "External Services"
        NicePay[NicePay API<br/>PG사]
    end

    subgraph "Database"
        PaymentDB[(Payment DB<br/>PostgreSQL)]
        OrderDB[(Order DB<br/>PostgreSQL)]
        ProductDB[(Product DB<br/>PostgreSQL)]
        UserDB[(User DB<br/>PostgreSQL)]
    end

    Web --> Gateway
    Mobile --> Gateway
    Gateway --> Eureka
    Gateway --> PaymentService
    Gateway --> OrderService
    Gateway --> ProductService
    Gateway --> UserService

    PaymentService --> Eureka
    PaymentService --> OrderService
    PaymentService --> ProductService
    PaymentService --> UserService
    PaymentService --> NicePay
    PaymentService --> PaymentDB

    OrderService --> Eureka
    OrderService --> OrderDB

    ProductService --> Eureka
    ProductService --> ProductDB

    UserService --> Eureka
    UserService --> UserDB

    style PaymentService fill:#4A90E2,stroke:#2E5C8A,stroke-width:3px,color:#fff
    style Gateway fill:#50C878,stroke:#2E7D4E,stroke-width:2px,color:#fff
    style Eureka fill:#FF6B6B,stroke:#C92A2A,stroke-width:2px,color:#fff
```

### Payment Service 내부 아키텍처

```mermaid
graph TB
    subgraph "API Layer"
        Controller[PaymentController<br/>REST API]
    end

    subgraph "Service Layer"
        PaymentService[PaymentService<br/>비즈니스 로직 오케스트레이션]
        NicePayService[NicePayService<br/>PG사 통신]
    end

    subgraph "Domain Layer"
        Payment[Payment Entity<br/>Rich Domain Model]
        PaymentStatus[PaymentStatus Enum]
    end

    subgraph "Infrastructure Layer"
        PaymentRepository[PaymentRepository<br/>JPA]
        OrderClient[OrderServiceClient<br/>Feign]
        ProductClient[ProductServiceClient<br/>Feign]
        UserClient[UserServiceClient<br/>Feign]
        WebClient[WebClient<br/>HTTP Client]
    end

    subgraph "External"
        OrderService[Order Service]
        ProductService[Product Service]
        UserService[User Service]
        NicePayAPI[NicePay API]
    end

    subgraph "Database"
        DB[(PostgreSQL)]
    end

    Controller --> PaymentService
    PaymentService --> NicePayService
    PaymentService --> PaymentRepository
    PaymentService --> OrderClient
    PaymentService --> ProductClient
    PaymentService --> UserClient

    PaymentRepository --> Payment
    PaymentRepository --> DB

    NicePayService --> WebClient
    NicePayService --> NicePayConfig

    OrderClient --> OrderService
    ProductClient --> ProductService
    UserClient --> UserService
    WebClient --> NicePayAPI

    PaymentService --> Payment

    style PaymentService fill:#4A90E2,stroke:#2E5C8A,stroke-width:2px,color:#fff
    style Payment fill:#9B59B6,stroke:#6C3483,stroke-width:2px,color:#fff
    style NicePayService fill:#E67E22,stroke:#A04000,stroke-width:2px,color:#fff
```

### 결제 플로우

```mermaid
sequenceDiagram
    participant Client
    participant Gateway
    participant PaymentService
    participant ProductService
    participant UserService
    participant OrderService
    participant NicePayService
    participant NicePayAPI
    participant DB

    Client->>Gateway: POST /api/payments/prepare
    Gateway->>PaymentService: Forward Request
    
    PaymentService->>ProductService: GET /api/products/{id}
    ProductService-->>PaymentService: ProductDto
    
    PaymentService->>UserService: GET /api/users/{id}
    UserService-->>PaymentService: UserDto
    
    PaymentService->>PaymentService: Create Payment Entity
    PaymentService->>DB: Save Payment
    
    PaymentService->>OrderService: POST /api/orders
    OrderService-->>PaymentService: OrderId
    
    PaymentService->>NicePayService: createPaymentInfo()
    NicePayService-->>PaymentService: Payment Info Map
    
    PaymentService-->>Gateway: Payment Info
    Gateway-->>Client: Payment Info
    
    Note over Client,NicePayAPI: Frontend에서 PG사 결제 진행
    
    Client->>Gateway: POST /api/payments/return
    Gateway->>PaymentService: Forward Request
    
    PaymentService->>DB: Find Payment by pgOrderId
    DB-->>PaymentService: Payment
    
    PaymentService->>NicePayService: approvePayment()
    NicePayService->>NicePayAPI: POST /v1/payments/{tid}
    NicePayAPI-->>NicePayService: Success Response
    
    NicePayService-->>PaymentService: Success
    PaymentService->>Payment: payment.approve()
    PaymentService->>DB: Save Payment
    
    PaymentService->>OrderService: PUT /api/orders/{id}/status
    PaymentService-->>Gateway: Success
    Gateway-->>Client: Success
```

## 🛠️ 기술 스택

### Backend
- **Language**: Kotlin 1.9+
- **Framework**: Spring Boot 3.x
- **Build Tool**: Gradle (Kotlin DSL)

### Spring Cloud
- **Spring Cloud Gateway**: API Gateway
- **Spring Cloud Netflix Eureka**: Service Discovery
- **Spring Cloud OpenFeign**: Service-to-Service Communication

### Database
- **PostgreSQL**: 관계형 데이터베이스
- **Spring Data JPA**: ORM

### External Communication
- **WebClient**: 비동기 HTTP Client (NicePay API 통신)
- **Feign Client**: 동기 HTTP Client (마이크로서비스 간 통신)

### Testing
- **JUnit 5**: 테스트 프레임워크
- **Mockito Kotlin**: Mocking 라이브러리
- **AssertJ**: Assertion 라이브러리

### Documentation
- **OpenAPI 3 (Swagger)**: API 문서화

## ✨ 주요 기능

### 1. 결제 준비 (Prepare Payment)
- 상품 정보 조회
- 사용자 정보 조회
- Payment 엔티티 생성
- Order 생성
- PG사 결제 정보 생성

### 2. 결제 승인 (Approve Payment)
- PG사 API 호출
- Payment 상태 업데이트
- Order 상태 업데이트
- 예외 처리 및 실패 처리

### 3. 결제 조회
- 사용자별 결제 내역 조회
- 결제 상태별 조회
- 페이징 지원

## 📁 프로젝트 구조
