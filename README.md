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
