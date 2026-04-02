-- Reformat all invoice numbers to {INITIALS}-INV-XX per distributor and
-- change the uniqueness guarantee from global to per-distributor, which is
-- semantically correct (invoice numbers only need to be unique within a distributor).

-- Step 1: Drop the global unique constraint
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_invoice_number_key;

-- Step 2: Renumber ALL invoices per distributor sequentially (oldest first)
WITH distributor_initials AS (
    SELECT
        d.id,
        UPPER(STRING_AGG(SUBSTRING(word, 1, 1), '' ORDER BY ord)) AS initials
    FROM distributors d,
    LATERAL (
        SELECT word, ordinality AS ord
        FROM UNNEST(REGEXP_SPLIT_TO_ARRAY(TRIM(d.name), '\s+')) WITH ORDINALITY AS t(word, ordinality)
        WHERE word != ''
    ) words
    GROUP BY d.id
),
ranked AS (
    SELECT
        i.id,
        di.initials || '-INV-' AS prefix,
        ROW_NUMBER() OVER (PARTITION BY i.distributor_id ORDER BY i.created_at, i.id) AS rn
    FROM invoices i
    JOIN distributor_initials di ON di.id = i.distributor_id
)
UPDATE invoices
SET invoice_number = r.prefix || LPAD(r.rn::TEXT, 2, '0')
FROM ranked r
WHERE invoices.id = r.id;

-- Step 3: Add a per-distributor unique constraint (replaces the dropped global one)
ALTER TABLE invoices
    ADD CONSTRAINT invoices_distributor_invoice_number_key UNIQUE (distributor_id, invoice_number);
