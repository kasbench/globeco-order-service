#!/bin/bash

# Test script to verify connection leak fix
# This script tests the batch order submission with monitoring

echo "🔧 Testing Connection Leak Fix"
echo "================================"

# Configuration
SERVICE_URL="http://localhost:8081"
BATCH_SIZE=10
NUM_BATCHES=5

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check system health
check_health() {
    echo -e "${YELLOW}📊 Checking system health...${NC}"
    curl -s "$SERVICE_URL/api/v1/system/health" | jq '.' || echo "Health endpoint not available"
    echo ""
}

# Function to monitor connection pool
monitor_connections() {
    echo -e "${YELLOW}🔍 Monitoring connection pool...${NC}"
    curl -s "$SERVICE_URL/actuator/metrics/hikaricp.connections.active" | jq '.measurements[0].value' 2>/dev/null || echo "Metrics not available"
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

# Function to submit batch and measure time
submit_batch() {
    local batch_num=$1
    local batch_data=$2
    
    echo -e "${YELLOW}📤 Submitting batch $batch_num ($BATCH_SIZE orders)...${NC}"
    
    start_time=$(date +%s.%N)
    
    response=$(curl -s -w "\n%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d "$batch_data" \
        "$SERVICE_URL/api/v1/orders/batch/submit")
    
    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    
    http_code=$(echo "$response" | tail -n1)
    response_body=$(echo "$response" | head -n -1)
    
    if [ "$http_code" = "200" ]; then
        echo -e "${GREEN}✅ Batch $batch_num completed in ${duration}s${NC}"
        echo "$response_body" | jq '.successful, .failed, .totalRequested' 2>/dev/null || echo "Response: $response_body"
    else
        echo -e "${RED}❌ Batch $batch_num failed (HTTP $http_code)${NC}"
        echo "Response: $response_body"
    fi
    
    echo ""
}

# Main test execution
echo "🚀 Starting connection leak fix test..."
echo "Service URL: $SERVICE_URL"
echo "Batch size: $BATCH_SIZE orders"
echo "Number of batches: $NUM_BATCHES"
echo ""

# Initial health check
check_health

# Create test batch data
echo -e "${YELLOW}📝 Creating test batch data...${NC}"
BATCH_DATA=$(create_test_batch $BATCH_SIZE)

# Submit batches sequentially
for ((i=1; i<=NUM_BATCHES; i++)); do
    submit_batch $i "$BATCH_DATA"
    
    # Monitor connections after each batch
    echo -e "${YELLOW}Active connections:${NC} $(monitor_connections)"
    
    # Small delay between batches
    sleep 2
done

# Final health check
echo -e "${YELLOW}🏁 Final system health check:${NC}"
check_health

# Check for connection leaks in logs
echo -e "${YELLOW}🔍 Checking for connection leak warnings in recent logs...${NC}"
if command -v kubectl &> /dev/null; then
    echo "Checking Kubernetes logs..."
    kubectl logs --tail=50 deployment/globeco-order-service | grep -i "connection leak" || echo -e "${GREEN}✅ No connection leak warnings found${NC}"
else
    echo "Kubectl not available - check application logs manually for 'connection leak' warnings"
fi

echo ""
echo -e "${GREEN}🎉 Connection leak fix test completed!${NC}"
echo ""
echo "Expected results:"
echo "✅ All batches should complete successfully"
echo "✅ No connection leak warnings in logs"
echo "✅ Active connections should return to baseline after processing"
echo "✅ System health should remain 'HEALTHY'"