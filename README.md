# globeco-order-service
Part of the GlobeCo suite for benchmarking Kubernetes autoscaling.  APIs for managing orders.

## Status Data Model and API Documentation

### Introduction
The **Status** resource in the GlobeCo Order Service defines the possible states an order can have throughout its lifecycle. This API allows clients to list, create, update, and delete status records, which are referenced by orders to indicate their current state.

### Status Data Model
| Field         | Type    | Nullable | Description                                 |
|---------------|---------|----------|---------------------------------------------|
| id            | Integer | No       | Unique identifier for the status            |
| abbreviation  | String  | No       | Short code for the status                   |
| description   | String  | No       | Detailed description of the status          |
| version       | Integer | No       | Optimistic locking version number           |

#### Example Status Values
| abbreviation | description                | version |
|--------------|----------------------------|---------|
| NEW          | New                        | 1       |
| SENT         | Sent                       | 1       |
| WORK         | In progress                | 1       |
| FULL         | Filled                     | 1       |
| PART         | Partial fill               | 1       |
| HOLD         | Hold                       | 1       |
| CNCL         | Cancel                     | 1       |
| CNCLD        | Cancelled                  | 1       |
| CPART        | Cancelled with partial fill| 1       |
| DEL          | Delete                     | 1       |

### Status API Endpoints

| Method | Path                        | Request Body         | Response Body        | Description                       |
|--------|-----------------------------|---------------------|----------------------|-----------------------------------|
| GET    | /api/v1/statuses            |                     | [StatusDTO]          | List all statuses                 |
| GET    | /api/v1/status/{id}         |                     | StatusDTO            | Get a status by ID                |
| POST   | /api/v1/statuses            | StatusDTO (POST)     | StatusDTO            | Create a new status               |
| PUT    | /api/v1/status/{id}         | StatusDTO (PUT)      | StatusDTO            | Update an existing status         |
| DELETE | /api/v1/status/{id}?version={version} |           |                      | Delete a status by ID             |

#### StatusDTO for GET and PUT
```
{
  "id": 1,
  "abbreviation": "NEW",
  "description": "New",
  "version": 1
}
```

#### StatusDTO for POST
```
{
  "abbreviation": "NEW",
  "description": "New",
  "version": 1
}
```

#### Example: Get All Statuses
Request:
```
GET /api/v1/statuses
```
Response:
```
[
  {
    "id": 1,
    "abbreviation": "NEW",
    "description": "New",
    "version": 1
  },
  ...
]
```

#### Example: Create Status
Request:
```
POST /api/v1/statuses
Content-Type: application/json

{
  "abbreviation": "HOLD",
  "description": "Hold",
  "version": 1
}
```
Response:
```
{
  "id": 6,
  "abbreviation": "HOLD",
  "description": "Hold",
  "version": 1
}
```

#### Example: Update Status
Request:
```
PUT /api/v1/status/6
Content-Type: application/json

{
  "id": 6,
  "abbreviation": "HOLD",
  "description": "Hold (updated)",
  "version": 2
}
```
Response:
```
{
  "id": 6,
  "abbreviation": "HOLD",
  "description": "Hold (updated)",
  "version": 2
}
```

#### Example: Delete Status
Request:
```
DELETE /api/v1/status/6?version=2
```
Response: HTTP 204 No Content

### Notes
- All endpoints return JSON and use standard HTTP status codes.
- The `version` field is used for optimistic locking. Deletion will fail with a 409 Conflict if the version does not match.
- Status records cannot be deleted if they are referenced by any orders.
- Standard error responses (e.g., 404 Not Found, 400 Bad Request, 409 Conflict) are used as appropriate.

---

## OrderType Data Model and API Documentation

### Introduction
The **OrderType** resource defines the types of orders available in the GlobeCo Order Service. This API allows clients to list, create, update, and delete order type records, which are referenced by orders to indicate their type (e.g., Buy, Sell, Short).

### OrderType Data Model
| Field         | Type    | Nullable | Description                                 |
|---------------|---------|----------|---------------------------------------------|
| id            | Integer | No       | Unique identifier for the order type        |
| abbreviation  | String  | No       | Short code for the order type               |
| description   | String  | No       | Detailed description of the order type      |
| version       | Integer | No       | Optimistic locking version number           |

#### Example OrderType Values
| abbreviation | description      | version |
|--------------|------------------|---------|
| BUY          | Buy              | 1       |
| SELL         | Sell             | 1       |
| SHORT        | Sell to Open     | 1       |
| COVER        | Buy to Close     | 1       |
| EXRC         | Exercise         | 1       |

