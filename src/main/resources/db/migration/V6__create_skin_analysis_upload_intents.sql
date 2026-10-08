CREATE TABLE IF NOT EXISTS app_skin_analysis.upload_intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_auth.users(id) ON DELETE CASCADE,
    public_id TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_skin_analysis_upload_intents_public_id CHECK (btrim(public_id) <> ''),
    CONSTRAINT ck_skin_analysis_upload_intents_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_skin_analysis_upload_intents_terminal CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);
CREATE INDEX IF NOT EXISTS ix_skin_analysis_upload_intents_user_expiry
    ON app_skin_analysis.upload_intents(user_id, expires_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_skin_analysis_upload_intents_user_pending
    ON app_skin_analysis.upload_intents(user_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_skin_analysis_upload_intents_public_id
    ON app_skin_analysis.upload_intents(public_id);

ALTER TABLE app_skin_analysis.upload_intents ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON app_skin_analysis.upload_intents FROM PUBLIC;
