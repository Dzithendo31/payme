# Load environment variables from .env file
Write-Host "Loading environment variables..." -ForegroundColor Green
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
        Write-Host "  Set $name" -ForegroundColor Gray
    }
}

# Check if Docker is running
Write-Host "`nChecking if Docker is running..." -ForegroundColor Green
try {
    $dockerInfo = docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Docker is not running"
    }
    Write-Host "Docker is running." -ForegroundColor Green
}
catch {
    Write-Host "Docker is not running. Please start Docker Desktop first." -ForegroundColor Red
    exit 1
}

# Start PostgreSQL container
Write-Host "`nStarting PostgreSQL container..." -ForegroundColor Green
Set-Location infra
docker-compose up -d postgres
Set-Location ..

# Wait for PostgreSQL to be ready
Write-Host "`nWaiting for PostgreSQL to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# Start Maven application
Write-Host "`nStarting Maven application..." -ForegroundColor Green
Set-Location backend
mvn spring-boot:run