### OrderType API Endpoints

| Method | Path                        | Request Body           | Response Body        | Description                       |
|--------|-----------------------------|-----------------------|----------------------|-----------------------------------|
| GET    | /api/v1/orderTypes          |                       | [OrderTypeDTO]       | List all order types              |
| GET    | /api/v1/orderTypes/{id}     |                       | OrderTypeDTO         | Get an order type by ID           |
| POST   | /api/v1/orderTypes          | OrderTypeDTO (POST)    | OrderTypeDTO         | Create a new order type           |
| PUT    | /api/v1/orderType/{id}      | OrderTypeDTO (PUT)     | OrderTypeDTO         | Update an existing order type     |
| DELETE | /api/v1/orderType/{id}?version={version} |           |                      | Delete an order type by ID        |

#### OrderTypeDTO for GET and PUT
```
{
  "id": 1,
  "abbreviation": "BUY",
  "description": "Buy",
  "version": 1
}
```

#### OrderTypeDTO for POST
```
{
  "abbreviation": "BUY",
  "description": "Buy",
  "version": 1
}
```

#### Example: Get All Order Types
Request:
```
GET /api/v1/orderTypes
```
Response:
```
[
  {
    "id": 1,
    "abbreviation": "BUY",
    "description": "Buy",
    "version": 1
  },
  ...
]
```

#### Example: Create Order Type
Request:
```
POST /api/v1/orderTypes
Content-Type: application/json

{
  "abbreviation": "COVER",
  "description": "Buy to Close",
  "version": 1
}
```
Response:
```
{
  "id": 4,
  "abbreviation": "COVER",
  "description": "Buy to Close",
  "version": 1
}
```

#### Example: Update Order Type
Request:
```
PUT /api/v1/orderType/4
Content-Type: application/json

{
  "id": 4,
  "abbreviation": "COVER",
  "description": "Buy to Close (updated)",
  "version": 2
}
```
Response:
```
{
  "id": 4,
  "abbreviation": "COVER",
  "description": "Buy to Close (updated)",
  "version": 2
}
```

#### Example: Delete Order Type
Request:
```
DELETE /api/v1/orderType/4?version=2
```
Response: HTTP 204 No Content

### Notes
- All endpoints return JSON and use standard HTTP status codes.
- The `version` field is used for optimistic locking. Deletion will fail with a 409 Conflict if the version does not match.
- Order types cannot be deleted if they are referenced by any orders.
- Standard error responses (e.g., 404 Not Found, 400 Bad Request, 409 Conflict) are used as appropriate.

---

## Blotter Data Model and API Documentation

### Introduction
The **Blotter** resource in the GlobeCo Order Service represents a record grouping financial transactions, typically used to organize and group orders. This API allows clients to list, create, update, and delete blotter records, which are referenced by orders to indicate their grouping.

### Blotter Data Model
| Field   | Type    | Nullable | Description                                 |
|---------|---------|----------|---------------------------------------------|
| id      | Integer | No       | Unique identifier for the blotter           |
| name    | String  | No       | Name of the blotter                         |
| version | Integer | No       | Optimistic locking version number           |

#### Example Blotter Values
| name         | version |
|--------------|---------|
| Default      | 1       |
| Equity       | 1       |
| Fixed Income | 1       |
| Hold         | 1       |
| Crypto       | 1       |

### Blotter API Endpoints

| Method | Path                                 | Request Body         | Response Body        | Description                       |
|--------|--------------------------------------|---------------------|----------------------|-----------------------------------|
| GET    | /api/v1/blotters                     |                     | [BlotterDTO]         | List all blotters                 |
| GET    | /api/v1/blotter/{id}                 |                     | BlotterDTO           | Get a blotter by ID               |
| POST   | /api/v1/blotters                     | BlotterDTO (POST)    | BlotterDTO           | Create a new blotter              |
| PUT    | /api/v1/blotter/{id}                 | BlotterDTO (PUT)     | BlotterDTO           | Update an existing blotter        |
| DELETE | /api/v1/blotter/{id}?version={version}|                     |                      | Delete a blotter by ID            |

#### BlotterDTO for GET and PUT
```
{
  "id": 1,
  "name": "Default",
  "version": 1
}
```

#### BlotterDTO for POST
```
{
  "name": "Default",
  "version": 1
}
```

