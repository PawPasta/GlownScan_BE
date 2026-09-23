# CODEX.md — GlowScan Backend

## Mục đích và trạng thái source

Tài liệu này là bản đồ kỹ thuật của source đang có trong repository, đã đối
chiếu ở branch `feat/auth` (HEAD `6e52274`, 20-09-2026). Dùng nó để định hướng
những thay đổi tiếp theo; không suy diễn rằng các mục tiêu sản phẩm đã có code.

Backend hiện chỉ triển khai nền tảng **authentication**. Các phần skin analysis,
skin profile, routine, recommendation, product catalog, subscription và admin
được mô tả trong `README.md` là roadmap sản phẩm: chưa có controller, service,
API hay nghiệp vụ tương ứng trong source hiện tại.

## Nền tảng và entry point

- Entry point: `com.pawpasta.glowscan_be.GlowScanBeApplication`.
- Java 21, Maven, Spring Boot 4.1.1.
- Spring MVC, Validation, Spring Security OAuth2 Resource Server, Spring Data
  JPA, PostgreSQL, JavaMail, Thymeleaf, Lombok và springdoc OpenAPI 3.0.2.
- Chỉ có PostgreSQL và SMTP là dịch vụ bên ngoài đang được source dùng. Không
  có Redis, queue, object storage, payment, AI/inference service hay FCM client.
- Ứng dụng không đặt `server.port`; khi chạy thành công dùng cổng mặc định 8080.
  Swagger UI ở `/swagger-ui.html`; `/swagger-ui/**` và `/v3/api-docs/**` là
  public.

## Cấu trúc source

| Khu vực | Vai trò hiện có |
| --- | --- |
| `config/` | `JwtConfig` tạo/xác thực JWT HS256; `SecurityConfig` cấu hình stateless resource server và CORS; `SwaggerConfig` cấu hình OpenAPI. |
| `controller/AuthController.java` | HTTP boundary duy nhất, base path `/api/auth`. |
| `service/` và `implement/` | Contract và implementation cho auth, email, token. `AuthServiceImpl` chứa các auth flow. |
| `modal/dto/` | DTO request/response. Giữ tên package `modal` vì đó là tên đang có trong source. |
| `modal/entity/` | Entity JPA và enum ánh xạ schema `app_auth`. |
| `repository/` | Spring Data repository; `ActionTokenRepository`, `RefreshTokenRepository` và `UserRepository` có query/lock phục vụ auth. |
| `util/` | `ResponseUtil`, `SecurityUtil` và tiện ích render/gửi email. |
| `handler/` | `AuthExceptionHandler` là factory lỗi auth; `ExceptionHandler` là factory HTTP chung; `GlobalHandleException` chuyển lỗi ứng dụng thành response API. |
| `resources/db/migration/` | Flyway migration versioned cho schema và dữ liệu tham chiếu. |
| `resources/templates/email/account-action.html` | Template chung cho email xác thực và reset mật khẩu. |

## Database và khởi tạo

Schema PostgreSQL là `app_auth` và `app_profile`. Flyway chạy migration trước
khi Hibernate thực hiện `spring.jpa.hibernate.ddl-auto=validate`; migration là
nguồn schema duy nhất.

Các bảng là `users`, `roles`, `permissions`, `user_roles`,
`role_permissions`, `user_devices`, `refresh_tokens`, `push_registrations`,
`action_tokens`, `auth_audit_logs`, `user_profiles` và `skin_profiles`. Nghiệp vụ hiện chỉ dùng users, roles,
user roles, devices, refresh tokens và action tokens. Permission, push
registration và audit log hiện mới có schema/entity/repository, chưa có API
hay service nghiệp vụ.

`V1__create_auth_schema.sql` tạo schema auth, `V2__create_profile_schema.sql`
tạo schema profile và giữ lại dữ liệu `full_name`/`avatar_url` từ schema cũ,
và `V3__seed_auth_reference_data.sql` seed role `USER`, `ADMIN`, permission và
mapping role. Migration cũng bật RLS, thu hồi quyền `PUBLIC` và dùng
`gen_random_uuid()` qua extension `pgcrypto`.

Với database auth đã tồn tại nhưng chưa có Flyway history, cấu hình baseline ở
version 1 để migration profile và seed tiếp tục chạy mà không reset dữ liệu.

## Cấu hình runtime

Spring nạp `.env` tại thư mục làm việc qua
`spring.config.import=optional:file:.env[.properties]`; file này đã bị Git
ignore. Không đưa giá trị bí mật vào source hay tài liệu. Biến môi trường triển
khai có thể ghi đè giá trị từ `.env`.

