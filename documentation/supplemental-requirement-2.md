---
description: 
globs: 
alwaysApply: false
---
# Supplemental Requirements

This is the second supplemental requirement for the order service. The data model has been modified:  
- The `order` table now has a new column called `trade_order_id` (type: integer, nullable).  
- This column will store the `id` field returned when posting to the trade service (`POST /api/v1/tradeOrders`).  
- The updated schema is shown in [order-service.sql](mdc:order-service.sql).

**Steps:**

1. **Database Migration:**  
   - Update the schema in [V1__init_schema.sql](mdc:src/main/resources/db/migration/V1__init_schema.sql) (or create a new migration if preserving migration history) to add the `trade_order_id` column to the `order` table.

2. **DTO and Service Update:**  
   - Update all relevant order DTOs (`OrderDTO`, `OrderPostDTO`, `OrderWithDetailsDTO`) and the service layer to include a `tradeOrderId` field mapped to the new column.
   - Update all relevant tests to reflect this change.

3. **Order Submission Endpoint:**  
   - In the implementation of `api/v1/orders/{id}/submit`, after calling the trade service, save the `id` returned from the trade service as the `trade_order_id` (`tradeOrderId`) for the order.
   - If the trade service call fails, ensure the `tradeOrderId` remains null and handle the error appropriately (e.g., return a 400 or 500 error).
   - Update all relevant tests.

4. **Documentation:**  
   - Update [README.md](mdc:README.md) and [openapi.yaml](mdc:openapi.yaml) (and [openapi.yaml](mdc:src/main/resources/static/openapi.yaml), if applicable) to reflect these changes, including schema definitions and example payloads.