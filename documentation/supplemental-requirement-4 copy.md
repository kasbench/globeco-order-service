# Supplemental Requirement 4

1. Modify OrderWithDetailsDTO
    - Instead of returning `securityId`, return 
    `"security": {"securityId": string, "ticker": string}`.  You can get the mapping of securityId to ticker from the Security Service API.  See ## Integrations below.  For performance, you can cache security records in memory with a 5 minute TTL
    - Instead of returning `portfolioId`, return
    `"portfolio": {"portfolioId": string, "name": string}.  You can get the mapping of portfolioId to name from the portfolio service.  See ## Integrations below.  For performance, you can cache security records in memory with a 5 minute TTL
2. Implement paging for GET api/v1/orders.  Add `limit` and `offset` query parameters.  Limit must be between 1 and 1000, inclusive.
3. Implement sorting for GET api/v1/orders.  Add `sort_by` as a query parameter.  This query parameter is comma separated list of fields.  Fields include: id, security, portfolio, blotter, status, orderType, quantity, and orderTimestamp. For blotter, sort on blotter.name.  For security, sort on ticker.  For portfolio, sort on name.  For status, sort on status.abbreviation. For orderType, sort on orderType.abbreviation.  If the user supplies multiple comma separated field names, sort in the order they appear.  The default sorting is ascending.  A minus sign preceding the field name indicates descending sorting for that field.  If the user supplies an invalid sort_by value, through a 400-level error with an appropriate message.
4.  Implement a filter_by query parameter.  The allowable filters are the same as the sort_by fields.  The user can supply multiple filter_by key-value pairs, separated by comma.  For example:
`GET api/v1/orders?filter_by="ticker=IBM,portfolio=PORT001"



## Integrations

| Service | Host | Port | OpenAPI Spec |
| --- | --- | --- | --- |
| Security Service | globeco-security-service | 8000 | [globeco-security-service-openapi.yaml](globeco-security-service-openapi.yaml)
| Portfolio Service | globeco-portfolio-service | 8001 | [lobeco-portfolio-service-openapi.yaml](globeco-portfolio-service-openapi.yaml)
---