# Phase 01 Implementation Plan - PayMe Core (Invoices)
## Current State
Phase 0 is complete:
* Spring Boot application running on port 8080
* PostgreSQL database connection established via Docker Compose
* Health endpoints operational (`/health` and `/actuator/health`)
* Basic project structure in place with `backend/src/main/java/com/payme/` containing `PaymeApplication.java` and `api/HealthController.java`
## Phase 01 Goal
Build the core invoice functionality without payment gateway integration. Users should be able to create invoices, query their status, and access a basic pay page. The domain model will enforce business rules including invoice expiry and valid state transitions.
## Implementation Strategy
We'll follow the **layered architecture** (Domain → Ports → Application → Adapters → API) as specified in the build spec. This ensures clean separation of concerns and testability.
### Layer-by-layer approach:
1. **Domain layer** (pure business logic, no Spring dependencies)
2. **Ports** (interfaces for repositories and utilities)
3. **Adapters** (JPA implementations of ports)
4. **Application layer** (use-cases/orchestration)
5. **API layer** (controllers + DTOs + validation)
## Detailed Implementation Steps
### Step 1: Domain Layer - Value Objects
Create foundational value objects that will be used throughout the domain:
* `Money` - Represents monetary amount with currency
    * Fields: `amount` (BigDecimal), `currency` (Currency enum)
    * Immutable
    * Validation: amount cannot be negative
* `Currency` - Enum for supported currencies
    * Start with: USD, ZAR, EUR, GBP
* `InvoiceId` - Typed ID wrapper
    * Wraps String UUID
    * Factory method for generation
* `MerchantId` - Typed ID wrapper
    * Wraps String UUID
### Step 2: Domain Layer - Invoice Entity
Create the core `Invoice` domain entity:
* **Location**: `backend/src/main/java/com/payme/domain/Invoice.java`
* **Fields**:
    * `invoiceId` (InvoiceId)
    * `merchantId` (MerchantId)
    * `money` (Money)
    * `description` (String)
    * `status` (InvoiceStatus enum)
    * `expiresAt` (Instant)
    * `createdAt` (Instant)
    * `updatedAt` (Instant)
* **InvoiceStatus Enum**: `CREATED`, `PENDING`, `SUCCEEDED`, `FAILED`, `EXPIRED`
* **Business methods**:
    * `isPayable()` - Returns true if status is CREATED/PENDING and not expired
    * `isExpired(Instant now)` - Check if invoice has passed expiry
    * `markAsPending(Instant now)` - Transition to PENDING (validates state)
    * `markAsExpired(Instant now)` - Transition to EXPIRED (validates state)
    * State transition validation (prevent invalid transitions)
* **Invariants**:
    * SUCCEEDED is terminal (cannot transition out)
    * Valid transitions: CREATED→PENDING→SUCCEEDED, CREATED→EXPIRED, PENDING→FAILED
    * No backwards transitions
### Step 3: Ports Layer - Repository Interfaces
Define port interfaces that the domain/application will depend on:
* **InvoiceRepository** (`backend/src/main/java/com/payme/ports/InvoiceRepository.java`):
    * `save(Invoice invoice)` → Invoice
    * `findById(InvoiceId id)` → Optional<Invoice>
    * `existsById(InvoiceId id)` → boolean
* **Clock** (`backend/src/main/java/com/payme/ports/Clock.java`):
    * `now()` → Instant
    * Allows deterministic testing with fake clock
### Step 4: Adapters Layer - JPA Entities
Create JPA entities that map domain objects to database tables:
* **InvoiceJpaEntity** (`backend/src/main/java/com/payme/adapters/persistence/jpa/InvoiceJpaEntity.java`):
    * Maps to `invoices` table
    * Fields match Invoice domain entity
    * Use `@Id`, `@Column`, `@Enumerated`, etc.
    * Store IDs as String (UUID)
    * Store Money as two columns: `amount` (BigDecimal), `currency` (String)
* **Converter methods**:
    * `toDomain()` - Convert JPA entity to domain Invoice
    * `fromDomain(Invoice)` - Convert domain Invoice to JPA entity
### Step 5: Adapters Layer - Repository Implementation
Implement the repository port using Spring Data JPA:
* **JpaInvoiceRepository** (Spring Data interface in `adapters/persistence/jpa/`):
    * Extends `JpaRepository<InvoiceJpaEntity, String>`
* **InvoiceRepositoryAdapter** (`adapters/persistence/jpa/InvoiceRepositoryAdapter.java`):
    * Implements `InvoiceRepository` port
    * Delegates to `JpaInvoiceRepository`
    * Handles domain ↔ JPA entity conversion
* **SystemClock** (`backend/src/main/java/com/payme/adapters/time/SystemClock.java`):
    * Implements `Clock` port
    * Returns `Instant.now()`
### Step 6: Application Layer - Use Cases
Create use-case classes that orchestrate domain operations:
* **CreateInvoiceUseCase** (`backend/src/main/java/com/payme/application/CreateInvoiceUseCase.java`):
    * Dependencies: InvoiceRepository, Clock
    * Input: merchantId, amount, currency, description, expiryDurationHours
    * Logic:
        * Generate new InvoiceId
        * Calculate expiresAt (now + duration)
        * Create Invoice domain object (status: CREATED)
        * Save via repository
        * Return Invoice