| Nhóm | Biến cần có / mặc định |
| --- | --- |
| PostgreSQL | Bắt buộc: `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`. Mặc định: `DB_PORT=5432`, `DB_SSL_MODE=require`, `DB_CHANNEL_BINDING=require`. |
| JWT | `JWT_SECRET` phải là Base64 giải mã được tối thiểu 32 byte; `JWT_SECRET_TTL` bắt buộc. Issuer/audience hiện cố định là `glowscan` và `glowscan-api`. |
| Mail và link | Bắt buộc: `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `FRONTEND_DOMAIN`, `VERIFY_EMAIL_URL`, `RESET_PASSWORD_URL`. `MAIL_PORT` mặc định 587; SMTP bắt buộc auth và STARTTLS. |
| Auth policy | Bắt buộc: `MAX_FAILED_LOGIN_ATTEMPTS`, `LOGIN_LOCK_DURATION`. Mặc định: `VERIFY_EMAIL_TOKEN_TTL=4h`, `RESET_PASSWORD_TOKEN_TTL=1h`, `REFRESH_TOKEN_TTL=30d`. |

`FRONTEND_DOMAIN` vừa là allowed origin CORS vừa được ghép với đường dẫn email.
`SecurityConfig` chấp nhận danh sách CORS phân tách bằng dấu phẩy, nhưng cấu
hình link email cần được kiểm tra kỹ nếu dùng nhiều domain.

## HTTP API và security

Mọi endpoint hiện có đều là `POST`; `register` trả HTTP 201, các endpoint khác
mặc định 200. Controller dùng `@Valid`, OpenAPI `@Tag`/`@Operation`, và thành
công trả wrapper:

```json
{ "success": true, "message": "...", "data": {} }
```

`GlobalHandleException` bọc `ResponseStatusException` và validation error vào
`ResponseUtil.error(...)`; validation chỉ trả message field lỗi đầu tiên.
Lỗi 401/403 từ Spring Security filter chain chưa có custom entry point/access
denied handler, nên không nên giả định chúng luôn cùng wrapper này.

| Endpoint | Quyền | Request/body đáng chú ý |
| --- | --- | --- |
| `POST /api/auth/register` | Public | `email`, `fullName`, `password`, `confirmPassword`; tạo account chờ xác thực. |
| `POST /api/auth/verify-email-token` | Public | `email`, `rawToken`. |
| `POST /api/auth/login` | Public | `email`, `password`, `deviceUuid`, `deviceName`, `platform` (`ANDROID`, `IOS`, `WEBSITE`); `deviceUUid` vẫn là JSON alias tương thích. |
| `POST /api/auth/logout` | Bearer JWT | Không có body; revoke toàn bộ refresh token còn active của user hiện tại. |
| `POST /api/auth/refresh-token` | Public | `accessToken`, `refreshToken`; trả access/refresh token mới. |
| `POST /api/auth/reset-password` | Public | `email`; tạo/gửi reset link cho account active. |
| `POST /api/auth/verify-reset-password-token` | Public | `email`, `resetPasswordToken`, `newPassword`, `confirmPassword`; `rawToken` là alias tương thích. |
| `POST /api/auth/change-password` | Bearer JWT | `oldPassword`, `newPassword`, `confirmPassword`. |

`SecurityConfig` liệt kê rõ các public route ở trên; mọi `/api/**` khác bắt buộc
Bearer JWT. Chưa có `@PreAuthorize`, `@Secured` hay endpoint RBAC dù schema đã
có role/permission. Khi thêm public API, phải bổ sung rule rõ ràng vào
`SecurityConfig`; endpoint mới dưới `/api/**` mặc định sẽ cần JWT.

## Luồng auth và bất biến cần giữ

- Mật khẩu mới phải dài 6–12 ký tự, có tối thiểu một chữ hoa và một dấu câu/ký
  tự đặc biệt. Password hash dùng BCrypt; email được `strip` và lowercase.
- Register tạo `PENDING_VERIFICATION`, gán role `USER`, tạo action token
  `VERIFY_EMAIL`, rồi schedule email sau transaction commit. Verify email
  consume token, đổi account sang `ACTIVE` và đặt `email_verified_at`.
- Raw action/refresh token được sinh từ 32 random bytes và chỉ lưu SHA-256 hex
  64 ký tự. Không log hoặc persist raw token. `action_tokens` dùng
  `PESSIMISTIC_WRITE`; raw token hợp lệ phải đúng hash, purpose, chưa consume,
  chưa revoke và chưa hết hạn.
- Partial unique index của `action_tokens` chỉ cho phép một token pending trên
  mỗi `(user_id, purpose)`, kể cả token đã hết hạn. Luồng reset phải revoke token
  cũ trước khi tạo token mới.
- Login cập nhật/tạo device, trả JWT access token và refresh token đã hash theo
  token family. JWT access có claim `uid` và `token_version`; resource server
  kiểm tra HS256, issuer, audience, user còn active/chưa soft-delete và
  `token_version` trong database ở mỗi request bearer.
- Refresh khóa refresh record, rotate token trong cùng family và revoke family
  khi phát hiện reuse. Source hiện **không** đối chiếu claim `uid` của
  `accessToken` với chủ sở hữu của refresh token; không nên dựa vào sự ràng buộc
  đó cho thay đổi mới nếu chưa bổ sung nó có chủ đích.
- Reset password và change password đổi BCrypt hash, tăng `token_version` và
  revoke toàn bộ refresh token active. Vì `token_version` tăng, access token cũ
  bị resource server từ chối ngay sau đó.
- Logout chỉ revoke refresh token; không tăng `token_version`, nên access token
  hiện tại vẫn hợp lệ đến khi hết hạn.
- `EmailServiceImpl` log và nuốt lỗi gửi mail sau commit. Một transaction auth
  có thể thành công dù email delivery thất bại; hiện chưa có retry/outbox.

## Quy ước khi mở rộng

- Giữ controller mỏng: tạo DTO ở `modal.dto`, validate ở HTTP boundary, đưa
  nghiệp vụ vào `service` + `implement`, và trả `ResponseUtil`.
- Với caller đã xác thực, dùng `SecurityUtil.getUserContext()` thay vì tự parse
  Authorization header. Dùng factory trong `AuthExceptionHandler` cho lỗi auth
  client-facing; `GlobalHandleException` đã xử lý các exception này.
- Endpoint mới cần OpenAPI annotation và phải được rà security rule. Không trả
  trực tiếp entity JPA ra API.
- Với token/auth: giữ transaction, repository lock, hash raw token, revoke
  session đúng scope và gửi email bằng `scheduleEmailAfterCommit`. Không sao
  chép flow token hoặc password nếu helper hiện có đáp ứng được.
- Thay đổi schema phải thêm migration Flyway mới; không sửa migration đã được
  áp dụng trong môi trường chia sẻ.

## Build, test và hạ tầng hiện tại

Không có Dockerfile, Docker Compose, CI workflow, deployment manifest,
Makefile, test profile, Testcontainers, lint, formatter, coverage hay quality
plugin trong repository. Các mục Docker/Docker Compose/Mockito trong README
chưa có artifact tương ứng.

Source tracked chỉ có một test:
`src/test/java/com/pawpasta/glowscan_be/GlowScanBeApplicationTests.java`, một
`@SpringBootTest` `contextLoads`. Các test được nhắc đến trong CODEX cũ
(`AuthServiceImplTest`, `JwtConfigTest`, `SecurityUtilTest`) và
`EntityMappingTests` được nhắc trong `entity-mapping.md` không tồn tại trong
source hiện tại; các report/class cũ dưới `target/` không phải test source.

Trên Windows, Maven hệ thống là lựa chọn khả dụng trong workspace đã kiểm tra:

```powershell
mvn -DskipTests compile
mvn -DskipTests package
mvn spring-boot:run
```

`mvnw.cmd` hiện lỗi trong PowerShell với `Cannot index into a null array`.
POSIX wrapper đang được track không có executable bit; nếu dùng POSIX, gọi
`sh ./mvnw ...` hoặc sửa permission có chủ đích. Các lệnh cần tải dependency sẽ
cần truy cập Maven Central nếu cache chưa có.

Chỉ chạy `mvn test` hoặc `mvn -Dtest=GlowScanBeApplicationTests test` sau khi
cung cấp đủ cấu hình và trỏ tới PostgreSQL disposable, vì test này khởi động full
Spring context và chạy Flyway migration. Trước khi có test profile an toàn, ưu
tiên unit test mock repository/service cho logic mới.

## Tài liệu cần đọc với source

- `README.md` mô tả product vision và có hướng dẫn Docker/wrapper chưa khớp với
  artifact hiện có.
- `entity-mapping.md` hữu ích cho bảng/entity mapping và migration tương ứng.
- Khi tài liệu và source mâu thuẫn, lấy source, `application.properties`,
  `SecurityConfig` và Flyway migration làm bằng chứng trước khi thay đổi hành vi.
