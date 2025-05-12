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
