CREATE TABLE system_information (
    id BIGSERIAL PRIMARY KEY,
    application_name VARCHAR(100) NOT NULL,
    application_version VARCHAR(30) NOT NULL,
    environment VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_information (
    application_name,
    application_version,
    environment
) VALUES (
    'Fantacalcio Intelligence',
    '0.1.0',
    'development'
);
