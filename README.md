# Fraud Detection Engine

A production-grade fraud rule engine service that processes financial transaction events and flags potential fraud using multiple detection rules.

## Overview

This system evaluates transactions in real-time against a sophisticated rule engine to detect fraudulent patterns including:

- **High-value transactions** - Flags unusually large transactions
- **Velocity checks** - Detects excessive transaction frequency or amounts
- **Unusual time patterns** - Identifies suspicious late-night transactions
- **Rapid location changes** - Flags impossible travel patterns
- **Repeated declined attempts** - Detects card testing attacks

## Technology Stack

- **Java 21** - Latest LTS with modern language features (Records, Virtual Threads, Pattern Matching)
- **Spring Boot 3.2.2** - Production-ready framework with virtual threads enabled
- **PostgreSQL 16** - Robust relational database
- **Liquibase** - Database migration management
- **JUnit 5 & Mockito** - Comprehensive testing
- **Docker & Docker Compose** - Containerization
- **OpenAPI/Swagger** - API documentation

**Modern Java 21 Features**:
- Response DTOs use Java 21 **records** for immutability (`ErrorResponse`, `TransactionResponse`, `FraudAlertResponse`, `FraudRuleResult`)
- Virtual threads enabled for 10x better I/O concurrency
- Pattern matching (instanceof + switch) for clean error handling

## Architecture

### Core Components

```
┌─────────────────┐
│  REST API       │ - Transaction submission & fraud alert retrieval
└────────┬────────┘
         │
┌────────▼────────┐
│  Service Layer  │ - Business logic & orchestration
└────────┬────────┘
         │
┌────────▼────────────┐
│  Fraud Engine       │ - Rule evaluation & alert generation
│  ┌───────────────┐  │
│  │ Rule Registry │  │
│  └───────┬───────┘  │
│          │          │
│  ┌───────▼───────┐  │
│  │ Fraud Rules:  │  │
│  │ - High Value  │  │
│  │ - Velocity    │  │
│  │ - Time-based  │  │
│  │ - Location    │  │
│  │ - Repeated    │  │
│  └───────────────┘  │
└─────────────────────┘
         │
┌────────▼────────┐
│  Data Layer     │ - JPA repositories & PostgreSQL
└─────────────────┘
```

### Rule Engine Design

The fraud detection engine follows a **Strategy Pattern** where:
- Each rule implements the `FraudRule` interface
- Rules are auto-discovered via Spring's component scanning
- Rules execute in priority order
- Multiple rules can flag a single transaction
- Results are aggregated and persisted as fraud alerts

## Prerequisites

- **Docker & Docker Compose** (recommended)
- **OR** for local development:
    - Java 21 JDK
    - Maven 3.9+
    - PostgreSQL 16+

## Quick Start with Docker

### 1. Environment Setup

The project uses environment variables for configuration. A template is provided:

```bash
# Copy the example environment file (first time only)
cp .env.example .env

# The default values in .env work for local development
# Edit .env if you need to change database credentials
```

**Note**: The `.env` file contains local credentials and is in `.gitignore`. The `.env.example` template is committed to git as a reference.

**For Docker Compose**: Use `.env` as-is (DB_HOST=postgres)
**For local Maven runs**: Either don't use `.env` (defaults to localhost) or change `DB_HOST=localhost` in `.env`

### 2. Build and Run

```bash
# Clone the repository
git clone <repository-url>
cd fraud-detection-engine

# Build and start all services
docker-compose up --build
```

This will:
- Build the application image
- Start PostgreSQL database
- Start the application on port 8080
- Run database migrations automatically

### 3. Verify Running

```bash
# Check health
curl http://localhost:8080/api/v1/transactions/health

# Access API documentation
open http://localhost:8080/swagger-ui.html
```

### 4. Stop Services

```bash
docker-compose down

# To remove volumes as well
docker-compose down -v
```

## Building and Running Locally

### 1. Setup Database

```bash
# Start PostgreSQL (if not using Docker)
docker run -d \
  --name fraud-postgres \
  -e POSTGRES_DB=frauddb \
  -e POSTGRES_USER=frauduser \
  -e POSTGRES_PASSWORD=fraudpass \
  -p 5432:5432 \
  postgres:16-alpine
```