* **GetInvoiceUseCase** (`backend/src/main/java/com/payme/application/GetInvoiceUseCase.java`):
    * Dependencies: InvoiceRepository, Clock
    * Input: invoiceId
    * Logic:
        * Fetch invoice
        * Check if expired (if CREATED and past expiresAt, mark as EXPIRED)
        * Save if status changed
        * Return Invoice
* **GetPayPageDataUseCase** (`backend/src/main/java/com/payme/application/GetPayPageDataUseCase.java`):
    * Dependencies: InvoiceRepository, Clock
    * Input: invoiceId
    * Logic:
        * Fetch invoice (similar to GetInvoice)
        * Check expiry and update if needed
        * Return pay page data (invoice details + payable status)
### Step 7: API Layer - DTOs
Create data transfer objects for API communication:
* **CreateInvoiceRequest** (`backend/src/main/java/com/payme/api/dto/CreateInvoiceRequest.java`):
    * Fields: merchantId, amount, currency, description, expiryHours
    * Validation: @NotNull, @Positive, @Size, etc.
* **InvoiceResponse** (`backend/src/main/java/com/payme/api/dto/InvoiceResponse.java`):
    * Fields: invoiceId, merchantId, amount, currency, description, status, expiresAt, createdAt, payUrl
    * Factory method: `fromDomain(Invoice, String baseUrl)`
* **PayPageResponse** (`backend/src/main/java/com/payme/api/dto/PayPageResponse.java`):
    * Fields: invoiceId, merchantName (placeholder), amount, currency, description, status, isPayable, expiresAt
### Step 8: API Layer - Controllers
Create REST controllers exposing the endpoints:
* **InvoiceController** (`backend/src/main/java/com/payme/api/InvoiceController.java`):
    * `POST /api/invoices`
        * Accepts CreateInvoiceRequest
        * Calls CreateInvoiceUseCase
        * Returns InvoiceResponse (201 Created)
    * `GET /api/invoices/{invoiceId}`
        * Calls GetInvoiceUseCase
        * Returns InvoiceResponse (200 OK)
        * Returns 404 if not found
* **PayController** (`backend/src/main/java/com/payme/api/PayController.java`):
    * `GET /pay/{invoiceId}`
        * Calls GetPayPageDataUseCase
        * Returns PayPageResponse (200 OK)
        * Returns 404 if not found
### Step 9: Configuration
Create Spring configuration to wire dependencies:
* **ApplicationConfig** (`backend/src/main/java/com/payme/config/ApplicationConfig.java`):
    * Define @Bean for Clock (SystemClock)
    * Define @Bean for use-cases (CreateInvoice, GetInvoice, GetPayPageData)
    * Inject repository adapters
### Step 10: Exception Handling
Add proper error handling:
* **InvoiceNotFoundException** (custom exception in `domain/exceptions/`)
* **InvalidInvoiceStateException** (custom exception in `domain/exceptions/`)
* **GlobalExceptionHandler** (`api/GlobalExceptionHandler.java`):
    * @ControllerAdvice
    * Handle InvoiceNotFoundException → 404
    * Handle InvalidInvoiceStateException → 400
    * Handle validation errors → 400
## Database Schema
Hibernate will auto-create the `invoices` table with ddl-auto=update:
```warp-runnable-command
invoices:
- id (VARCHAR/UUID, PK)
- merchant_id (VARCHAR)
- amount (DECIMAL)
- currency (VARCHAR)
- description (VARCHAR)
- status (VARCHAR)
- expires_at (TIMESTAMP)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)
```
## Testing Strategy
After implementation:
1. **Manual API Testing**:
    * Use curl or Postman to test endpoints
    * Create invoice: `POST /api/invoices`
    * Get invoice: `GET /api/invoices/{id}`
    * Get pay page: `GET /pay/{id}`
2. **Verify Business Rules**:
    * Invoice expiry logic works (create invoice with short expiry, check after time passes)
    * State transitions are enforced
    * Validation works (negative amounts rejected, etc.)
3. **Database Verification**:
    * Check that invoices table is created
    * Verify data is persisted correctly
## Exit Criteria
✅ Can create invoice via `POST /api/invoices` and receive invoice ID + pay URL
✅ Can query invoice status via `GET /api/invoices/{id}`
✅ Can access pay page via `GET /pay/{id}`
✅ Invoice expiry logic correctly marks expired invoices as EXPIRED
✅ Invalid state transitions are prevented
✅ Validation works (rejects invalid inputs)
✅ Data persists correctly in PostgreSQL
## Implementation Order Summary
1. Domain value objects (Money, Currency, IDs)
2. Domain Invoice entity + InvoiceStatus enum
3. Ports (InvoiceRepository, Clock)
4. JPA entities (InvoiceJpaEntity)
5. Repository adapter implementation
6. SystemClock implementation
7. Use-cases (CreateInvoice, GetInvoice, GetPayPageData)
8. DTOs (Request/Response objects)
9. Controllers (InvoiceController, PayController)
10. Configuration beans
11. Exception handling
12. Manual testing and verification
## Notes
* Keep domain layer pure (no Spring annotations)
* Use Lombok for boilerplate reduction (@Getter, @AllArgsConstructor, @Builder, etc.)
* All timestamps use `java.time.Instant`
* IDs are UUIDs stored as Strings
* Money amounts use `BigDecimal` for precision
* Follow naming conventions from build spec
