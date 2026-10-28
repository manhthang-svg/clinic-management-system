-- V15 has already been applied to shared environments and must remain immutable.
-- Remove its database objects with a forward migration instead of rewriting history.
DROP INDEX IF EXISTS public.idx_staff_accounts_search_trgm;
DROP INDEX IF EXISTS public.idx_staff_accounts_created_at_id;

DROP FUNCTION IF EXISTS public.staff_account_search_text(TEXT, TEXT, TEXT, TEXT);
DROP FUNCTION IF EXISTS public.normalize_staff_account_search(TEXT);

-- Keep pg_trgm because extensions can be shared by unrelated database objects.
