# Supplemental Requirement 6: Send orders to the transaction service in bulk

The `SubmitOrdersBatch` method in `OrderController` sends orders to the transaction service one-by-one.  It calls `SubmitOrdersBatch` in `OrderService`, which calls `SubmitIndividualOrderInTransaction`, which calls `SubmitIndividualOrder`.  `SubmitIndividualOrderInTransaction` accomplishes nothing as it is not a transaction.  The transactional annotation was removed previously to avoid long running transactions.

The transaction service has a batch endpoint that accepts a list of orders: POST /api/v1/tradeOrders/bulk.  The endpoint is fully documented [here](bulk-trade-orders-api-guide.md)

This enhancement is to simplify the current implementation of `SubmitOrdersBatch` by sending the batch of trade orders to the trade service using the POST /api/v1/tradeOrders/bulk API of the trade service as described above.  Speeding up processing is the primary objective.  The current process has too much overhead and is way too slow.

This change should not break any order service APIs, particularly POST /api/v1/orders/batch/submit.  The requests and responses should remain unchanged.

Important Instructions:
- I will perform the integration testing in Kubernetes.  The APIs and databases are not exposed outside of Kubernetes.  Don't waste time trying to perform integration tests.  Turn it over to me.  I can quickly run the tests.
- Log any errors received from the trade service in as much detail as you receive.  This will assist greatly with debugging.
