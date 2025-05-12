-- GlobeCo Order Service Status Initialization Data
-- Inserts initial rows into the status table

INSERT INTO status (abbreviation, description, version) VALUES
  ('NEW', 'New', 1),
  ('SENT', 'Sent', 1),
  ('WORK', 'In progress', 1),
  ('FULL', 'Filled', 1),
  ('PART', 'Partial fill', 1),
  ('HOLD', 'Hold', 1),
  ('CNCL', 'Cancel', 1),
  ('CNCLD', 'Cancelled', 1),
  ('CPART', 'Cancelled with partial fill', 1),
  ('DEL', 'Delete', 1); 