-- Initialize Ai Chat Service Database
-- This script runs when the PostgreSQL container starts for the first time

-- Create additional schemas
CREATE SCHEMA IF NOT EXISTS aichat;

-- Set default search path
ALTER DATABASE aichat SET search_path TO public, aichat;

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE aichat TO svc_aichat_dba;
GRANT ALL PRIVILEGES ON SCHEMA public TO svc_aichat_dba;
GRANT ALL PRIVILEGES ON SCHEMA aichat TO svc_aichat_dba;

-- Audit trigger function for tracking row updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

SELECT 'Ai Chat Service Database initialized successfully' AS status;
