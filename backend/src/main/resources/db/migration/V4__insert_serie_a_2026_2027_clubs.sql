INSERT INTO clubs (
    name,
    short_name,
    slug,
    country_code,
    active
) VALUES
    ('Atalanta', 'Atalanta', 'atalanta', 'IT', TRUE),
    ('Bologna', 'Bologna', 'bologna', 'IT', TRUE),
    ('Cagliari', 'Cagliari', 'cagliari', 'IT', TRUE),
    ('Como', 'Como', 'como', 'IT', TRUE),
    ('Fiorentina', 'Fiorentina', 'fiorentina', 'IT', TRUE),
    ('Frosinone', 'Frosinone', 'frosinone', 'IT', TRUE),
    ('Genoa', 'Genoa', 'genoa', 'IT', TRUE),
    ('Inter', 'Inter', 'inter', 'IT', TRUE),
    ('Juventus', 'Juventus', 'juventus', 'IT', TRUE),
    ('Lazio', 'Lazio', 'lazio', 'IT', TRUE),
    ('Lecce', 'Lecce', 'lecce', 'IT', TRUE),
    ('Milan', 'Milan', 'milan', 'IT', TRUE),
    ('Monza', 'Monza', 'monza', 'IT', TRUE),
    ('Napoli', 'Napoli', 'napoli', 'IT', TRUE),
    ('Parma', 'Parma', 'parma', 'IT', TRUE),
    ('Roma', 'Roma', 'roma', 'IT', TRUE),
    ('Sassuolo', 'Sassuolo', 'sassuolo', 'IT', TRUE),
    ('Torino', 'Torino', 'torino', 'IT', TRUE),
    ('Udinese', 'Udinese', 'udinese', 'IT', TRUE),
    ('Venezia', 'Venezia', 'venezia', 'IT', TRUE)
ON CONFLICT (slug) DO UPDATE SET
    name = EXCLUDED.name,
    short_name = EXCLUDED.short_name,
    country_code = EXCLUDED.country_code,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;