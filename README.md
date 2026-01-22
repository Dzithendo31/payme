# PayMe - Payment Link Platform

PayMe is a payment platform that allows merchants to create invoices and share payment links with customers. Built with a focus on clean architecture, webhook-driven status updates, and support for multiple payment gateways.

## Features

- **Invoice Management**: Create and manage payment invoices
- **Hosted Checkout**: Redirect customers to secure payment pages
- **Webhook-Driven Updates**: Invoice status updated via verified webhook notifications
- **Multiple Payment Providers**: Pluggable architecture supports multiple gateways
- **Audit Trail**: Complete tracking of all webhook events and payment attempts
- **Idempotent Webhooks**: Automatic deduplication prevents double-processing

## Supported Payment Providers

### PayFast (South Africa)
- Credit/debit cards
- Instant EFT
- SnapScan, Mobicred, and more
- **Status**: ✅ Fully integrated

### Fake Provider (Testing)
- Test provider for development
- No external dependencies
- **Status**: ✅ Available for testing

## Architecture

PayMe follows Clean/Hexagonal Architecture principles:

```
┌─────────────────────────────────────────────────────┐
│                    API Layer                        │
│  (Controllers, DTOs, HTTP handling)                 │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│              Application Layer                      │
│  (Use Cases: CreateInvoice, ProcessWebhook, etc.)  │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│                 Domain Layer                        │
│  (Invoice, PaymentAttempt, Business Rules)          │
└─────────────────────────────────────────────────────┘
                        ↑
┌─────────────────────────────────────────────────────┐
│            Ports (Interfaces)                       │
│  (Repositories, PaymentProvider, etc.)              │
└─────────────────────────────────────────────────────┘
                        ↑
┌─────────────────────────────────────────────────────┐
│              Adapters Layer                         │
│  (JPA, PayFast, FakeProvider, etc.)                 │
└─────────────────────────────────────────────────────┘
```

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.x
- **Database**: PostgreSQL 16
- **Payment Gateway**: PayFast (South Africa)
- **Build Tool**: Maven
- **Testing**: JUnit, Testcontainers (planned)

## Quick Start

### Prerequisites

- Java 17+
- PostgreSQL 16
- Docker & Docker Compose (for database)
- Maven (or use included wrapper)

### 1. Start Database

```bash
docker compose -f infra/docker-compose.yml up -d
```

### 2. Configure Payment Provider

#### Option A: Use Fake Provider (for testing)

No configuration needed - this is the default.

#### Option B: Use PayFast

Set environment variables:

```bash
export PAYMENT_PROVIDER=PAYFAST
export PAYFAST_MERCHANT_ID=your_merchant_id
export PAYFAST_MERCHANT_KEY=your_merchant_key
export PAYFAST_PASSPHRASE=your_passphrase
export PAYFAST_SANDBOX=true
export PAYFAST_NOTIFY_URL=http://localhost:8080/webhooks/PAYFAST
```

See [PayFast Setup Guide](docs/PAYFAST_SETUP.md) for detailed instructions.

### 3. Run Application

#### Windows (PowerShell):
```powershell
.\backend\mvnw.cmd spring-boot:run
```

#### Linux/Mac:
```bash
./backend/mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`

### 4. Test the API

#### Create an Invoice
```bash
curl -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -d '{
    "merchantId": "merchant_001",
    "amount": 100.00,
    "currency": "ZAR",
    "description": "Test Payment",
    "expiresAt": "2026-12-31T23:59:59Z"
  }'
```

#### Get Invoice Status
```bash
curl http://localhost:8080/api/invoices/{invoiceId}
```

#### Access Payment Page
```
http://localhost:8080/pay/{invoiceId}
```

## API Endpoints

### Merchant API (Invoice Management)

- `POST /api/invoices` - Create new invoice
- `GET /api/invoices/{id}` - Get invoice status

### Customer API (Payment Flow)

- `GET /pay/{invoiceId}` - Payment page / initiate checkout

### Webhook API (Payment Gateway)

- `POST /webhooks/{provider}` - Receive payment notifications
  - `/webhooks/FAKE` - For fake provider
  - `/webhooks/PAYFAST` - For PayFast

### Health Check

- `GET /health` - Application health status
- `GET /actuator/health` - Detailed health information

## Environment Variables

### Database
- `SPRING_DATASOURCE_URL` - Database connection URL (default: `jdbc:postgresql://localhost:5432/payme`)
- `SPRING_DATASOURCE_USERNAME` - Database username (default: `payme`)
- `SPRING_DATASOURCE_PASSWORD` - Database password (default: `payme`)

### Payment Provider
- `PAYMENT_PROVIDER` - Provider to use: `FAKE` or `PAYFAST` (default: `FAKE`)

### PayFast Configuration
- `PAYFAST_MERCHANT_ID` - PayFast merchant ID
- `PAYFAST_MERCHANT_KEY` - PayFast merchant key
- `PAYFAST_PASSPHRASE` - PayFast passphrase (optional but recommended)
- `PAYFAST_SANDBOX` - Use sandbox mode: `true` or `false` (default: `true`)
- `PAYFAST_NOTIFY_URL` - Webhook URL for ITN notifications

### Checkout URLs
- `PAYME_CHECKOUT_SUCCESS_URL` - Redirect URL after successful payment
- `PAYME_CHECKOUT_CANCEL_URL` - Redirect URL after cancelled payment

## Project Structure

```
payme/
├── backend/                 # Java/Spring Boot application
│   ├── src/main/java/com/payme/
│   │   ├── api/            # REST controllers & DTOs
│   │   ├── application/    # Use cases / services
│   │   ├── domain/         # Domain entities & business logic
│   │   ├── ports/          # Interfaces (repository, provider)
│   │   ├── adapters/       # Implementations (JPA, PayFast, etc.)
│   │   └── config/         # Spring configuration
│   └── src/main/resources/
│       └── application.yml
├── infra/                   # Infrastructure (Docker Compose)
│   └── docker-compose.yml
├── docs/                    # Documentation
│   └── PAYFAST_SETUP.md
└── README.md
```



## Testing

### Unit Tests
```bash
.\backend\mvnw.cmd test
```

### Integration Tests (Planned)
```bash
.\backend\mvnw.cmd verify
```



## Security

- **Webhook Signature Verification**: All webhook notifications are cryptographically verified
- **IP Validation**: Production webhooks validated against known provider IPs
- **HTTPS Required**: Production webhook URLs must use HTTPS
- **Idempotent Processing**: Duplicate webhooks automatically detected and rejected
- **State Machine Validation**: Invalid state transitions are prevented at domain level

## Contributing

This is a personal learning project demonstrating:
- Clean Architecture / Hexagonal Architecture
- Domain-Driven Design principles
- Webhook-driven integrations
- Payment gateway abstractions
- Production-ready patterns (idempotency, audit trails, etc.)

## License

[Add license here]

## Support

For PayFast-specific issues, refer to:
- [PayFast Documentation](https://developers.payfast.co.za/docs)
- [PayFast Setup Guide](docs/PAYFAST_SETUP.md)

For PayMe application issues, check the application logs for detailed error messages.
