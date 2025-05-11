# GlobeCo Order Service Requirements

## Background

This document provides a comprehensive data dictionary and model documentation for the Order Service database. The database is designed to manage trading orders within a financial system, including order management, order types, status tracking, and organization via blotters.


## Technology



## Data Dictionary


[Data Dictionary](order-service.html)

## Entity Relationship Diagram

<img src="./images/order-service.png">



```
+-------------+       +----------------+       +-------------+
|   blotter   |       |     order      |       | order_type  |
+-------------+       +----------------+       +-------------+
| PK id       |<----->| PK id          |<----->| PK id       |
|    name     |       |    blotter_id  |       |    abbrev.  |
|    version  |       |    status_id   |       |    desc.    |
+-------------+       |    portfolio_id|       |    version  |
                      |    order_type_id|      +-------------+
                      |    security_id |
                      |    quantity    |       +-------------+
                      |    limit_price |       |   status    |
                      |    order_time  |<----->| PK id       |
                      |    version     |       |    abbrev.  |
                      +----------------+       |    desc.    |
                                               |    version  |
                                               +-------------+
```

## Tables

### blotter

A blotter is a record of financial transactions, typically used to organize and group orders.

| Column  | Data Type   | Constraints    | Description                       |
|---------|-------------|----------------|-----------------------------------|
| id      | serial      | PK, NOT NULL   | Unique identifier                 |
| name    | varchar(60) | NOT NULL       | Name of the blotter               |
| version | integer     | NOT NULL, DEF 1 | Optimistic locking version number |

### order

The main entity representing a trading order in the system.

| Column          | Data Type     | Constraints        | Description                            |
|-----------------|---------------|-------------------|----------------------------------------|
| id              | serial        | PK, NOT NULL      | Unique identifier                      |
| blotter_id      | integer       | FK to blotter.id  | Reference to the containing blotter    |
| status_id       | integer       | FK, NOT NULL      | Reference to order status              |
| portfolio_id    | char(24)      | NOT NULL          | ID of the portfolio making the order   |
| order_type_id   | integer       | FK, NOT NULL      | Reference to order type                |
| security_id     | char(24)      | NOT NULL          | ID of the security being traded        |
| quantity        | decimal(18,8) | NOT NULL          | Amount of security to trade            |
| limit_price     | decimal(18,8) |                   | Price limit for the order (if applicable) |
| order_timestamp | timestamptz   | NOT NULL, DEF NOW | When the order was placed              |
| version         | integer       | NOT NULL, DEF 1   | Optimistic locking version number      |

### order_type

Defines the various types of orders available in the system.

| Column       | Data Type   | Constraints    | Description                       |
|-------------|-------------|----------------|-----------------------------------|
| id          | serial      | PK, NOT NULL   | Unique identifier                 |
| abbreviation | varchar(10) | NOT NULL       | Short code for the order type     |
| description | varchar(60) | NOT NULL       | Detailed description of order type |
| version     | integer     | NOT NULL, DEF 1 | Optimistic locking version number |

### status

Defines the possible statuses an order can have.

| Column       | Data Type   | Constraints    | Description                       |
|-------------|-------------|----------------|-----------------------------------|
| id          | serial      | PK, NOT NULL   | Unique identifier                 |
| abbreviation | varchar(20) | NOT NULL       | Short code for the status         |
| description | varchar(60) | NOT NULL       | Detailed description of status    |
| version     | integer     | NOT NULL, DEF 1 | Optimistic locking version number |

## Relationships

1. **blotter to order (1:N)**
   - A blotter can contain multiple orders
   - An order can optionally belong to one blotter
   - If a blotter is deleted, the blotter_id in associated orders is set to NULL

2. **order_type to order (1:N)**
   - An order type can be used by multiple orders
   - Each order must have exactly one order type
   - Order types cannot be deleted if they're referenced by orders

3. **status to order (1:N)**
   - A status can apply to multiple orders
   - Each order must have exactly one status
   - Status records cannot be deleted if they're referenced by orders

## Design Notes

1. The database uses PostgreSQL version 17.0
2. All tables include a version column for optimistic locking
3. The model uses 24-character strings for external IDs (portfolio_id, security_id), likely to accommodate MongoDB ObjectIDs
4. Decimal columns use high precision (18,8) to accommodate financial calculations
5. Orders have ON DELETE RESTRICT for status and order_type relationships, preventing deletion of referenced records
6. All timestamp fields use timestamptz (timestamp with time zone) to ensure proper timezone handling


## Data Transfer Objects (DTOs)

The following DTOs represent the data structures used to transfer information between the API and clients for the main entities in the GlobeCo Order Service.

---

### BlotterDTO for GET and PUT

Represents a blotter, which is a record grouping financial transactions.

| Field   | Type    | Nullable | Description                                 |
|---------|---------|----------|---------------------------------------------|
| id      | Integer | No       | Unique identifier for the blotter           |
| name    | String  | No       | Name of the blotter                         |
| version | Integer | No       | Optimistic locking version number           |

---
### BlotterDTO for POST

Represents a blotter, which is a record grouping financial transactions.