### 2. Build Application

```bash
# Build without tests
./mvnw clean package -DskipTests

# Build with tests
./mvnw clean package
```

### 3. Run Application

```bash
# Using Maven
./mvnw spring-boot:run

# Using JAR
java -jar target/fraud-detection-engine-1.0.0.jar
```

## Testing

### Run All Tests

```bash
./mvnw test
```

### Run Specific Test Classes

```bash
# Unit tests
./mvnw test -Dtest=HighValueTransactionRuleTest
./mvnw test -Dtest=VelocityRuleTest

# Integration tests
./mvnw test -Dtest=FraudDetectionIntegrationTest
```

### Test Coverage

The project includes:
- **Unit tests** for individual fraud rules
- **Controller tests** for API endpoints
- **Integration tests** for end-to-end flows

## API Documentation

### Interactive Documentation

Once running, access Swagger UI at:
```
http://localhost:8080/swagger-ui.html
```

**OpenAPI Spec** (for Postman, code generators):
```
http://localhost:8080/api-docs
```

### Key Endpoints

#### Submit Transaction for Processing

```bash
POST /api/v1/transactions

curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN001",
    "customerId": "CUST001",
    "amount": 75000.00,
    "currency": "ZAR",
    "type": "EFT_TRANSFER",
    "merchantName": "Recipient Bank",
    "location": "Johannesburg, ZA",
    "ipAddress": "192.168.1.1",
    "deviceId": "device-123"
  }'
```

**Response:**
```json
{
  "transactionId": "TXN001",
  "customerId": "CUST001",
  "type": "EFT_TRANSFER",
  "status": "FLAGGED",
  "alertCount": 1,
  "alerts": [
    {
      "id": 1,
      "transactionId": "TXN001",
      "customerId": "CUST001",
      "transactionType": "EFT_TRANSFER",
      "ruleName": "HIGH_VALUE_TRANSACTION",
      "severity": "HIGH",
      "reason": "Transaction amount 75000.00 ZAR exceeds threshold 50000.0 for type EFT_TRANSFER",
      "riskScore": 75,
      "details": {
        "amount": "75000.00",
        "baseThreshold": "50000.0",
        "effectiveThreshold": "50000.0",
        "transactionType": "EFT_TRANSFER",
        "currency": "ZAR"
      },
      "createdAt": "2026-03-06T14:30:00"
    }
  ],
  "message": "Transaction flagged - 1 fraud alert(s) generated"
}
```

#### Get Alerts by Customer

```bash
GET /api/v1/fraud-alerts/customer/{customerId}

curl http://localhost:8080/api/v1/fraud-alerts/customer/CUST001
```

#### Get Alerts by Customer (Paginated)

```bash
GET /api/v1/fraud-alerts/customer/{customerId}/paginated?page=0&size=20

curl "http://localhost:8080/api/v1/fraud-alerts/customer/CUST001/paginated?page=0&size=20&sort=createdAt,desc"
```

**Query Parameters**:
- `page` - Page number (default: 0)
- `size` - Page size (default: 20)
- `sort` - Sort field and direction (default: createdAt,desc)

**Response includes pagination metadata**:
```json
{
  "content": [...],
  "totalElements": 150,
  "totalPages": 8,
  "size": 20,
  "number": 0
}
```

#### Get Recent Alerts

```bash
GET /api/v1/fraud-alerts/recent?hours=24

curl http://localhost:8080/api/v1/fraud-alerts/recent?hours=48
```

#### Get Recent Alerts (Paginated)

```bash
GET /api/v1/fraud-alerts/recent/paginated?hours=24&page=0&size=20

curl "http://localhost:8080/api/v1/fraud-alerts/recent/paginated?hours=24&page=0&size=20&sort=createdAt,desc"
```

**Query Parameters**:
- `hours` - Number of hours to look back (1-720, default: 24)
- `page` - Page number (default: 0)
- `size` - Page size (default: 20)
- `sort` - Sort field and direction (default: createdAt,desc)

