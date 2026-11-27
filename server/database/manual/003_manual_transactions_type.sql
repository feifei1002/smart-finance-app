BEGIN;

ALTER TABLE public.manual_transactions
    ADD COLUMN IF NOT EXISTS transaction_type TEXT;

UPDATE public.manual_transactions
   SET transaction_type = CASE WHEN amount > 0 THEN 'income' ELSE 'expense' END
 WHERE transaction_type IS NULL;

ALTER TABLE public.manual_transactions
    ALTER COLUMN transaction_type SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'manual_transactions_type_check'
          AND conrelid = 'public.manual_transactions'::regclass
    ) THEN
        ALTER TABLE public.manual_transactions
            ADD CONSTRAINT manual_transactions_type_check CHECK (
                (transaction_type = 'income' AND amount > 0) OR
                (transaction_type = 'expense' AND amount < 0)
            );
    END IF;
END $$;

COMMIT;