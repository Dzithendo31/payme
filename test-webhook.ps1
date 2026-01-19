# Test Webhook Flow End-to-End
# This script tests Phase 03 webhook implementation

$baseUrl = "http://localhost:8080"

Write-Host "=== PayMe Webhook Flow Test ===" -ForegroundColor Cyan
Write-Host ""

# Step 1: Create an invoice
Write-Host "Step 1: Creating invoice..." -ForegroundColor Yellow
$createInvoicePayload = @{
    merchantId = "merchant_123"
    amount = 1000
    currency = "USD"
    description = "Test webhook payment"
    expiryHours = 24
} | ConvertTo-Json

try {
    $invoiceResponse = Invoke-RestMethod -Uri "$baseUrl/api/invoices" -Method Post -Body $createInvoicePayload -ContentType "application/json"
    $invoiceId = $invoiceResponse.invoiceId
    Write-Host "✓ Invoice created: $invoiceId" -ForegroundColor Green
    Write-Host "  Status: $($invoiceResponse.status)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Failed to create invoice: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Step 2: Start checkout to create payment attempt
Write-Host "Step 2: Starting checkout (creates PaymentAttempt)..." -ForegroundColor Yellow
try {
    $checkoutResponse = Invoke-RestMethod -Uri "$baseUrl/pay/$invoiceId" -Method Get
    $checkoutUrl = $checkoutResponse.checkoutUrl
    
    # Extract provider reference from the fake checkout URL
    # Format: http://fake-gateway.local/checkout/{attemptId}
    $attemptId = $checkoutUrl.Split('/')[-1]
    $providerReference = "fake_ref_$attemptId"
    
    Write-Host "✓ Checkout started" -ForegroundColor Green
    Write-Host "  Checkout URL: $checkoutUrl" -ForegroundColor Gray
    Write-Host "  Provider Reference: $providerReference" -ForegroundColor Gray
} catch {
    Write-Host "✗ Failed to start checkout: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Step 3: Send successful payment webhook
Write-Host "Step 3: Sending successful payment webhook..." -ForegroundColor Yellow
$webhookPayload = @{
    eventId = "evt_test_" + (Get-Date -Format "yyyyMMddHHmmss")
    type = "payment.succeeded"
    reference = $providerReference
    invoiceId = $invoiceId
} | ConvertTo-Json

Write-Host "  Webhook payload: $webhookPayload" -ForegroundColor Gray

try {
    $webhookResponse = Invoke-RestMethod -Uri "$baseUrl/webhooks/fake" -Method Post -Body $webhookPayload -ContentType "application/json"
    Write-Host "✓ Webhook processed: $($webhookResponse.status)" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to process webhook: $_" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Step 4: Verify invoice status updated to SUCCEEDED
Write-Host "Step 4: Verifying invoice status..." -ForegroundColor Yellow
try {
    $updatedInvoice = Invoke-RestMethod -Uri "$baseUrl/api/invoices/$invoiceId" -Method Get
    Write-Host "✓ Invoice retrieved" -ForegroundColor Green
    Write-Host "  Status: $($updatedInvoice.status)" -ForegroundColor Gray
    
    if ($updatedInvoice.status -eq "SUCCEEDED") {
        Write-Host "  ✓ Invoice status correctly updated to SUCCEEDED!" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Expected SUCCEEDED but got $($updatedInvoice.status)" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ Failed to get invoice: $_" -ForegroundColor Red
}

Write-Host ""

# Step 5: Test duplicate webhook (should be ignored)
Write-Host "Step 5: Testing duplicate webhook detection..." -ForegroundColor Yellow
try {
    $duplicateResponse = Invoke-RestMethod -Uri "$baseUrl/webhooks/fake" -Method Post -Body $webhookPayload -ContentType "application/json"
    Write-Host "✓ Duplicate webhook handled: $($duplicateResponse.status)" -ForegroundColor Green
    Write-Host "  (Should be processed but marked as duplicate in database)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Failed to handle duplicate: $_" -ForegroundColor Red
}

Write-Host ""

# Step 6: Test failed payment webhook on a new invoice
Write-Host "Step 6: Testing failed payment webhook..." -ForegroundColor Yellow

# Create new invoice
$createInvoicePayload2 = @{
    merchantId = "merchant_123"
    amount = 500
    currency = "USD"
    description = "Test failed payment"
    expiryHours = 24
} | ConvertTo-Json

try {
    $invoiceResponse2 = Invoke-RestMethod -Uri "$baseUrl/api/invoices" -Method Post -Body $createInvoicePayload2 -ContentType "application/json"
    $invoiceId2 = $invoiceResponse2.invoiceId
    Write-Host "✓ Second invoice created: $invoiceId2" -ForegroundColor Green
    
    # Start checkout
    $checkoutResponse2 = Invoke-RestMethod -Uri "$baseUrl/pay/$invoiceId2" -Method Get
    $attemptId2 = $checkoutResponse2.checkoutUrl.Split('/')[-1]
    $providerReference2 = "fake_ref_$attemptId2"
    
    # Send failed webhook
    $failedWebhook = @{
        eventId = "evt_fail_" + (Get-Date -Format "yyyyMMddHHmmss")
        type = "payment.failed"
        reference = $providerReference2
        invoiceId = $invoiceId2
    } | ConvertTo-Json
    
    $failedResponse = Invoke-RestMethod -Uri "$baseUrl/webhooks/fake" -Method Post -Body $failedWebhook -ContentType "application/json"
    Write-Host "✓ Failed payment webhook processed" -ForegroundColor Green
    
    # Verify status
    $failedInvoice = Invoke-RestMethod -Uri "$baseUrl/api/invoices/$invoiceId2" -Method Get
    Write-Host "  Invoice status: $($failedInvoice.status)" -ForegroundColor Gray
    
    if ($failedInvoice.status -eq "FAILED") {
        Write-Host "  ✓ Invoice correctly marked as FAILED!" -ForegroundColor Green
    } else {
        Write-Host "  ✗ Expected FAILED but got $($failedInvoice.status)" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ Failed payment test error: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
