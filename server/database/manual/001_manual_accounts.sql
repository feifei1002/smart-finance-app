BEGIN;

CREATE TABLE public.manual_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    account_type TEXT NOT NULL,
    currency TEXT NOT NULL,
    starting_balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT manual_accounts_owner_id_unique UNIQUE (user_id, id),
    CONSTRAINT manual_accounts_name_check CHECK (char_length(btrim(name)) BETWEEN 1 AND 100),
    CONSTRAINT manual_accounts_type_check
        CHECK (account_type IN ('bank_account', 'cash', 'savings', 'credit_card')),
    CONSTRAINT manual_accounts_currency_check
        CHECK (currency IN ('GBP', 'USD', 'EUR', 'PLN', 'TWD')),
    CONSTRAINT manual_accounts_balance_finite CHECK (starting_balance <> 'NaN'::numeric),
    CONSTRAINT manual_accounts_starting_balance_check
        CHECK (account_type = 'credit_card' OR starting_balance >= 0)
);

CREATE INDEX manual_accounts_user_created_idx
    ON public.manual_accounts (user_id, created_at DESC, id DESC);

-- Minimal transaction storage supports computed balances.
-- Transaction create/edit/delete endpoints are a separate frontend/backend story.
CREATE TABLE public.manual_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL DEFAULT 'Others',
    transaction_timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT manual_transactions_owner_account_fkey
        FOREIGN KEY (user_id, account_id)
        REFERENCES public.manual_accounts(user_id, id) ON DELETE CASCADE,
    CONSTRAINT manual_transactions_amount_check
        CHECK (amount <> 'NaN'::numeric AND amount <> 0)
);

CREATE INDEX manual_transactions_account_idx
    ON public.manual_transactions (user_id, account_id);

COMMIT;