#!/bin/bash

# Load environment variables from .env file
echo "Loading environment variables..."
export $(cat .env | grep -v '^#' | xargs)

# Check if Docker is running
echo "Checking if Docker is running..."
if ! docker info > /dev/null 2>&1; then
    echo "Docker is not running. Please start Docker first."
    exit 1
fi

echo "Docker is running."

# Start PostgreSQL container
echo "Starting PostgreSQL container..."
cd infra
docker-compose up -d postgres
cd ..

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to be ready..."
sleep 5

# Start Maven application
echo "Starting Maven application..."
cd backend
mvn spring-boot:run