| Field   | Type    | Nullable | Description                                 |
|---------|---------|----------|---------------------------------------------|
| name    | String  | No       | Name of the blotter                         |
| version | Integer | No       | Optimistic locking version number           |

---

### OrderTypeDTO for GET and PUT

Represents the type of an order.

| Field         | Type    | Nullable | Description                                 |
|---------------|---------|----------|---------------------------------------------|
| id            | Integer | No       | Unique identifier for the order type        |
| abbreviation  | String  | No       | Short code for the order type               |
| description   | String  | No       | Detailed description of the order type      |
| version       | Integer | No       | Optimistic locking version number           |

---

### OrderTypeDTO for POST

Represents the type of an order.

| Field         | Type    | Nullable | Description                                 |
|---------------|---------|----------|---------------------------------------------|
| abbreviation  | String  | No       | Short code for the order type               |
| description   | String  | No       | Detailed description of the order type      |
| version       | Integer | No       | Optimistic locking version number           |

---


### StatusDTO for GET and PUT

Represents the status of an order.

| Field         | Type    | Nullable | Description                                 |
|---------------|---------|----------|---------------------------------------------|
| id            | Integer | No       | Unique identifier for the status            |
| abbreviation  | String  | No       | Short code for the status                   |
| description   | String  | No       | Detailed description of the status          |
| version       | Integer | No       | Optimistic locking version number           |

---

### StatusDTO for POST

Represents the status of an order.

| Field         | Type    | Nullable | Description                                 |
|---------------|---------|----------|---------------------------------------------|
| abbreviation  | String  | No       | Short code for the status                   |
| description   | String  | No       | Detailed description of the status          |
| version       | Integer | No       | Optimistic locking version number           |

---






### OrderDTO for PUT

Represents a trading order in the system.

| Field           | Type             | Nullable | Description                                         |
|-----------------|------------------|----------|-----------------------------------------------------|
| id              | Integer          | No       | Unique identifier for the order                     |
| blotterId       | Integer          | Yes      | Reference to the containing blotter                 |
| statusId        | Integer          | No       | Reference to the order status                       |
| portfolioId     | String (24 char) | No       | ID of the portfolio making the order                |
| orderTypeId     | Integer          | No       | Reference to the order type                         |
| securityId      | String (24 char) | No       | ID of the security being traded                     |
| quantity        | Decimal(18,8)    | No       | Amount of security to trade                         |
| limitPrice      | Decimal(18,8)    | Yes      | Price limit for the order (if applicable)           |
| orderTimestamp  | OffsetDateTime   | No       | When the order was placed                           |
| version         | Integer          | No       | Optimistic locking version number                   |

---



### OrderDTO for POST

Represents a trading order in the system.

| Field           | Type             | Nullable | Description                                         |
|-----------------|------------------|----------|-----------------------------------------------------|
| id              | Integer          | No       | Unique identifier for the order                     |
| blotterId       | Integer          | Yes      | Reference to the containing blotter                 |
| statusId        | Integer          | No       | Reference to the order status                       |
| portfolioId     | String (24 char) | No       | ID of the portfolio making the order                |
| orderTypeId     | Integer          | No       | Reference to the order type                         |
| securityId      | String (24 char) | No       | ID of the security being traded                     |
| quantity        | Decimal(18,8)    | No       | Amount of security to trade                         |
| limitPrice      | Decimal(18,8)    | Yes      | Price limit for the order (if applicable)           |
| orderTimestamp  | OffsetDateTime   | No       | When the order was placed                           |
| version         | Integer          | No       | Optimistic locking version number                   |

---





### OrderWithDetailsDTO for GET

Represents a trading order in the system, including detailed information about its associated blotter, status, and order type.

