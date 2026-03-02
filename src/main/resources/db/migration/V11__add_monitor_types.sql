ALTER TABLE monitors ADD COLUMN IF NOT EXISTS http_method VARCHAR(10) DEFAULT 'GET';
ALTER TABLE monitors ADD COLUMN IF NOT EXISTS expected_response_keyword VARCHAR(255);
UPDATE monitors SET http_method = 'GET' WHERE type = 'HTTP' AND http_method IS NULL;
