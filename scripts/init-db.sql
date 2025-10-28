-- Database initialization script for Stacks Chain Monitor
-- This script runs when the PostgreSQL container starts for the first time

-- Create database (if using docker-compose, this is handled by POSTGRES_DB env var)
-- CREATE DATABASE stacks_monitor;

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE stacks_monitor TO postgres;

-- Log initialization
DO $$
BEGIN
  RAISE NOTICE 'Stacks Chain Monitor database initialized successfully';
END $$;
