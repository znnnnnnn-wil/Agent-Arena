-- Model invocation auditing has no service or persistence implementation yet.
-- Remove the unused schema surface instead of implying that calls are audited.
DROP TABLE IF EXISTS aa_model_invocation;