#### Example: Get All Blotters
Request:
```
GET /api/v1/blotters
```
Response:
```
[
  {
    "id": 1,
    "name": "Default",
    "version": 1
  },
  ...
]
```

#### Example: Create Blotter
Request:
```
POST /api/v1/blotters
Content-Type: application/json

{
  "name": "Equity",
  "version": 1
}
```
Response:
```
{
  "id": 2,
  "name": "Equity",
  "version": 1
}
```

#### Example: Update Blotter
Request:
```
PUT /api/v1/blotter/2
Content-Type: application/json

{
  "id": 2,
  "name": "Equity (updated)",
  "version": 2
}
```
Response:
```
{
  "id": 2,
  "name": "Equity (updated)",
  "version": 2
}
```

#### Example: Delete Blotter
Request:
```
DELETE /api/v1/blotter/2?version=2
```
Response: HTTP 204 No Content

### Notes
- All endpoints return JSON and use standard HTTP status codes.
- The `version` field is used for optimistic locking. Deletion will fail with a 409 Conflict if the version does not match.
- Blotters cannot be deleted if they are referenced by any orders.
- Standard error responses (e.g., 404 Not Found, 400 Bad Request, 409 Conflict) are used as appropriate.

---

## Order Data Model and API Documentation

### Introduction
The **Order** resource in the GlobeCo Order Service represents a trading order, including all required fields and relationships to blotter, status, and order type. This API allows clients to list, create, update, and delete orders, with rich responses that include nested details for related entities.

### Order Data Model
| Field           | Type                | Nullable | Description                                         |
|----------------|---------------------|----------|-----------------------------------------------------|
| id             | Integer             | No       | Unique identifier for the order                     |
| blotter        | BlotterDTO          | Yes      | The containing blotter (see BlotterDTO)             |
| status         | StatusDTO           | No       | The order status (see StatusDTO)                    |
| portfolioId    | String (24 char)    | No       | ID of the portfolio making the order                |
| orderType      | OrderTypeDTO        | No       | The order type (see OrderTypeDTO)                   |
| securityId     | String (24 char)    | No       | ID of the security being traded                     |
| quantity       | Decimal(18,8)       | No       | Amount of security to trade                         |
| limitPrice     | Decimal(18,8)       | Yes      | Price limit for the order (if applicable)           |
| orderTimestamp | OffsetDateTime      | No       | When the order was placed                           |
| version        | Integer             | No       | Optimistic locking version number                   |
| tradeOrderId   | Integer             | Yes      | ID returned from trade service after submission     |

#### OrderDTO for POST/PUT
| Field           | Type             | Nullable | Description                                         |
|-----------------|------------------|----------|-----------------------------------------------------|
| id              | Integer          | No (PUT) | Unique identifier for the order (PUT only)          |
| blotterId       | Integer          | Yes      | Reference to the containing blotter                 |
| statusId        | Integer          | No       | Reference to the order status                       |
| portfolioId     | String (24 char) | No       | ID of the portfolio making the order                |
| orderTypeId     | Integer          | No       | Reference to the order type                         |
| securityId      | String (24 char) | No       | ID of the security being traded                     |
| quantity        | Decimal(18,8)    | No       | Amount of security to trade                         |
| limitPrice      | Decimal(18,8)    | Yes      | Price limit for the order (if applicable)           |
| orderTimestamp  | OffsetDateTime   | No       | When the order was placed                           |
| version         | Integer          | No       | Optimistic locking version number                   |
| tradeOrderId    | Integer          | Yes      | ID returned from trade service after submission     |

### Order API Endpoints
| Method | Path                                 | Request Body         | Response Body            | Description                                 |
|--------|--------------------------------------|---------------------|--------------------------|---------------------------------------------|
| GET    | /api/v1/orders                       |                     | [OrderWithDetailsDTO]    | List all orders (with details)              |
| GET    | /api/v1/order/{id}                   |                     | OrderWithDetailsDTO      | Get an order by ID (with details)           |
| POST   | /api/v1/orders                       | OrderDTO (POST)     | OrderWithDetailsDTO      | Create a new order                          |
| PUT    | /api/v1/order/{id}                   | OrderDTO (PUT)      | OrderWithDetailsDTO      | Update an existing order                    |
| DELETE | /api/v1/order/{id}?version={version} |                     |                          | Delete an order by ID                       |

