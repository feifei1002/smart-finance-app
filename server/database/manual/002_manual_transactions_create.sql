BEGIN;

ALTER TABLE public.manual_transactions
    ADD COLUMN IF NOT EXISTS create_request_id UUID,
    ADD COLUMN IF NOT EXISTS create_request_hash TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'manual_transactions_description_check'
          AND conrelid = 'public.manual_transactions'::regclass
    ) THEN
        ALTER TABLE public.manual_transactions
            ADD CONSTRAINT manual_transactions_description_check
                CHECK (char_length(btrim(description)) BETWEEN 1 AND 100) NOT VALID;
    END IF;
END $$;


CREATE UNIQUE INDEX IF NOT EXISTS manual_transactions_create_request_unique
    ON public.manual_transactions (user_id, create_request_id);

CREATE INDEX IF NOT EXISTS manual_transactions_user_timestamp_idx
    ON public.manual_transactions (user_id, transaction_timestamp DESC, created_at DESC, id DESC);

COMMIT;