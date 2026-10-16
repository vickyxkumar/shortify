-- Seeds url_mapping with :rows rows for load testing.
-- Tables are created by Hibernate (ddl-auto=update), so start the app once before running this.
-- Usage: psql -v rows=1000000 -f seed.sql

\set ON_ERROR_STOP on

-- one owner for all seeded urls (password is not a valid bcrypt hash: this user can't log in)
INSERT INTO users (email, password, username, role)
SELECT 'loadtest@shortify.local', 'not-a-login', 'loadtest', 'ROLE_USER'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'loadtest');

-- start from a clean slate for seeded rows; ids 1..N never collide with snowflake ids (~2^60)
DELETE FROM url_mapping WHERE short_url LIKE 'lt%';

INSERT INTO url_mapping (id, original_url, short_url, created_at, user_id)
SELECT i,
       'https://example.com/articles/' || i || '?utm_source=loadtest',
       'lt' || i,
       now(),
       (SELECT id FROM users WHERE username = 'loadtest')
FROM generate_series(1, :rows) AS i;

ANALYZE url_mapping;

SELECT count(*) AS seeded_rows FROM url_mapping WHERE short_url LIKE 'lt%';
