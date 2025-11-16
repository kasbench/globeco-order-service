#!/bin/bash

# Test script to verify circuit breaker returns 503 instead of 400

echo "Testing Circuit Breaker Status Code Fix"
echo "========================================"
echo ""

# Check if service is running
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "❌ Service is not running on localhost:8080"
    echo "Please start the service first with: ./gradlew bootRun"
    exit 1
fi

echo "✓ Service is running"
echo ""

# Test 1: Normal batch submission (should work)
echo "Test 1: Normal batch submission"
echo "--------------------------------"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '[
    {
      "securityId": 1,
      "portfolioId": 1,
      "orderType": "BUY",
      "quantity": 100,
      "price": 150.00
    }
  ]')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "207" ]; then
    echo "✓ Normal submission works (HTTP $HTTP_CODE)"
else
    echo "⚠ Unexpected status code: $HTTP_CODE"
fi
echo ""

# Test 2: Check circuit breaker metrics
echo "Test 2: Circuit Breaker State"
echo "------------------------------"
CB_STATE=$(curl -s http://localhost:8080/actuator/metrics/circuit.breaker.state | grep -o '"value":[0-9]' | cut -d: -f2)

if [ -z "$CB_STATE" ]; then
    echo "⚠ Circuit breaker metrics not available"
else
    case $CB_STATE in
        0)
            echo "✓ Circuit breaker is CLOSED (normal operation)"
            ;;
        1)
            echo "⚠ Circuit breaker is OPEN (system overloaded)"
            echo ""
            echo "Test 3: Verify 503 status code during overload"
            echo "-----------------------------------------------"
            
            OVERLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8080/api/v1/orders \
              -H "Content-Type: application/json" \
              -d '[{"securityId": 1, "portfolioId": 1, "orderType": "BUY", "quantity": 100, "price": 150.00}]')
            
            OVERLOAD_HTTP_CODE=$(echo "$OVERLOAD_RESPONSE" | tail -n1)
            OVERLOAD_BODY=$(echo "$OVERLOAD_RESPONSE" | head -n-1)
            
            if [ "$OVERLOAD_HTTP_CODE" = "503" ]; then
                echo "✓ Returns 503 Service Unavailable (correct!)"
                echo ""
                echo "Response body:"
                echo "$OVERLOAD_BODY" | jq '.' 2>/dev/null || echo "$OVERLOAD_BODY"
                
                # Check for Retry-After header
                RETRY_AFTER=$(curl -s -I -X POST http://localhost:8080/api/v1/orders \
                  -H "Content-Type: application/json" \
                  -d '[]' | grep -i "retry-after" | cut -d: -f2 | tr -d ' \r')
                
                if [ -n "$RETRY_AFTER" ]; then
                    echo ""
                    echo "✓ Retry-After header present: $RETRY_AFTER seconds"
                fi
            elif [ "$OVERLOAD_HTTP_CODE" = "400" ]; then
                echo "❌ Returns 400 Bad Request (WRONG - should be 503!)"
                echo ""
                echo "Response body:"
                echo "$OVERLOAD_BODY" | jq '.' 2>/dev/null || echo "$OVERLOAD_BODY"
            else
                echo "⚠ Unexpected status code: $OVERLOAD_HTTP_CODE"
            fi
            ;;
        2)
            echo "⚠ Circuit breaker is HALF_OPEN (testing recovery)"
            ;;
        *)
            echo "⚠ Unknown circuit breaker state: $CB_STATE"
            ;;
    esac
fi

echo ""
echo "========================================"
echo "Test completed"
echo ""
echo "Note: To trigger circuit breaker for testing:"
echo "1. Submit large batches to exhaust connection pool"
echo "2. Monitor circuit breaker state with:"
echo "   watch -n 1 'curl -s http://localhost:8080/actuator/metrics/circuit.breaker.state'"
