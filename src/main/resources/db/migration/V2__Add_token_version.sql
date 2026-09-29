-- ============================================
-- Token revocation
-- ============================================
--
-- Every JWT carries the version current when it was issued. Logging out bumps
-- the version, so every token issued before that stops being accepted without
-- the server having to remember individual tokens.

alter table users add column token_version integer default 0 not null;
