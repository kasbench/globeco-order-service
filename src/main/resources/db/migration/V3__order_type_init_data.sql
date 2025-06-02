-- GlobeCo Order Service Order Type Initialization Data
-- Inserts initial rows into the order_type table

INSERT INTO order_type (abbreviation, description, version) VALUES
  ('BUY', 'Buy', 1),
  ('SELL', 'Sell', 1),
  ('SHORT', 'Sell to Open', 1),
  ('COVER', 'Buy to Close', 1),
  ('EXRC', 'Exercise', 1);