| Field           | Type                | Nullable | Description                                         |
|-----------------|---------------------|----------|-----------------------------------------------------|
| id              | Integer             | No       | Unique identifier for the order                     |
| blotter         | BlotterDTO          | Yes      | The containing blotter (see [BlotterDTO](#blotterdto)) |
| status          | StatusDTO           | No       | The order status (see [StatusDTO](#statusdto))      |
| portfolioId     | String (24 char)    | No       | ID of the portfolio making the order                |
| orderType       | OrderTypeDTO        | No       | The order type (see [OrderTypeDTO](#ordertypedto))  |
| securityId      | String (24 char)    | No       | ID of the security being traded                     |
| quantity        | Decimal(18,8)       | No       | Amount of security to trade                         |
| limitPrice      | Decimal(18,8)       | Yes      | Price limit for the order (if applicable)           |
| orderTimestamp  | OffsetDateTime      | No       | When the order was placed                           |
| version         | Integer             | No       | Optimistic locking version number                   |


#### Nested DTOs

##### BlotterDTO

| Field   | Type    | Nullable | Description                                 |
|---------|---------|----------|---------------------------------------------|
| id      | Integer | No       | Unique identifier for the blotter           |
| name    | String  | No       | Name of the blotter                         |
| version | Integer | No       | Optimistic locking version number           |

##### StatusDTO

| Field         | Type    | Nullable | Description                                 |
|---------------|---------|----------|---------------------------------------------|
| id            | Integer | No       | Unique identifier for the status            |
| abbreviation  | String  | No       | Short code for the status                   |
| description   | String  | No       | Detailed description of the status          |
| version       | Integer | No       | Optimistic locking version number           |

##### OrderTypeDTO

| Field         | Type    | Nullable | Description                                 |
|---------------|---------|----------|---------------------------------------------|
| id            | Integer | No       | Unique identifier for the order type        |
| abbreviation  | String  | No       | Short code for the order type               |
| description   | String  | No       | Detailed description of the order type      |
| version       | Integer | No       | Optimistic locking version number           |

---


**Notes:**
- All DTOs use types that match the database schema.
- Foreign keys are represented as IDs.
- Nullable fields are indicated in the table.
- The `OrderWithDetailsDTO` provides a richer representation for API responses, embedding related entities directly.
- For lists of orders, you may return a list of `OrderWithDetailsDTO` objects.


## REST API Documentation

The following REST APIs are recommended for managing blotters, order types, statuses, and orders in the GlobeCo Order Service. All endpoints return JSON and use standard HTTP status codes.

---

### Blotter Endpoints

| Method | Path              | Request Body         | Response Body        | Description                       |
|--------|-------------------|---------------------|----------------------|-----------------------------------|
| GET    | /api/v1/blotters     |                     | [BlotterDTO]         | List all blotters                 |
| GET    | /api/v1/blotter/{id}|                     | BlotterDTO           | Get a blotter by ID               |
| POST   | /api/v1/blotters     | BlotterDTO (POST)   | BlotterDTO           | Create a new blotter              |
| PUT    | /api/v1/blotter/{id}| BlotterDTO (PUT)    | BlotterDTO           | Update an existing blotter        |
| DELETE | /api/v1/blotter/{id}?version={version}|                     |                      | Delete a blotter by ID            |

---

### Order Type Endpoints

| Method | Path                   | Request Body           | Response Body        | Description                       |
|--------|------------------------|-----------------------|----------------------|-----------------------------------|
| GET    | /api/v1/orderTypes       |                       | [OrderTypeDTO]       | List all order types              |
| GET    | /api/v1/orderTypes/{id}  |                       | OrderTypeDTO         | Get an order type by ID           |
| POST   | /api/v1/orderTypes       | OrderTypeDTO (POST)   | OrderTypeDTO         | Create a new order type           |
| PUT    | /api/v1/orderType/{id}  | OrderTypeDTO (PUT)    | OrderTypeDTO         | Update an existing order type     |
| DELETE | /api/v1/orderType/{id}?version={version}  |                       |                      | Delete an order type by ID        |

---

### Status Endpoints

| Method | Path              | Request Body         | Response Body        | Description                       |
|--------|-------------------|---------------------|----------------------|-----------------------------------|
| GET    | /api/v1/statuses     |                     | [StatusDTO]          | List all statuses                 |
| GET    | /api/v1/status/{id}|                     | StatusDTO            | Get a status by ID                |
| POST   | /api/v1/statuses     | StatusDTO (POST)    | StatusDTO            | Create a new status               |
| PUT    | /api/v1/status/{id}| StatusDTO (PUT)     | StatusDTO            | Update an existing status         |
| DELETE | /api/v1/status/{id}?version={version}|                     |                      | Delete a status by ID             |

---

### Order Endpoints

| Method | Path              | Request Body         | Response Body            | Description                                 |
|--------|-------------------|---------------------|--------------------------|---------------------------------------------|
| GET    | /api/v1/orders       |                     | [OrderWithDetailsDTO]    | List all orders (with details)              |
| GET    | /api/v1/order/{id}  |                     | OrderWithDetailsDTO      | Get an order by ID (with details)           |
| POST   | /api/v1/orders       | OrderDTO (POST)     | OrderWithDetailsDTO      | Create a new order                          |
| PUT    | /api/v1/order/{id}  | OrderDTO (PUT)      | OrderWithDetailsDTO      | Update an existing order                    |
| DELETE | /api/v1/order/{id}?version={version}  |                     |                        | Delete an order by ID                       |

---

#### Notes
- All POST and PUT endpoints expect the corresponding DTO in the request body.
- All GET endpoints return the DTO or a list of DTOs as described above.
- The `OrderWithDetailsDTO` includes nested `BlotterDTO`, `StatusDTO`, and `OrderTypeDTO` objects for richer responses.
- Standard error responses (e.g., 404 Not Found, 400 Bad Request, 409 Conflict) should be used as appropriate.

---

**Example: Create Order Request (POST /api/v1/orders)**
```json
{
  "blotterId": 1,
  "statusId": 2,
  "portfolioId": "5f47ac10b8e4e53b8cfa9b1a",
  "orderTypeId": 3,
  "securityId": "5f47ac10b8e4e53b8cfa9b1b",
  "quantity": 100.00000000,
  "limitPrice": 50.25000000,
  "orderTimestamp": "2024-06-01T12:00:00Z",
  "version": 1
}
```

**Example: Get Order Response (GET /api/v1/orders/42)**
```json
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
  "version": 1
}
```

