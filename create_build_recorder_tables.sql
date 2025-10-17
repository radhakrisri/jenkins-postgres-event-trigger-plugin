-- Create the jobs metadata table
CREATE TABLE IF NOT EXISTS jobs (
    id BIGSERIAL PRIMARY KEY,
    job_name TEXT NOT NULL,
    job_full_name TEXT UNIQUE NOT NULL,
    job_display_name TEXT,
    table_name TEXT NOT NULL,
    job_type TEXT,
    job_url TEXT,
    folder_path TEXT,
    configuration JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create the builds table for supabase-event-demo job
CREATE TABLE IF NOT EXISTS builds_supabase_event_demo (
    id BIGSERIAL PRIMARY KEY,
    build_number INTEGER NOT NULL,
    build_id TEXT,
    build_url TEXT,
    result TEXT,
    duration_ms BIGINT,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    queue_time_ms BIGINT,
    node_name TEXT,
    executor_info JSONB,
    workspace_path TEXT,
    causes JSONB,
    artifacts JSONB,
    test_results JSONB,
    stages JSONB,
    environment_variables JSONB,
    custom_data JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(build_number)
);

-- Add comments
COMMENT ON TABLE jobs IS 'Metadata about Jenkins jobs using Supabase Build Recorder';
COMMENT ON TABLE builds_supabase_event_demo IS 'Build data for job supabase-event-demo';
