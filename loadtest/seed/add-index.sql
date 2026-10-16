-- Index for redirect cache misses (findByShortUrl).
-- Hibernate (ddl-auto=update) creates it on startup from @Table(indexes=...) on UrlMapping, but with a
-- plain CREATE INDEX that locks writes on a big table. For the production DB, run this first instead:
-- CONCURRENTLY builds it without blocking writes. Fails if short_url already has duplicates.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_url_mapping_short_url ON url_mapping (short_url);