#### OrderWithDetailsDTO Example (GET)
```
{
  "id": 42,
  "blotter": {
    "id": 1,
    "name": "Equities",
    "version": 1
  },
  "status": {
    "id": 2,
    "abbreviation": "NEW",
    "description": "New Order",
    "version": 1
  },
  "portfolioId": "5f47ac10b8e4e53b8cfa9b1a",
  "orderType": {
    "id": 3,
    "abbreviation": "LMT",
    "description": "Limit Order",
    "version": 1
  },
  "securityId": "5f47ac10b8e4e53b8cfa9b1b",
  "quantity": 100.00000000,
  "limitPrice": 50.25000000,
  "orderTimestamp": "2024-06-01T12:00:00Z",
  "version": 1,
  "tradeOrderId": 99999
}
```

#### OrderDTO Example (POST)
```
{
  "blotterId": 1,
  "statusId": 2,
  "portfolioId": "5f47ac10b8e4e53b8cfa9b1a",
  "orderTypeId": 3,
  "securityId": "5f47ac10b8e4e53b8cfa9b1b",
  "quantity": 100.00000000,
  "limitPrice": 50.25000000,
  "orderTimestamp": "2024-06-01T12:00:00Z",
  "version": 1,
  "tradeOrderId": 99999
}
```

#### Example: Get All Orders
Request:
```
GET /api/v1/orders
```
Response:
```
[
  {
    "id": 42,
    "blotter": { "id": 1, "name": "Equities", "version": 1 },
    "status": { "id": 2, "abbreviation": "NEW", "description": "New Order", "version": 1 },
    "portfolioId": "5f47ac10b8e4e53b8cfa9b1a",
    "orderType": { "id": 3, "abbreviation": "LMT", "description": "Limit Order", "version": 1 },
    "securityId": "5f47ac10b8e4e53b8cfa9b1b",
    "quantity": 100.00000000,
    "limitPrice": 50.25000000,
    "orderTimestamp": "2024-06-01T12:00:00Z",
    "version": 1,
    "tradeOrderId": 99999
  },
  ...
]
```

#### Example: Create Order
Request:
```
POST /api/v1/orders
Content-Type: application/json

{
  "blotterId": 1,
  "statusId": 2,
  "portfolioId": "5f47ac10b8e4e53b8cfa9b1a",
  "orderTypeId": 3,
  "securityId": "5f47ac10b8e4e53b8cfa9b1b",
  "quantity": 100.00000000,
  "limitPrice": 50.25000000,
  "orderTimestamp": "2024-06-01T12:00:00Z",
  "version": 1,
  "tradeOrderId": 99999
}
```
Response:
```
{
  "id": 42,
  "blotter": { "id": 1, "name": "Equities", "version": 1 },
  "status": { "id": 2, "abbreviation": "NEW", "description": "New Order", "version": 1 },
  "portfolioId": "5f47ac10b8e4e53b8cfa9b1a",
  "orderType": { "id": 3, "abbreviation": "LMT", "description": "Limit Order", "version": 1 },
  "securityId": "5f47ac10b8e4e53b8cfa9b1b",
  "quantity": 100.00000000,
  "limitPrice": 50.25000000,
  "orderTimestamp": "2024-06-01T12:00:00Z",
  "version": 1,
  "tradeOrderId": 99999
}
```

#### Example: Update Order
Request:
```
PUT /api/v1/order/42
Content-Type: application/json

{
  "id": 42,
  "blotterId": 1,
  "statusId": 2,
  "portfolioId": "5f47ac10b8e4e53b8cfa9b1a",
  "orderTypeId": 3,
  "securityId": "5f47ac10b8e4e53b8cfa9b1b",
  "quantity": 100.00000000,
  "limitPrice": 50.25000000,
  "orderTimestamp": "2024-06-01T12:00:00Z",
  "version": 2,
  "tradeOrderId": 99999
}
```
Response:
```
{
  "id": 42,
  "blotter": { "id": 1, "name": "Equities", "version": 1 },
  "status": { "id": 2, "abbreviation": "NEW", "description": "New Order", "version": 1 },
  "portfolioId": "5f47ac10b8e4e53b8cfa9b1a",
  "orderType": { "id": 3, "abbreviation": "LMT", "description": "Limit Order", "version": 1 },
  "securityId": "5f47ac10b8e4e53b8cfa9b1b",
  "quantity": 100.00000000,
  "limitPrice": 50.25000000,
  "orderTimestamp": "2024-06-01T12:00:00Z",
  "version": 2,
  "tradeOrderId": 99999
}
```

#### Example: Delete Order
Request:
```
DELETE /api/v1/order/42?version=2
```
Response: HTTP 204 No Content

