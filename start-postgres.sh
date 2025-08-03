#!/bin/bash

# Start PostgreSQL using Docker for local testing
echo "Starting PostgreSQL database for local testing..."

docker run --name globeco-postgres \
  -e POSTGRES_DB=postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -d postgres:15

echo "PostgreSQL started on localhost:5432"
echo "Database: postgres"
echo "Username: postgres" 
echo "Password: postgres"
echo ""
echo "To stop: docker stop globeco-postgres"
echo "To remove: docker rm globeco-postgres"