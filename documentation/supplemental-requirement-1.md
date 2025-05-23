# Supplemental Requirements

## Cross Origin Resource Sharing (CORS)
* Must allow all origins


## GitHub CI-CD

* Add GitHub CI-CD (actions) to perform a multiarchitecture build (ARM and AMD for Linux) and deploy to DockerHub.  

## Test Containers

* Modify tests to use testcontainers
* Add integration tests using testcontainers.

## New requirement

Create a new API

`POST /api/v1/orders/{id}/submit`

This API calls the the POST /api/v1/tradeOrders API of the globeco-trade-service on port 8082.  The openapi schema for the trade service is at [documentation/trade-service-openapi.yaml](trade-service-openapi.yaml).

Map {id} to tradeOrder.orderId.  All other fields map by name.

If successful, change the order status from "NEW" to "SENT"

Return 200 and status of "submitted" if successful.

Please also generate tests for this new API.

## Update documentation

* Update README.md and openapi.yaml to reflect these changes