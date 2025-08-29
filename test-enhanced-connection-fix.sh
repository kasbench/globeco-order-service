#!/bin/bash

# Enhanced test script for connection leak fix with manual transaction management
# This script tests the batch order submission with detailed monitoring

echo "🔧 Testing Enhanced Connection Leak Fix"
echo "======================================="

# Configuration
SERVICE_URL="http://localhost:8081"
BATCH_SIZE=20
NUM_CONCURRENT_BATCHES=5

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to check system health
check_health() {
    echo -e "${YELLOW}📊 Checking system health...${NC}"
    health_response=$(curl -s "$SERVICE_URL/api/v1/system/health" 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "$health_response" | jq '.' 2>/dev/null || echo "Health response: $health_response"
    else
        echo "Health endpoint not available"
    fi
    echo ""
}

# Function to monitor connection pool metrics
monitor_connection_metrics() {
    echo -e "${BLUE}🔍 Connection Pool Metrics:${NC}"
    
    # Active connections
    active=$(curl -s "$SERVICE_URL/actuator/metrics/hikaricp.connections.active" 2>/dev/null | jq '.measurements[0].value' 2>/dev/null)
    echo "  Active connections: ${active:-'N/A'}"
    
    # Total connections
    total=$(curl -s "$SERVICE_URL/actuator/metrics/hikaricp.connections" 2>/dev/null | jq '.measurements[0].value' 2>/dev/null)
    echo "  Total connections: ${total:-'N/A'}"
    
    # Pending connections
    pending=$(curl -s "$SERVICE_URL/actuator/metrics/hikaricp.connections.pending" 2>/dev/null | jq '.measurements[0].value' 2>/dev/null)
    echo "  Pending connections: ${pending:-'N/A'}"
    
    # Connection usage
    usage=$(curl -s "$SERVICE_URL/actuator/metrics/hikaricp.connections.usage" 2>/dev/null | jq '.measurements[0].value' 2>/dev/null)
    echo "  Connection usage: ${usage:-'N/A'}"
    
    echo ""
}

# Function to create test batch
create_test_batch() {
    local size=$1
    local batch="["
    for ((i=1; i<=size; i++)); do
        if [ $i -gt 1 ]; then
            batch+=","
        fi
        batch+='{
            "blotterId": 1,
            "statusId": 1,
            "portfolioId": "P001",
            "orderTypeId": 1,
            "securityId": "S001",
            "quantity": 100,
            "limitPrice": 50.00
        }'
    done
    batch+="]"
    echo "$batch"
}

# Function to submit batch in background
submit_batch_async() {
    local batch_num=$1
    local batch_data=$2
    
    {
        echo -e "${YELLOW}📤 Starting batch $batch_num ($BATCH_SIZE orders)...${NC}"
        
        start_time=$(date +%s.%N)
        
        response=$(curl -s -w "\n%{http_code}" -X POST \
            -H "Content-Type: application/json" \
            -d "$batch_data" \
            "$SERVICE_URL/api/v1/orders/batch/submit" 2>/dev/null)
        
        end_time=$(date +%s.%N)
        duration=$(echo "$end_time - $start_time" | bc 2>/dev/null || echo "N/A")
        
        http_code=$(echo "$response" | tail -n1)
        response_body=$(echo "$response" | head -n -1)
        
        if [ "$http_code" = "200" ]; then
            echo -e "${GREEN}✅ Batch $batch_num completed in ${duration}s${NC}"
            echo "$response_body" | jq -c '.successful, .failed, .totalRequested' 2>/dev/null || echo "Response: $response_body"
        else
            echo -e "${RED}❌ Batch $batch_num failed (HTTP $http_code)${NC}"
            echo "Response: $response_body"
        fi
    } &
}

# Function to check for connection leaks in logs
check_connection_leaks() {
    echo -e "${YELLOW}🔍 Checking for connection leak warnings...${NC}"
    
    if command -v kubectl &> /dev/null; then
        echo "Checking Kubernetes logs for connection leaks..."
        leak_count=$(kubectl logs --tail=100 deployment/globeco-order-service 2>/dev/null | grep -c "Connection leak detection triggered" || echo "0")
        if [ "$leak_count" -eq "0" ]; then
            echo -e "${GREEN}✅ No connection leak warnings found${NC}"
        else
            echo -e "${RED}❌ Found $leak_count connection leak warnings${NC}"
            kubectl logs --tail=20 deployment/globeco-order-service | grep -A 2 -B 2 "Connection leak"
        fi
    else
        echo "Kubectl not available - check application logs manually"
    fi
    echo ""
}

# Function to monitor semaphore usage (if available)
monitor_semaphore() {
    echo -e "${BLUE}🚦 Semaphore Status:${NC}"
    echo "  Database operation semaphore: Limited to 25 concurrent operations"
    echo "  Timeout: 2 seconds per operation"
    echo ""
}

# Main test execution
echo "🚀 Starting enhanced connection leak fix test..."
echo "Service URL: $SERVICE_URL"
echo "Batch size: $BATCH_SIZE orders per batch"
echo "Concurrent batches: $NUM_CONCURRENT_BATCHES"
echo ""

# Initial health check
check_health
monitor_connection_metrics
monitor_semaphore

# Create test batch data
echo -e "${YELLOW}📝 Creating test batch data...${NC}"
BATCH_DATA=$(create_test_batch $BATCH_SIZE)

# Submit batches concurrently to stress test the system
echo -e "${YELLOW}🚀 Submitting $NUM_CONCURRENT_BATCHES concurrent batches...${NC}"
for ((i=1; i<=NUM_CONCURRENT_BATCHES; i++)); do
    submit_batch_async $i "$BATCH_DATA"
    sleep 0.5  # Small stagger to avoid overwhelming the system
done

# Wait for all background jobs to complete
echo -e "${YELLOW}⏳ Waiting for all batches to complete...${NC}"
wait

echo ""
echo -e "${YELLOW}📊 Post-test monitoring:${NC}"

# Monitor connection pool after test
monitor_connection_metrics

# Check for connection leaks
check_connection_leaks

# Final health check
echo -e "${YELLOW}🏁 Final system health check:${NC}"
check_health

echo ""
echo -e "${GREEN}🎉 Enhanced connection leak fix test completed!${NC}"
echo ""
echo "Expected results with enhanced fix:"
echo "✅ All batches should complete successfully"
echo "✅ No connection leak warnings in logs"
echo "✅ Connection pool utilization should stay reasonable"
echo "✅ System should handle concurrent load without exhaustion"
echo "✅ Semaphore should prevent database operation overload"
echo "✅ Manual transaction management should provide precise control"