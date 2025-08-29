#!/bin/bash

# Emergency Connection Pool Fix Verification Script

echo "🚨 Testing Emergency Connection Pool Fixes"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SERVICE_URL="http://localhost:8081"
HEALTH_ENDPOINT="$SERVICE_URL/api/v1/system/health"
ORDERS_ENDPOINT="$SERVICE_URL/api/v1/orders"

echo -e "${YELLOW}Step 1: Checking if service is running...${NC}"
if curl -s -f "$SERVICE_URL/actuator/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Service is running${NC}"
else
    echo -e "${RED}❌ Service is not running. Please start the service first.${NC}"
    exit 1
fi

echo -e "\n${YELLOW}Step 2: Checking emergency health endpoint...${NC}"
HEALTH_RESPONSE=$(curl -s "$HEALTH_ENDPOINT" 2>/dev/null)
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Health endpoint is working${NC}"
    echo "Health Status:"
    echo "$HEALTH_RESPONSE" | jq '.' 2>/dev/null || echo "$HEALTH_RESPONSE"
else
    echo -e "${RED}❌ Health endpoint not accessible${NC}"
fi

echo -e "\n${YELLOW}Step 3: Testing small batch order creation...${NC}"
SMALL_BATCH='[
    {
        "blotterId": 1,
        "statusId": 1,
        "portfolioId": "TEST001",
        "orderTypeId": 1,
        "securityId": "TEST001",
        "quantity": 100,
        "limitPrice": 50.00
    }
]'

SMALL_BATCH_RESPONSE=$(curl -s -X POST "$ORDERS_ENDPOINT" \
    -H "Content-Type: application/json" \
    -d "$SMALL_BATCH" 2>/dev/null)

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Small batch test successful${NC}"
    echo "Response:"
    echo "$SMALL_BATCH_RESPONSE" | jq '.' 2>/dev/null || echo "$SMALL_BATCH_RESPONSE"
else
    echo -e "${RED}❌ Small batch test failed${NC}"
fi

echo -e "\n${YELLOW}Step 4: Testing medium batch (5 orders)...${NC}"
MEDIUM_BATCH='[
    {"blotterId": 1, "statusId": 1, "portfolioId": "TEST001", "orderTypeId": 1, "securityId": "TEST001", "quantity": 100, "limitPrice": 50.00},
    {"blotterId": 1, "statusId": 1, "portfolioId": "TEST002", "orderTypeId": 1, "securityId": "TEST002", "quantity": 200, "limitPrice": 60.00},
    {"blotterId": 1, "statusId": 1, "portfolioId": "TEST003", "orderTypeId": 1, "securityId": "TEST003", "quantity": 150, "limitPrice": 55.00},
    {"blotterId": 1, "statusId": 1, "portfolioId": "TEST004", "orderTypeId": 1, "securityId": "TEST004", "quantity": 300, "limitPrice": 45.00},
    {"blotterId": 1, "statusId": 1, "portfolioId": "TEST005", "orderTypeId": 1, "securityId": "TEST005", "quantity": 250, "limitPrice": 70.00}
]'

MEDIUM_BATCH_RESPONSE=$(curl -s -X POST "$ORDERS_ENDPOINT" \
    -H "Content-Type: application/json" \
    -d "$MEDIUM_BATCH" 2>/dev/null)

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Medium batch test successful${NC}"
    echo "Response summary:"
    echo "$MEDIUM_BATCH_RESPONSE" | jq '.status, .totalReceived, .successful, .failed' 2>/dev/null || echo "$MEDIUM_BATCH_RESPONSE"
else
    echo -e "${RED}❌ Medium batch test failed${NC}"
fi

echo -e "\n${YELLOW}Step 5: Checking final system health...${NC}"
FINAL_HEALTH=$(curl -s "$HEALTH_ENDPOINT" 2>/dev/null)
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Final health check successful${NC}"
    echo "Connection Pool Status:"
    echo "$FINAL_HEALTH" | jq '.connectionPool' 2>/dev/null || echo "$FINAL_HEALTH"
    
    echo -e "\nCircuit Breaker Status:"
    echo "$FINAL_HEALTH" | jq '.circuitBreaker' 2>/dev/null || echo "$FINAL_HEALTH"
else
    echo -e "${RED}❌ Final health check failed${NC}"
fi

echo -e "\n${YELLOW}=========================================="
echo -e "Emergency Fix Verification Complete"
echo -e "==========================================${NC}"

echo -e "\n${YELLOW}Next Steps:${NC}"
echo "1. If all tests passed, try gradually increasing batch sizes"
echo "2. Monitor the health endpoint during load: curl $HEALTH_ENDPOINT"
echo "3. Watch for connection pool warnings in logs"
echo "4. If issues persist, check the health endpoint for specific problems"

echo -e "\n${YELLOW}Monitoring Commands:${NC}"
echo "Health check: curl $HEALTH_ENDPOINT | jq"
echo "Watch logs: kubectl logs -f deployment/globeco-order-service | grep -E '(Connection|Circuit|CRITICAL|ERROR)'"