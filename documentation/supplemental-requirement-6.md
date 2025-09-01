# Supplemental Requirement 6: Send orders to the transaction service in bulk

The `SubmitOrdersBatch` method in `OrderController` sends orders to the transaction service one-by-one.  It calls `SubmitOrdersBatch` in `OrderService`, which calls `SubmitIndividualOrderInTransaction`, which calls `SubmitIndividualOrder`.  `SubmitIndividualOrderInTransaction` accomplishes nothing as it is not a transaction.  The transactional annotation was removed previously to avoid long running transactions.

The transaction service has a batch endpoint that accepts a list of orders: POST /api/v1/tradeOrders/batch/submit.  Here is the OpenAPI schema for the API:

    
    "/api/v1/tradeOrders/batch/submit": {
      "post": {
        "tags": [
          "Trade Orders Batch"
        ],
        "summary": "Submit multiple trade orders in batch",
        "description": "Submit up to 100 trade orders in a single batch operation with parallel processing. Returns detailed results for each submission including success/failure status and execution details.",
        "operationId": "submitTradeOrdersBatch",
        "parameters": [
          {
            "name": "noExecuteSubmit",
            "in": "query",
            "description": "When false (default), automatically submits to execution service; when true, only creates local executions",
            "required": false,
            "schema": {
              "type": "boolean",
              "default": false
            }
          }
        ],
        "requestBody": {
          "content": {
            "application/json": {
              "schema": {
                "$ref": "#/components/schemas/BatchSubmitRequestDTO"
              }
            }
          },
          "required": true
        },
        "responses": {
          "200": {
            "description": "All trade orders submitted successfully",
            "content": {
              "*/*": {
                "schema": {
                  "$ref": "#/components/schemas/BatchSubmitResponseDTO"
                }
              }
            }
          },
          "207": {
            "description": "Partial success - some trade orders submitted successfully",
            "content": {
              "*/*": {
                "schema": {
                  "$ref": "#/components/schemas/BatchSubmitResponseDTO"
                }
              }
            }
          },
          "400": {
            "description": "Invalid request format or batch size exceeded",
            "content": {
              "*/*": {
                "schema": {
                  "$ref": "#/components/schemas/ErrorResponse"
                }
              }
            }
          },
          "413": {
            "description": "Payload too large - batch size exceeds 100 items",
            "content": {
              "*/*": {
                "schema": {
                  "$ref": "#/components/schemas/ErrorResponse"
                }
              }
            }
          },
          "500": {
            "description": "Internal server error",
            "content": {
              "*/*": {
                "schema": {
                  "$ref": "#/components/schemas/ErrorResponse"
                }
              }
            }
          }
        }
      }
    }
    

Request DTO

    
    "BatchSubmitRequestDTO": {
        "type": "object",
        "description": "Batch submission request containing trade order submissions",
        "properties": {
          "submissions": {
            "type": "array",
            "items": {
              "$ref": "#/components/schemas/TradeOrderSubmissionDTO"
            },
            "maxItems": 100,
            "minItems": 1
          }
        },
        "required": [
          "submissions"
        ]
      }
    
Response DTO

      "BatchSubmitResponseDTO": {
        "type": "object",
        "properties": {
          "status": {
            "type": "string",
            "enum": [
              "SUCCESS",
              "PARTIAL",
              "FAILURE"
            ]
          },
          "message": {
            "type": "string"
          },
          "totalRequested": {
            "type": "integer",
            "format": "int32"
          },
          "successful": {
            "type": "integer",
            "format": "int32"
          },
          "failed": {
            "type": "integer",
            "format": "int32"
          },
          "results": {
            "type": "array",
            "items": {
              "$ref": "#/components/schemas/TradeOrderSubmitResultDTO"
            }
          }
        }
      }

The full [Trade Service OpenAPI Schema](trade-service-openapi.yaml) is available [here](trade-service-openapi.yaml).



This enhancement is to simplify the current implementation of `SubmitOrdersBatch` by sending the batch to the trade service using the POST /api/v1/tradeOrders/batch/submit API of the trade service as described above.  Speeding up processing is the primary objective.  The current process has too much overhead and is way too slow.

Important Instructions:
- I will perform the integration testing in Kubernetes.  The APIs and databases are not exposed outside of Kubernetes.  Don't waste time trying to perform integration tests.  Turn it over to me.  I can quickly run the tests.
- Log any errors received from the trade service in as much detail as you receive.  This will assist greatly with debugging.
