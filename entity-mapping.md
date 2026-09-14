# JPA mapping

`database.portgre.sql` is the source of truth. Run it once against an empty
`app_auth` schema and use `spring.jpa.hibernate.ddl-auto=validate` with the
configured PostgreSQL datasource. Do not use Hibernate schema generation to
replace the script: partial unique indexes, CHECK constraints, deferred foreign
keys, delete actions, triggers and RLS remain database-managed.

| Table | Entity |
| --- | --- |
| users | User |
| roles | Role |
| permissions | Permission |
| user_roles | UserRole |
| role_permissions | RolePermission |
| user_devices | UserDevice |
| refresh_tokens | RefreshToken |
| push_registrations | PushRegistration |
| action_tokens | ActionToken |
| auth_audit_logs | AuthAuditLog |

All entities are in `com.pawpasta.glowscan_be.modal.entity`. Relationships are lazy,
unidirectional and have no ORM remove cascade; the SQL foreign keys own deletion
behavior. Join entities preserve their assignment/grant timestamps and assigner.
Database-generated timestamps are retrieved by Hibernate after insert/update.
UUID identifiers, including the primary keys of the two join entities, are generated
by JPA; numeric identities come from PostgreSQL. `user_roles` and
`role_permissions` retain unique constraints over their respective relationship
columns.
TIMESTAMPTZ uses OffsetDateTime, INET uses InetAddress, and JSONB metadata uses
Map<String, Object>. String enums mirror the SQL allowed values.

For RefreshToken, set `user`, `device`, `tokenFamilyId` and optionally
`replacedByToken`. Both foreign keys now reference their target UUID primary keys
directly. The service must verify that a device belongs to the selected user and
that a replacement token belongs to the expected rotation chain before persisting,
because those former composite-key checks are no longer database constraints.

Run `mvn -Dtest=EntityMappingTests test` to check Hibernate metadata and session
factory initialization without a database, using both an explicit registration
order and Spring's classpath scan order. PostgreSQL integration still requires
a configured datasource and the installed SQL schema.