## Configuration

### Application Properties

Configure via environment variables or `application.yml`:

```yaml
# Database Configuration (use environment variables in production)
DB_HOST: localhost
DB_PORT: 5432
DB_NAME: frauddb
DB_USER: frauduser
DB_PASSWORD: fraudpass

# Fraud Rule Configuration
fraud.rules.high-value-threshold: 50000.00
fraud.rules.velocity.max-transactions-per-hour: 10
fraud.rules.velocity.max-amount-per-hour: 100000.00
fraud.rules.unusual-time.start-hour: 2
fraud.rules.unusual-time.end-hour: 5
fraud.rules.unusual-time.significant-amount: 10000.00
```

### Transaction Types

The system uses banking transaction types based on **SARB payment systems** for proper fraud detection:

```
SARB Payment Rails (South African Reserve Bank):
- EFT_TRANSFER: Electronic Funds Transfer via BankServ (1-3 days, reversible, MEDIUM RISK)
- RTC_TRANSFER: Real-Time Clearing - instant payments <R5M (irrevocable, HIGH RISK)
- RTGS_TRANSFER: Real-Time Gross Settlement - SAMOS high-value >R5M (CRITICAL RISK)
- INTERNAL_TRANSFER: Transfer within same bank (LOW RISK)
- INTERNATIONAL_TRANSFER: Cross-border via SWIFT (CRITICAL RISK)

Card Transactions:
- CARD_PURCHASE_POS: In-person card purchase with chip/swipe (MEDIUM RISK)
- CARD_PURCHASE_ONLINE: Online card-not-present purchase (HIGH RISK)
- CARD_CASH_ADVANCE: Cash advance from card (HIGH RISK)

Automated/Recurring:
- DEBIT_ORDER: Pre-authorized recurring payment (LOW RISK)
- SALARY_DEPOSIT: Payroll deposit (LOW RISK)
- RECURRING_PAYMENT: Standing order (LOW RISK)

Mobile/Digital:
- MOBILE_TRANSFER: P2P mobile money (MEDIUM RISK)
- P2P_PAYMENT: Instant payment (Zapper, SnapScan, MEDIUM RISK)
- QR_CODE_PAYMENT: QR code scan payment (MEDIUM RISK)

... and 8 more banking operation types (total: 22 types)
```

## Project Structure

```
src/
├── main/
│   ├── java/za/co/capitec/fraud/
│   │   ├── config/               # Configuration & filters
│   │   ├── controller/           # REST controllers
│   │   ├── domain/               # JPA entities
│   │   ├── dto/                  # Data transfer objects
│   │   ├── engine/               # Fraud detection engine
│   │   │   └── rules/            # Fraud rule implementations
│   │   ├── exception/            # Exception handling
│   │   ├── repository/           # Data repositories
│   │   ├── service/              # Service layer
│   │   └── util/                 # Utility classes
│   └── resources/
│       ├── application.yml       # Configuration
│       └── db/changelog/         # Liquibase migrations
└── test/
    └── java/za/co/capitec/fraud/
        ├── controller/           # Controller tests
        ├── engine/rules/         # Rule unit tests
        └── integration/          # Integration tests
```

## Fraud Rules Explained

### 1. High-Value Transaction Rule (Type-Aware)
- **Base Threshold**: Configurable (default: R50,000)
- **Severity**: HIGH
- **Type-Aware Behavior**:
    - **System-Generated** (FEE_CHARGE, INTEREST_CREDIT, REVERSAL): Never flagged (bank-initiated)
    - **Critical Value** (RTGS_TRANSFER): 200× threshold (R10,000,000) - SARB high-value system
    - **Instant Irrevocable** (RTC_TRANSFER): 10× threshold (R500,000) - Real-Time Clearing
    - **Pre-Authorized** (DEBIT_ORDER, RECURRING_PAYMENT, SALARY_DEPOSIT): 5× threshold (R250,000)
    - **Critical Risk** (INTERNATIONAL_TRANSFER, CARD_CASH_ADVANCE): 0.3× threshold (R15,000)
    - **High Risk** (CARD_PURCHASE_ONLINE): 0.5× threshold (R25,000)
    - **Standard** (EFT_TRANSFER, all other types): Base threshold (R50,000)
