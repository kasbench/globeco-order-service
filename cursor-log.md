Instructions:
- Log every request that you receive.
- Log every action that you take in an enumerated list.
- Follow the format provided below.  
- Add each new entry at the end.
- Make sure there are two blank lines between each section.


Prompt: Configure the project to connect to the PostgreSQL database on host `globeco-order-service-postgresql`  port 5432 and database `postgres`.  The user is  "postgres".  No password is required. Please add an entry with this prompt and your actions in the cursor-log.md  file following the instructions in the file.
Actions:
1. Added PostgreSQL datasource configuration to src/main/resources/application.properties for host globeco-order-service-postgresql, port 5432, database postgres, user postgres, and no password.
2. Logged this prompt and these actions in cursor-log.md following the required format.


Prompt: Configure Flyway with the same configuration as in step 1.  Please add an entry with this prompt and your actions in the cursor-log.md  file following the instructions in the file.
Actions:
1. Verified that the datasource configuration for PostgreSQL is present in src/main/resources/application.properties.
2. Ensured Flyway is configured to use the same PostgreSQL connection settings (host globeco-order-service-postgresql, port 5432, database postgres, user postgres, no password) in application.properties.
3. Logged this prompt and these actions in cursor-log.md following the required format.


Prompt: Create a Flyway migration to deploy the schema for this project. The schema is in @order-service.sql.
Actions:
1. Located the schema definition in documentation/order-service.sql.
2. Created the initial Flyway migration file src/main/resources/db/migration/V1__init_schema.sql with the schema DDL, omitting CREATE DATABASE and OWNER statements.
3. Ensured all tables and constraints from the schema are included in the migration file.
4. Logged this prompt and these actions in cursor-log.md following the required format.


Prompt: Create a Flyway migration for the blotter data in #### Initialization Data for `blotter`
Actions:
1. Reviewed the requirements for the initialization data for the blotter table.
2. Created the Flyway migration file src/main/resources/db/migration/V2__blotter_init_data.sql to insert the required rows: Default, Equity, Fixed Income, Hold, Crypto (all with version 1).
3. Logged this prompt and these actions in cursor-log.md following the required format.


Prompt: Create a Flyway migration for the blotter data in #### Initialization Data for `order_type`
Actions:
1. Reviewed the requirements for the initialization data for the order_type table.
2. Created the Flyway migration file src/main/resources/db/migration/V3__order_type_init_data.sql to insert the required rows: BUY/Buy, SELL/Sell, SHORT/Sell to Open, COVER/Buy to Close, EXRC/Exercise (all with version 1).
3. Logged this prompt and these actions in cursor-log.md following the required format.


Prompt: Create a Flyway migration for the blotter data in #### Initialization Data for `status`
Actions:
1. Reviewed the requirements for the initialization data for the status table.
2. Created the Flyway migration file src/main/resources/db/migration/V4__status_init_data.sql to insert the required rows: NEW/New, SENT/Sent, WORK/In progress, FULL/Filled, PART/Partial fill, HOLD/Hold, CNCL/Cancel, CNCLD/Cancelled, CPART/Cancelled with partial fill, DEL/Delete (all with version 1).
3. Logged this prompt and these actions in cursor-log.md following the required format.


Prompt: Please implement the APIs for status using the requirements provided in @requirements.md. Please add an entry with this prompt and your actions in the cursor-log.md file following the instructions in the file.
Actions:
1. Created the Status JPA entity in src/main/java/org/kasbench/globeco_order_service/entity/Status.java.
2. Created StatusDTO and StatusPostDTO in src/main/java/org/kasbench/globeco_order_service/dto/ for GET/PUT and POST requests.
3. Created StatusRepository in src/main/java/org/kasbench/globeco_order_service/repository/ for CRUD operations.
4. Implemented StatusService in src/main/java/org/kasbench/globeco_order_service/service/ with business logic and mapping between entity and DTOs.
5. Implemented StatusController in src/main/java/org/kasbench/globeco_order_service/controller/ with endpoints for GET, POST, PUT, DELETE as specified.
6. Used a service layer for maintainability and testability.
7. Logged this prompt and these actions in cursor-log.md following the required format.

