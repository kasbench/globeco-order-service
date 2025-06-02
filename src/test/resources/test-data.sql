-- Test data for integration test
INSERT INTO blotter (id, name, version) VALUES 
(1, 'Test Blotter', 1),
(2, 'Equity Blotter', 1);

INSERT INTO status (id, abbreviation, description, version) VALUES 
(1, 'NEW', 'New Order', 1),
(2, 'SENT', 'Sent to Trade Service', 1),
(3, 'FILLED', 'Order Filled', 1);

INSERT INTO order_type (id, abbreviation, description, version) VALUES 
(1, 'BUY', 'Buy Order', 1),
(2, 'SELL', 'Sell Order', 1),
(3, 'LMT', 'Limit Order', 1);

INSERT INTO "order" (id, blotter_id, status_id, portfolio_id, order_type_id, security_id, quantity, limit_price, trade_order_id, order_timestamp, version) VALUES 
(1, 1, 1, 'PORT12345678901234567890', 1, 'SEC12345678901234567890', 100.12345678, 50.25, null, '2024-06-01T12:00:00Z', 1),
(2, 2, 2, 'PORT98765432109876543210', 2, 'SEC98765432109876543210', 200.87654321, 75.50, 12345, '2024-06-02T14:30:00Z', 1),
(3, null, 1, 'PORT11111111111111111111', 3, 'SEC11111111111111111111', 50.00000000, null, null, '2024-06-03T09:15:00Z', 1); 