### Notes
- All endpoints return JSON and use standard HTTP status codes.
- The `version` field is used for optimistic locking. Deletion will fail with a 409 Conflict if the version does not match.
- Orders cannot be deleted if they are referenced by other entities (e.g., foreign key constraints).
- The `OrderWithDetailsDTO` response includes nested Blotter, Status, and OrderType details for convenience.
- Standard error responses (e.g., 404 Not Found, 400 Bad Request, 409 Conflict) are used as appropriate.

## Health Check Endpoints (Liveness, Readiness, Startup)

This service exposes standard health check endpoints for Kubernetes probes using Spring Boot Actuator:

| Probe Type | Endpoint URL                  | Description                       |
|------------|-------------------------------|-----------------------------------|
| Liveness   | `/actuator/health/liveness`   | Used for Kubernetes livenessProbe |
| Readiness  | `/actuator/health/readiness`  | Used for Kubernetes readinessProbe|
| Startup    | `/actuator/health/startup`    | Used for Kubernetes startupProbe  |

All endpoints return JSON and HTTP 200 if healthy. Example response:
```
GET /actuator/health/liveness
{
  "status": "UP"
}
```

These endpoints are enabled and exposed by default. You can use them directly in your Kubernetes deployment YAML for health checks.

## OpenAPI Schema Endpoint

The OpenAPI 3.0 schema for this service is available at:

    /openapi.yaml

Example: http://localhost:8080/openapi.yaml

This file is served as a static resource and can be used for integration with tools such as Swagger UI, Redoc, or for client code generation.

## Springdoc OpenAPI & Swagger UI Endpoints

With Springdoc OpenAPI, the following endpoints are automatically available:

| Endpoint              | Description                        |
|----------------------|------------------------------------|
| /v3/api-docs         | OpenAPI 3.0 schema (JSON)          |
| /v3/api-docs.yaml    | OpenAPI 3.0 schema (YAML)          |
| /swagger-ui.html     | Interactive Swagger UI documentation|

Example URLs:
- http://localhost:8080/v3/api-docs
- http://localhost:8080/v3/api-docs.yaml
- http://localhost:8080/swagger-ui.html

These endpoints are generated from your controllers and models. You can use them for API exploration, client code generation, and integration with other tools.

## CORS Policy

All API endpoints in the GlobeCo Order Service allow requests from any origin (CORS is enabled globally for all origins, methods, and headers). This is configured to facilitate integration with any client or service, as required by the supplemental requirements.

## CI/CD: Multi-Architecture Docker Build & Deployment

This project uses GitHub Actions to build and push multi-architecture (AMD64 and ARM64) Docker images to DockerHub. The workflow is defined in `.github/workflows/docker-multiarch.yml` and is triggered on pushes and pull requests to the `main` branch.

**Workflow summary:**
- Checks out the code and sets up JDK 21
- Caches Gradle dependencies for faster builds
- Builds the Spring Boot JAR
- Uses Docker Buildx to build images for both AMD64 and ARM64
- Logs in to DockerHub using repository secrets
- Pushes the image to DockerHub with both `latest` and commit SHA tags (on push to main)

**Required GitHub Secrets:**
- `DOCKERHUB_USERNAME`: Your DockerHub username
- `DOCKERHUB_TOKEN`: A DockerHub access token or password
- `DOCKERHUB_REPO`: The DockerHub repository name (e.g., `globeco-order-service`)

This ensures that every commit to main is built and published as a multi-architecture image, ready for deployment on any platform.

### Submit Order Endpoint

#### POST /api/v1/orders/{id}/submit

Submits an order to the GlobeCo Trade Service. This endpoint calls the trade service's POST /api/v1/tradeOrders API, mapping the order fields as required. If the submission is successful, the order status is updated from "NEW" to "SENT" and the tradeOrderId field is set in the order service.

- **Request:** No body required. The order must be in status "NEW".
- **Response:**
  - 200 OK: `{ "status": "submitted" }` (if successful)
  - 400 Bad Request: `{ "status": "not submitted" }` (if not in NEW status or trade service call fails)

**Example:**
```
POST /api/v1/orders/42/submit

Response (success):
{
  "status": "submitted"
}

Response (failure):
{
  "status": "not submitted"
}
```

**Notes:**
- This endpoint is used to submit an order for execution.
- The order is sent to the trade service at http://localhost:8082/api/v1/tradeOrders.
- Only orders with status "NEW" can be submitted.
- On success, the order status is updated to "SENT" and the tradeOrderId field is set in the order service.
