--
-- PostgreSQL database dump
--

\restrict rBJmN3xVvf806fbTqeny8DE1ABxVj2n0uazDhAT43QRPgrPGqmENiGk6CIb1Fyk

-- Dumped from database version 18.6 (4e955f5)
-- Dumped by pg_dump version 18.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: match_tier2_category(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.match_tier2_category(p_text text) RETURNS TABLE(matched_category character varying, confidence numeric)
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_category VARCHAR;
BEGIN
    SELECT category_name INTO v_category
    FROM global_category_rules
    WHERE p_text ~* pattern
    ORDER BY priority ASC, id ASC
    LIMIT 1;

    IF v_category IS NOT NULL THEN
        RETURN QUERY SELECT v_category, 0.95::NUMERIC;
    ELSE
        RETURN QUERY SELECT 'Others'::VARCHAR, 0.00::NUMERIC;
    END IF;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: auth_rate_limits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_rate_limits (
    identifier text NOT NULL,
    action text NOT NULL,
    failed_count integer DEFAULT 0 NOT NULL,
    window_started_at timestamp with time zone DEFAULT now() NOT NULL,
    locked_until timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: bank_connection_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bank_connection_sessions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    state text NOT NULL,
    provider_id text NOT NULL,
    provider_name text NOT NULL,
    status text DEFAULT 'pending'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now(),
    access_token text,
    refresh_token text,
    token_expiry timestamp with time zone,
    available_accounts jsonb,
    expires_at timestamp with time zone
);


--
-- Name: bank_provider_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bank_provider_cache (
    cache_key text NOT NULL,
    providers_json jsonb NOT NULL,
    fetched_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: budgets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.budgets (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    category text NOT NULL,
    amount numeric(10,2) NOT NULL,
    period text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    currency text DEFAULT 'GBP'::text NOT NULL,
    CONSTRAINT budgets_period_check CHECK ((period = ANY (ARRAY['monthly'::text, 'weekly'::text])))
);


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    id integer NOT NULL,
    name character varying(50) NOT NULL,
    color_hex character varying(7) NOT NULL
);


--
-- Name: categories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.categories_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.categories_id_seq OWNED BY public.categories.id;


--
-- Name: connected_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.connected_accounts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    bank_name text NOT NULL,
    account_id text NOT NULL,
    access_token text,
    refresh_token text,
    token_expiry timestamp with time zone,
    provider text DEFAULT 'truelayer'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    account_number text,
    connection_status text DEFAULT 'connected'::text NOT NULL
);


--
-- Name: dashboard_layouts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dashboard_layouts (
    user_id uuid NOT NULL,
    card_order text DEFAULT ''::text NOT NULL,
    deleted_cards text DEFAULT ''::text NOT NULL,
    chart_cards text DEFAULT ''::text NOT NULL,
    half_positions text DEFAULT ''::text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: merchant_logos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.merchant_logos (
    merchant_key text NOT NULL,
    merchant_name text NOT NULL,
    domain text,
    logo_url text,
    lookup_status text NOT NULL,
    last_lookup_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_hash text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_hash text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: support_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.support_messages (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    type text NOT NULL,
    rating smallint,
    category text,
    message text NOT NULL,
    platform text,
    app_version text,
    language text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status text DEFAULT 'pending'::text NOT NULL,
    CONSTRAINT support_messages_category_check CHECK ((category = ANY (ARRAY['bug'::text, 'idea'::text, 'other'::text]))),
    CONSTRAINT support_messages_rating_check CHECK (((rating >= 1) AND (rating <= 5))),
    CONSTRAINT support_messages_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'sent'::text, 'failed'::text]))),
    CONSTRAINT support_messages_type_check CHECK ((type = ANY (ARRAY['feedback'::text, 'support'::text])))
);


--
-- Name: transaction_sync_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaction_sync_status (
    user_id uuid NOT NULL,
    last_attempted_sync_at timestamp with time zone,
    last_successful_sync_at timestamp with time zone,
    last_error text
);


--
-- Name: transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transactions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    connected_account_id uuid NOT NULL,
    provider_account_id text NOT NULL,
    provider_transaction_id text NOT NULL,
    merchant_name text NOT NULL,
    description text,
    category text DEFAULT 'Uncategorised'::text NOT NULL,
    account_name text NOT NULL,
    amount numeric(14,2) NOT NULL,
    currency text NOT NULL,
    transaction_type text NOT NULL,
    transaction_timestamp timestamp with time zone NOT NULL,
    imported_at timestamp with time zone DEFAULT now() NOT NULL,
    merchant_logo_url text,
    category_source text DEFAULT 'auto'::text NOT NULL,
    CONSTRAINT transactions_category_source_check CHECK ((category_source = ANY (ARRAY['auto'::text, 'manual'::text])))
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    full_name character varying(150) NOT NULL,
    email character varying(320) NOT NULL,
    password_hash character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    consent_accepted_at timestamp with time zone,
    subscription_status text DEFAULT 'free'::text NOT NULL,
    stripe_customer_id text,
    stripe_subscription_id text,
    subscription_updated_at timestamp with time zone,
    language text DEFAULT 'en'::text NOT NULL,
    currency text DEFAULT 'GBP'::text NOT NULL,
    theme text DEFAULT 'pastel_green'::text NOT NULL,
    CONSTRAINT users_currency_check CHECK ((currency = ANY (ARRAY['GBP'::text, 'USD'::text, 'EUR'::text, 'TWD'::text, 'PLN'::text]))),
    CONSTRAINT users_language_check CHECK ((language = ANY (ARRAY['en'::text, 'es'::text, 'fr'::text, 'nl'::text, 'de'::text, 'it'::text, 'pl'::text, 'zh-TW'::text])))
);


