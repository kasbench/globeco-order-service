# Supplemental Requirement 3

- Create a new DTO called OrderPostResponseDTO with the following definition:

    | Field | Data Type | Description |
    | --- | --- | --- |
    | status | String | "OK" or "FAIL"
    | message | String | Error message 
    | totalOrdersReceived | Integer | Total number of orders received 
    | successfulOrders | Integer | Number of orders successfully saved to the database
    | unsuccessfulOrders | Integer | Number of orders not successfully saved to the database
    | order | OrderWithDetailsDTO | response |
    ---

- Create a new DTO called OrderListResponseDTO with the following definition
    | Field | Data Type | Description |
    | --- | --- | --- |
    | status | String | "OK", "FAIL", or "PARTIAL". Respond with "OK" if all messages processed successfully, "FAIL" if no messages processed successfully, otherwise respond with "PARTIAL"
    | message | String | Error message
    | orders | [OrderPostResponseDTO] | responses |
    ---

- Modify POST api/v1/orders to take as input an array of OrderPostDTO and respond with an OrderListResponseDTO, as summarized below

    Current Requirement:

    | Verb | Path | Request DTO | Response DTO |
    | --- | --- | --- | --- |
    | POST | api/v1/orders |  OrderPostDTO | OrderWithDetailsDTO |


    New Requirement:

    | Verb | Path | Request DTO | Response DTO |
    | --- | --- | --- | --- |
    | POST | api/v1/orders |  [OrderPostDTO] | OrderListResponseDTO |

- Return HTTP code 200 if all successful, 207 if partially successful, and a 400-level code if unsuccessful
- Backward compatibility is not required
- Update tests to reflect this change
- Update README.md to reflect this change
- Accept up to 1000 orders at a time.  Return a 400-level code if greater than 1000

