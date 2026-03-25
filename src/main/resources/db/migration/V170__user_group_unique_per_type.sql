-- Drop the old unique constraint (distributor_id, name) which is too broad,
-- and replace with (distributor_id, name, user_type_id) so the same group name
-- can coexist for different user types within the same distributor.

-- Drop by the auto-generated name (PostgreSQL convention: table_col1_col2_key)
ALTER TABLE user_groups
  DROP CONSTRAINT IF EXISTS user_groups_distributor_id_name_key;

-- Also drop by explicit name in case it was created with a different name
DO $$
DECLARE
  cname TEXT;
BEGIN
  SELECT con.conname INTO cname
  FROM   pg_constraint con
  JOIN   pg_class     rel ON rel.oid = con.conrelid
  WHERE  rel.relname = 'user_groups'
    AND  con.contype = 'u'
    AND  array_to_string(ARRAY(
           SELECT attname FROM pg_attribute
           WHERE  attrelid = con.conrelid
             AND  attnum = ANY(con.conkey)
           ORDER BY attnum
         ), ',') = 'distributor_id,name';

  IF cname IS NOT NULL THEN
    EXECUTE format('ALTER TABLE user_groups DROP CONSTRAINT IF EXISTS %I', cname);
  END IF;
END;
$$;

-- Add the correct composite unique constraint including user_type_id
ALTER TABLE user_groups
  ADD CONSTRAINT uq_user_groups_distributor_name_type
  UNIQUE (distributor_id, name, user_type_id);
