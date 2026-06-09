-- Backfill: any OPEN period whose start_date is in the future should be FUTURE
UPDATE gl_periods
SET status = 'FUTURE'
WHERE status = 'OPEN'
  AND start_date > CURRENT_DATE;
