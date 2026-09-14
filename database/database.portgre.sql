-- GlowScan: custom authentication, PostgreSQL 15+ / Supabase SQL Editor.
-- INITIAL INSTALL ONLY, not an upgrade migration. Run in an empty app_auth schema.
-- No DROP, no subscriptions, no Supabase Auth integration.
-- Supabase: use the existing project database; do not create/modify auth.*.
-- Standalone PostgreSQL: optionally create database "GlowScan" separately,
-- connect to it, then execute this file. No psql-only commands in this script.
-- Private backend-only schema: never expose credentials/tokens through the Data API.

BEGIN;
CREATE SCHEMA app_auth;
REVOKE ALL ON SCHEMA app_auth FROM PUBLIC;

CREATE FUNCTION app_auth.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$;

CREATE TABLE app_auth.users (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                email VARCHAR(320) NOT NULL,
                                password_hash TEXT NOT NULL,
                                full_name VARCHAR(150),
                                avatar_url TEXT,
                                status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
                                email_verified_at TIMESTAMPTZ,
                                token_version INTEGER NOT NULL DEFAULT 1 CHECK (token_version >= 1),
                                failed_login_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
                                locked_until TIMESTAMPTZ,
                                last_login_at TIMESTAMPTZ,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                deleted_at TIMESTAMPTZ,
                                CONSTRAINT ck_users_status CHECK (status IN
                                                                  ('PENDING_VERIFICATION','ACTIVE','LOCKED','SUSPENDED','DELETED')),
                                CONSTRAINT ck_users_email CHECK (email = lower(btrim(email)) AND email <> ''),
                                CONSTRAINT ck_users_deleted CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_users_active_email ON app_auth.users(email) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_status ON app_auth.users(status);

CREATE TABLE app_auth.roles (
                                id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                code VARCHAR(50) NOT NULL UNIQUE CHECK (code = upper(code)),
                                name VARCHAR(100) NOT NULL,
                                description TEXT,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE app_auth.permissions (
                                      id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                      code VARCHAR(100) NOT NULL UNIQUE CHECK (code = upper(code)),
                                      name VARCHAR(150) NOT NULL,
                                      description TEXT,
                                      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE app_auth.user_roles (
                                     user_id UUID NOT NULL REFERENCES app_auth.users(id) ON DELETE CASCADE,
                                     role_id SMALLINT NOT NULL REFERENCES app_auth.roles(id) ON DELETE RESTRICT,
                                     assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     assigned_by UUID REFERENCES app_auth.users(id) ON DELETE SET NULL,
                                     PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role ON app_auth.user_roles(role_id);
CREATE TABLE app_auth.role_permissions (
                                           role_id SMALLINT NOT NULL REFERENCES app_auth.roles(id) ON DELETE CASCADE,
                                           permission_id SMALLINT NOT NULL REFERENCES app_auth.permissions(id) ON DELETE CASCADE,
                                           granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX ix_role_permissions_permission ON app_auth.role_permissions(permission_id);

CREATE TABLE app_auth.user_devices (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       user_id UUID NOT NULL REFERENCES app_auth.users(id) ON DELETE CASCADE,
                                       device_uuid VARCHAR(255) NOT NULL CHECK (btrim(device_uuid) <> ''),
                                       platform VARCHAR(20) NOT NULL CHECK (platform IN ('ANDROID','IOS')),
                                       device_name VARCHAR(150),
                                       device_model VARCHAR(150),
                                       os_version VARCHAR(50),
                                       app_version VARCHAR(50),
                                       trusted BOOLEAN NOT NULL DEFAULT FALSE,
                                       last_active_at TIMESTAMPTZ,
                                       created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       CONSTRAINT uq_devices_user_installation UNIQUE(user_id, device_uuid),
                                       CONSTRAINT uq_devices_id_user UNIQUE(id, user_id)
);
CREATE INDEX ix_devices_last_active ON app_auth.user_devices(last_active_at);

CREATE TABLE app_auth.refresh_tokens (
                                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                         user_id UUID NOT NULL REFERENCES app_auth.users(id) ON DELETE CASCADE,
                                         device_id UUID NOT NULL,
                                         token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    token_family_id UUID NOT NULL,
    replaced_by_token_id UUID,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(100),
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_device_owner FOREIGN KEY(device_id, user_id)
        REFERENCES app_auth.user_devices(id, user_id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_chain_identity UNIQUE(id, token_family_id, device_id, user_id),
    CONSTRAINT fk_refresh_replacement FOREIGN KEY
        (replaced_by_token_id, token_family_id, device_id, user_id)
        REFERENCES app_auth.refresh_tokens(id, token_family_id, device_id, user_id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_refresh_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_refresh_reason CHECK (revoked_at IS NOT NULL OR revoke_reason IS NULL),
    CONSTRAINT ck_refresh_replacement CHECK (replaced_by_token_id IS NULL OR
        (replaced_by_token_id <> id AND revoked_at IS NOT NULL))
);
-- One unrevoked token per family, including expired tokens until explicitly revoked.
CREATE UNIQUE INDEX ux_refresh_family_current ON app_auth.refresh_tokens(token_family_id)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_family ON app_auth.refresh_tokens(token_family_id);
CREATE INDEX ix_refresh_device ON app_auth.refresh_tokens(device_id, user_id);
CREATE INDEX ix_refresh_user_active ON app_auth.refresh_tokens(user_id, expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_expiry ON app_auth.refresh_tokens(expires_at);
COMMENT ON COLUMN app_auth.refresh_tokens.token_family_id IS
    'Stable JWT sid. Generate a fresh UUID on login; preserve during rotation. Never reopen a revoked family.';

CREATE TABLE app_auth.push_registrations (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                             device_id UUID NOT NULL REFERENCES app_auth.user_devices(id) ON DELETE CASCADE,
                                             provider VARCHAR(20) NOT NULL DEFAULT 'FCM' CHECK (provider = 'FCM'),
                                             registration_type VARCHAR(30) NOT NULL DEFAULT 'FCM_TOKEN'
                                                 CHECK (registration_type IN ('FCM_TOKEN','FID')),
                                             registration_value TEXT NOT NULL UNIQUE CHECK (btrim(registration_value) <> ''),
                                             notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                             status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','INVALID')),
                                             last_registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             last_success_at TIMESTAMPTZ,
                                             last_failure_at TIMESTAMPTZ,
                                             created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_push_device ON app_auth.push_registrations(device_id);
CREATE UNIQUE INDEX ux_push_device_active ON app_auth.push_registrations(device_id)
    WHERE status = 'ACTIVE';
COMMENT ON COLUMN app_auth.push_registrations.registration_type IS
    'Use the registration format actually supported by the configured SDK/send API. A bare FID is not interchangeable with an FCM token.';

CREATE TABLE app_auth.action_tokens (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        user_id UUID NOT NULL REFERENCES app_auth.users(id) ON DELETE CASCADE,
                                        purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL','RESET_PASSWORD')),
                                        token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_action_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_action_terminal CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);
CREATE INDEX ix_action_user_purpose ON app_auth.action_tokens(user_id, purpose);
CREATE INDEX ix_action_expiry ON app_auth.action_tokens(expires_at);
CREATE UNIQUE INDEX ux_action_pending ON app_auth.action_tokens(user_id, purpose)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
-- Expired but unconsumed tokens still occupy this unique slot.
-- Revoke them before inserting their replacement; do not use now() in an index predicate.

CREATE TABLE app_auth.auth_audit_logs (
                                          id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                          user_id UUID REFERENCES app_auth.users(id) ON DELETE SET NULL,
                                          event_type VARCHAR(80) NOT NULL,
                                          result VARCHAR(20) NOT NULL CHECK (result IN ('SUCCESS','FAILURE')),
                                          ip_address INET,
                                          user_agent TEXT,
                                          device_id UUID REFERENCES app_auth.user_devices(id) ON DELETE SET NULL,
                                          metadata JSONB NOT NULL DEFAULT '{}'::JSONB CHECK (jsonb_typeof(metadata) = 'object'),
                                          occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_audit_user_time ON app_auth.auth_audit_logs(user_id, occurred_at DESC);
CREATE INDEX ix_audit_event_time ON app_auth.auth_audit_logs(event_type, occurred_at DESC);
CREATE INDEX ix_audit_device ON app_auth.auth_audit_logs(device_id);

CREATE TRIGGER trg_users_updated BEFORE UPDATE ON app_auth.users
    FOR EACH ROW EXECUTE FUNCTION app_auth.set_updated_at();
CREATE TRIGGER trg_devices_updated BEFORE UPDATE ON app_auth.user_devices
    FOR EACH ROW EXECUTE FUNCTION app_auth.set_updated_at();
CREATE TRIGGER trg_push_updated BEFORE UPDATE ON app_auth.push_registrations
    FOR EACH ROW EXECUTE FUNCTION app_auth.set_updated_at();

INSERT INTO app_auth.roles(code, name, description) VALUES
                                                        ('USER','User','Standard authenticated user'),
                                                        ('ADMIN','Administrator','System administrator');
INSERT INTO app_auth.permissions(code, name) VALUES
                                                 ('PROFILE_READ_OWN','Read own profile'),
                                                 ('PROFILE_UPDATE_OWN','Update own profile'),
                                                 ('DEVICE_READ_OWN','Read own devices'),
                                                 ('DEVICE_REVOKE_OWN','Revoke own device sessions'),
                                                 ('USER_READ_ANY','Read users for administration'),
                                                 ('USER_MANAGE_ANY','Manage user accounts'),
                                                 ('ROLE_MANAGE','Manage roles'),
                                                 ('AUDIT_LOG_READ','Read authentication audit logs');
INSERT INTO app_auth.role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM app_auth.roles r CROSS JOIN app_auth.permissions p
WHERE r.code = 'ADMIN' OR (r.code = 'USER' AND p.code IN
                                               ('PROFILE_READ_OWN','PROFILE_UPDATE_OWN','DEVICE_READ_OWN','DEVICE_REVOKE_OWN'));

-- Defense in depth: no mobile/client access policies. Backend uses a separately
-- configured trusted DB role; grant only required privileges outside this script.
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['users','roles','permissions','user_roles','role_permissions',
        'user_devices','refresh_tokens','push_registrations','action_tokens','auth_audit_logs']
    LOOP
        EXECUTE format('ALTER TABLE app_auth.%I ENABLE ROW LEVEL SECURITY', t);
END LOOP;
END;
$$;
REVOKE ALL ON ALL TABLES IN SCHEMA app_auth FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA app_auth FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA app_auth FROM PUBLIC;
COMMIT;

-- BACKEND CONTRACT (constraints do not implement the authentication service):
-- 1. Store Argon2id/bcrypt password hashes; store SHA-256 hex of high-entropy
--    random action/refresh tokens. Never log raw tokens or passwords.
-- 2. Registration: insert user AND assign USER role in one transaction.
-- 3. Serialize token issuance/rotation/revocation on the owning users row
--    (SELECT ... FOR UPDATE), always in consistent order. Recheck status and
--    token state AFTER acquiring locks. Client must single-flight refresh.
-- 4. Rotation: lock user then old token; reject expired/revoked tokens. Reuse of
--    a replaced token revokes the whole family. For a valid token, revoke old,
--    insert new using SAME family/user/device, then link replaced_by_token_id.
--    Commit before returning tokens. New login always creates a fresh family.
-- 5. Action issuance: lock user, revoke outstanding same-purpose tokens (even
--    expired ones), then insert new token in the same transaction.
-- 6. Action consumption: lock user, UPDATE action_tokens SET consumed_at = now()
--    WHERE token_hash = :hash AND purpose = :expected_purpose
--      AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > now()
--    RETURNING user_id; require exactly one row. Perform password/email change
--    in SAME transaction; rollback consumption if the business operation fails.
-- 7. Reset/logout-all/suspend: revoke sessions, invalidate applicable push
--    bindings and increment token_version atomically. API must validate current
--    user status, token_version AND an unrevoked/unexpired family for immediate
--    access-token revocation. JWT signature validation alone is insufficient.
-- 8. Keep replaced refresh-token history for reuse detection. Purge expired
--    families as a unit only after retention; self-FKs protect chain integrity.
-- 9. FCM update: revoke old registration before activating new in a transaction.
--    Account switching must detach the previous account's push binding. Registration
--    values/device UUIDs are not authentication credentials. Send only for active
--    accounts/devices with an eligible session; do not trust client user_id.
-- 10. App uses backend endpoints, NOT direct table access. Do not expose this
--     schema or ship DB/service credentials to mobile. No Supabase auth.uid()
--     integration is assumed. Configure a least-privilege backend role separately.
-- 11. Temporary lock expiry/unlock, rate limits, JWT signing/verification, audit
--     retention, permission checks, and email delivery are backend responsibilities.
