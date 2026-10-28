-- Supports the default stable ordering used by GET /api/v1/accounts.
CREATE INDEX idx_staff_accounts_created_at_id
    ON staff_accounts (created_at DESC, id DESC);

-- The account search is accent-insensitive and contains-match based. A normal B-tree
-- cannot support LIKE '%term%', so keep the application expression and index identical.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE FUNCTION normalize_staff_account_search(value TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
PARALLEL SAFE
STRICT
AS $$
    SELECT lower(public.unaccent('public.unaccent', value));
$$;

CREATE FUNCTION staff_account_search_text(
    username TEXT,
    full_name TEXT,
    phone_number TEXT,
    email TEXT
)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT public.normalize_staff_account_search(
        concat_ws(' ', username, full_name, phone_number, email)
    );
$$;

CREATE INDEX idx_staff_accounts_search_trgm
    ON staff_accounts
    USING gin (staff_account_search_text(username, full_name, phone_number, email) gin_trgm_ops);