- **Triggers**: When transaction amount exceeds type-specific threshold
- **Example**: R8M RTGS_TRANSFER → NOT flagged (legitimate high-value), R20k INTERNATIONAL_TRANSFER → FLAGGED (critical risk)

### 2. Velocity Rule
- **Thresholds**:
    - Max 10 transactions per hour
    - Max 100,000 total amount per hour
- **Severity**: CRITICAL
- **Triggers**: Excessive transaction count or amount

### 3. Unusual Time Rule (Type-Aware)
- **Time Window**: 02:00 - 05:00 (configurable)
- **Amount Threshold**: R10,000
- **Severity**: MEDIUM
- **Type-Aware Behavior**:
    - **Automated Types** (DEBIT_ORDER, RECURRING_PAYMENT, FEE_CHARGE, INTEREST_CREDIT, SALARY_DEPOSIT, REVERSAL): Skipped (expected to run at any hour)
    - **User-Initiated Types**: Flagged if significant amount during unusual hours
- **Triggers**: Large user-initiated transactions during unusual hours
- **Example**: R50k DEBIT_ORDER at 03:00 → NOT flagged (automated), R50k EFT_TRANSFER at 03:00 → FLAGGED (user-initiated)

### 4. Rapid Location Change Rule
- **Time Window**: 2 hours
- **Severity**: HIGH
- **Triggers**:
    - Different locations within 2 hours
    - High-risk locations (VPN, Proxy, TOR)

### 5. Repeated Declined Transaction Rule
- **Time Window**: 30 minutes
- **Max Attempts**: 3
- **Severity**: CRITICAL
- **Triggers**: Multiple blocked transactions (card testing)

## Production Considerations

### Security
- Non-root Docker user
- Input validation on all endpoints
- Parameterized SQL queries (JPA)
- Connection pooling with HikariCP

### Performance
- Database indexes on high-query columns
- JPA batch inserts
- Connection pooling
- Efficient query design

### Observability
- Structured logging with SLF4J
- Health check endpoints
- Detailed error responses
- API documentation

### Scalability
- Stateless design
- Horizontal scaling ready
- Database connection pooling
- Async processing ready (future enhancement)

## Security Considerations

### Environment Variables & Secrets Management

**Development/Demo (Current Setup)**:
- Uses `.env` file for local credentials (not committed to git)
- `application.yml` has fallback defaults for ease of testing
- Follows standard Spring Boot development patterns

**Production Deployment**:
- Remove fallback defaults from `application.yml` or use Spring profiles
- Use enterprise secret management (AWS Secrets Manager, HashiCorp Vault, Azure Key Vault)
- Enable fail-fast if required environment variables are missing

```yaml
# Production configuration example
spring:
  datasource:
    password: ${DB_PASSWORD}  # No fallback - fails if not provided
```

**Current Fallback Values**:
```yaml
DB_PASSWORD:fraudpass  # Overridden by .env in docker-compose
DB_USER:frauduser      # Overridden by .env in docker-compose
DB_HOST:localhost      # Overridden by .env in docker-compose
```

These defaults allow standalone Docker runs for demos but should be removed/profiled for production.

### Additional Security Features

- Non-root Docker user (appuser:1001)
- Input validation with Jakarta Bean Validation
- Parameterized queries (SQL injection prevention)
- PII masking in logs (customer IDs)
- Rate limiting configured
- Container resource limits
- HTTPS ready (configure reverse proxy)

## Future Enhancements

- Machine learning-based fraud scoring
- Real-time streaming with Kafka
- Redis caching for velocity checks
- Advanced analytics dashboard
- Multi-region deployment support
- Webhook notifications for critical alerts
- Batch transaction processing
- Historical pattern analysis

## Troubleshooting

### Database Connection Issues

```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Check logs
docker logs fraud-detection-db
```

### Application Won't Start

```bash
# Check application logs
docker logs fraud-detection-app

# Verify database is healthy
docker-compose ps
```