--
-- Name: categories id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories ALTER COLUMN id SET DEFAULT nextval('public.categories_id_seq'::regclass);


--
-- Name: auth_rate_limits auth_rate_limits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_rate_limits
    ADD CONSTRAINT auth_rate_limits_pkey PRIMARY KEY (identifier, action);


--
-- Name: bank_connection_sessions bank_connection_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_connection_sessions
    ADD CONSTRAINT bank_connection_sessions_pkey PRIMARY KEY (id);


--
-- Name: bank_connection_sessions bank_connection_sessions_state_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_connection_sessions
    ADD CONSTRAINT bank_connection_sessions_state_key UNIQUE (state);


--
-- Name: bank_provider_cache bank_provider_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_provider_cache
    ADD CONSTRAINT bank_provider_cache_pkey PRIMARY KEY (cache_key);


--
-- Name: budgets budgets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.budgets
    ADD CONSTRAINT budgets_pkey PRIMARY KEY (id);


--
-- Name: budgets budgets_user_id_category_period_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.budgets
    ADD CONSTRAINT budgets_user_id_category_period_key UNIQUE (user_id, category, period);


--
-- Name: categories categories_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_name_key UNIQUE (name);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: connected_accounts connected_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.connected_accounts
    ADD CONSTRAINT connected_accounts_pkey PRIMARY KEY (id);


--
-- Name: connected_accounts connected_accounts_user_account_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.connected_accounts
    ADD CONSTRAINT connected_accounts_user_account_unique UNIQUE (user_id, account_id);


--
-- Name: connected_accounts connected_accounts_user_id_account_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.connected_accounts
    ADD CONSTRAINT connected_accounts_user_id_account_id_key UNIQUE (user_id, account_id);


--
-- Name: dashboard_layouts dashboard_layouts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dashboard_layouts
    ADD CONSTRAINT dashboard_layouts_pkey PRIMARY KEY (user_id);


--
-- Name: merchant_logos merchant_logos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.merchant_logos
    ADD CONSTRAINT merchant_logos_pkey PRIMARY KEY (merchant_key);


--
-- Name: password_reset_tokens password_reset_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: support_messages support_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_messages
    ADD CONSTRAINT support_messages_pkey PRIMARY KEY (id);


--
-- Name: transaction_sync_status transaction_sync_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_sync_status
    ADD CONSTRAINT transaction_sync_status_pkey PRIMARY KEY (user_id);


--
-- Name: transactions transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT transactions_pkey PRIMARY KEY (id);


--
-- Name: transactions unique_imported_transaction; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT unique_imported_transaction UNIQUE (user_id, provider_account_id, provider_transaction_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_stripe_customer_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_stripe_customer_id_key UNIQUE (stripe_customer_id);


--
-- Name: idx_password_reset_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_tokens_user_id ON public.password_reset_tokens USING btree (user_id);


--
-- Name: idx_refresh_tokens_active_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_active_lookup ON public.refresh_tokens USING btree (token_hash) WHERE (revoked_at IS NULL);


--
-- Name: idx_refresh_tokens_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_expires_at ON public.refresh_tokens USING btree (expires_at);


--
-- Name: idx_refresh_tokens_token_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_token_hash ON public.refresh_tokens USING btree (token_hash);


--
-- Name: idx_refresh_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_user_id ON public.refresh_tokens USING btree (user_id);


--
-- Name: idx_support_messages_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_support_messages_user_created ON public.support_messages USING btree (user_id, created_at DESC);


--
-- Name: idx_transactions_user_timestamp_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transactions_user_timestamp_id ON public.transactions USING btree (user_id, transaction_timestamp DESC, id DESC);


--
-- Name: bank_connection_sessions bank_connection_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_connection_sessions
    ADD CONSTRAINT bank_connection_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: budgets budgets_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.budgets
    ADD CONSTRAINT budgets_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: connected_accounts connected_accounts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.connected_accounts
    ADD CONSTRAINT connected_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: dashboard_layouts dashboard_layouts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dashboard_layouts
    ADD CONSTRAINT dashboard_layouts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: password_reset_tokens password_reset_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: refresh_tokens refresh_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: support_messages support_messages_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_messages
    ADD CONSTRAINT support_messages_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: transaction_sync_status transaction_sync_status_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_sync_status
    ADD CONSTRAINT transaction_sync_status_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: transactions transactions_connected_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT transactions_connected_account_id_fkey FOREIGN KEY (connected_account_id) REFERENCES public.connected_accounts(id) ON DELETE CASCADE;


--
-- Name: transactions transactions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT transactions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict rBJmN3xVvf806fbTqeny8DE1ABxVj2n0uazDhAT43QRPgrPGqmENiGk6CIb1Fyk

