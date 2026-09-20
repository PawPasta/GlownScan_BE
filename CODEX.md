# CODEX.md — GlowScan Backend

## Mục đích

Tài liệu này là bản đồ kỹ thuật đã được đọc và đối chiếu với source hiện tại
cho các session sau. Nó tập trung vào phần đang tồn tại trong repository, không
suy diễn các chức năng chưa được triển khai.

## Snapshot đã kiểm tra

- **Ứng dụng:** `com.pawpasta.glowscan_be.GlowScanBeApplication`.
- **Nền tảng:** Java 21, Spring Boot 4.1.1, Maven, Spring MVC, Spring Security,
  Spring Data JPA, PostgreSQL, Thymeleaf, JavaMail và springdoc OpenAPI.
- **Schema JPA:** `app_auth`; Hibernate được cấu hình `ddl-auto=validate`.
- **Nguồn schema chuẩn:** `database/database.portgre.sql`, theo
  `entity-mapping.md`.
- **Môi trường:** `.env` được Spring nạp bằng `spring.config.import`; không ghi
  giá trị bí mật vào tài liệu này.
- **Giao diện API hiện có:** tất cả đặt dưới `/api/auth`, trả về
  `ResponseUtil<T> { success, message, data }`.

## Cấu trúc source

| Khu vực | Vai trò |
| --- | --- |
| `config/` | JWT HS256, resource-server security, CORS và Swagger/OpenAPI. |
| `controller/AuthController.java` | HTTP boundary cho đăng ký, xác thực email, đăng nhập, token, reset password và change password. |
| `service/` | Contract cho auth, email và token. |
| `implement/` | Nghiệp vụ auth/email/token. `AuthServiceImpl` là nơi chứa phần lớn auth flow. |
| `modal/dto/` | DTO request/response; tên package đang dùng là `modal` theo source hiện hữu. |
| `modal/entity/` | JPA entities ánh xạ schema `app_auth`. |
| `repository/` | Spring Data repositories, gồm lock/query phục vụ token. |
| `util/` | Gửi/render email HTML, response wrapper và `SecurityUtil` để resolve Bearer JWT thành `User` hiện tại. |
| `handler/AppExceptionHandler.java` | Factory tập trung cho mọi `ResponseStatusException` của auth; message client-facing là `private static final` và service gọi theo tên lỗi. |
| `init/DataInit.java` | SQL khởi tạo schema được nhúng trực tiếp. Xem lưu ý an toàn bên dưới. |
| `resources/templates/email/account-action.html` | Template chung cho email xác thực và reset mật khẩu. |

## Database và các ràng buộc quan trọng

Các bảng xác thực là `users`, `roles`, `permissions`, `user_roles`,
`role_permissions`, `user_devices`, `refresh_tokens`, `push_registrations`,
`action_tokens` và `auth_audit_logs`.

`users` dùng email đã chuẩn hóa thành lowercase, có soft-delete (`deleted_at`),
trạng thái tài khoản, `token_version`, đếm đăng nhập sai và thời điểm khóa. Mật
khẩu được lưu trong `password_hash` bằng BCrypt.

`action_tokens` đã hỗ trợ hai mục đích: `VERIFY_EMAIL` và `RESET_PASSWORD`.
Raw token không được lưu; code hiện có sinh token ngẫu nhiên 32 byte và lưu
SHA-256 hex 64 ký tự. Một partial unique index chỉ cho phép một action token
chưa `consumed_at` và chưa `revoked_at` trên mỗi cặp `(user_id, purpose)`.
Vì token hết hạn vẫn chiếm index này, phải revoke token cũ trước khi tạo token
mới.

`refresh_tokens` lưu hash của refresh token và có family để xoay token. Khi
một mật khẩu được thay đổi, các refresh token đang hoạt động của user cần được
revoke để buộc đăng nhập lại.

## Luồng auth đã xác minh

1. `register` chuẩn hóa email, kiểm tra mật khẩu 6–12 ký tự có chữ hoa và ký tự
   đặc biệt, tạo user `PENDING_VERIFICATION`, gán role `USER`, tạo action token
   `VERIFY_EMAIL`, rồi gửi email sau khi transaction commit.
2. `verify-email-token` khóa action token khi đọc, kiểm tra hash/purpose/trạng
   thái/hạn dùng, kích hoạt user và consume token.
3. `login` kiểm tra trạng thái/khóa tạm thời, cập nhật thiết bị, tạo JWT access
   token và refresh token đã hash.
4. `logout` revoke mọi refresh token hoạt động của user suy ra từ JWT.
5. `refresh-token` khóa token hiện tại, phát hiện reuse, revoke hoặc xoay token
   trong cùng family.
6. `reset-password` nhận email, tìm user chưa soft-delete, khóa record để kiểm
   tra lại, và chỉ chấp nhận account `ACTIVE`. Email không tồn tại trả `404`; các
   trạng thái còn lại trả `403`. Khi hợp lệ, token reset cũ bị revoke, một action
   token `RESET_PASSWORD` mới được tạo và email được gửi sau khi commit.
7. `verify-reset-password-token` hiện là bước hoàn tất reset: nó nhận email,
   reset token, mật khẩu mới và xác nhận mật khẩu; khóa/consume token, đổi BCrypt
   hash, tăng `token_version`, và revoke toàn bộ refresh token. Không còn bước
   xác minh token độc lập hoặc endpoint confirm.
8. `change-password` yêu cầu Bearer JWT và mật khẩu hiện tại. `SecurityUtil`
   dùng JWT đã được Spring Resource Server xác thực, lấy claim `uid`, parse UUID,
   rồi tra `User` còn hoạt động. Service khóa record trước khi đổi mật khẩu, tăng
   `token_version` và revoke refresh token.

`JwtConfig` xác thực chữ ký HS256, issuer, audience, trạng thái `ACTIVE` của
user và `token_version`. Access token chứa `uid` và `token_version`; resource
server tra `users` cho mỗi request có bearer token. Vì vậy reset/change password
vô hiệu hóa ngay access token cũ, đồng thời refresh token cũ đã bị revoke.

## API password

| Endpoint | Quyền | Body |
| --- | --- | --- |
| `POST /api/auth/reset-password` | Public | `{ "email": "member@example.com" }` |
| `POST /api/auth/verify-reset-password-token` | Public | `{ "email": "member@example.com", "resetPasswordToken": "...", "newPassword": "New#123", "confirmPassword": "New#123" }` |
| `POST /api/auth/change-password` | Bearer JWT | `{ "oldPassword": "Old#123", "newPassword": "New#123", "confirmPassword": "New#123" }` |

DTO hoàn tất reset chấp nhận alias `rawToken` cho `resetPasswordToken`. Email
reset gửi link với hai query parameter `email` và `resetPasswordToken` qua
`EmailServiceImpl.buildActionUrl`. Password mới tuân theo policy hiện có: 6–12
ký tự, ít nhất một chữ hoa và một ký tự đặc biệt. `oldPassword` chỉ dùng ở
`change-password` vì reset được xác thực bằng action token một lần.

## Cấu hình cần có

Các nhóm biến môi trường được source dùng gồm:

- Database: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`,
  `DB_SSL_MODE`, `DB_CHANNEL_BINDING`.
- JWT: `JWT_SECRET` (Base64, ít nhất 32 byte sau giải mã), `JWT_SECRET_TTL`.
- Email/front end: `MAIL_*`, `FRONTEND_DOMAIN`, `VERIFY_EMAIL_URL`,
  `RESET_PASSWORD_URL`.
- Auth policy: `VERIFY_EMAIL_TOKEN_TTL`, `RESET_PASSWORD_TOKEN_TTL`,
  `MAX_FAILED_LOGIN_ATTEMPTS`, `LOGIN_LOCK_DURATION`.

`application.properties` bind `VERIFY_EMAIL_TOKEN_TTL`,
`RESET_PASSWORD_TOKEN_TTL` (mặc định `1h`) và `REFRESH_TOKEN_TTL`.

## Quy ước và điểm cần giữ khi mở rộng auth

- Dùng `TokenService.generateActionToken()` và `hashActionToken()`, không lưu
  raw token hoặc log raw token.
- Dùng `ActionTokenRepository` với `PESSIMISTIC_WRITE` khi consume token để
  tránh hai request sử dụng cùng một link.
- Dùng `createAndScheduleActionToken`, `findValidActionToken` và
  `updatePasswordAndRevokeSessions` trong `AuthServiceImpl` cho các luồng chung
  thay vì sao chép logic tạo token hoặc đổi mật khẩu.
- Email được gửi qua `scheduleEmailAfterCommit`, không gửi trước khi dữ liệu
  commit.
- URL email phải được chọn theo `ActionTokenPurpose`: verify dùng
  `app.links.verify-email`; reset phải dùng `app.links.reset-password`.
- Endpoint reset công khai phải được khai báo `permitAll` trong `SecurityConfig`.
- Endpoint gửi reset link hiện trả `404` khi email không tồn tại theo yêu cầu
  nghiệp vụ; nếu chính sách chống dò email thay đổi, cần thay đổi contract này
  có chủ đích.
- Sau khi reset thành công, consume action token, đổi BCrypt hash, tăng
  `token_version` và revoke các refresh token hiện hành của user.
- Token access tạo trước khi bổ sung `token_version` sẽ bị từ chối sau khi deploy
  phiên bản này; client cần đăng nhập lại. Đây là hệ quả có chủ đích để bảo đảm
  token cũ không sống tiếp sau reset/change password.

## Lưu ý vận hành đã phát hiện

`DataInit` có `@PostConstruct` và chạy `DROP SCHEMA IF EXISTS app_auth CASCADE`
trước khi dựng lại schema. Điều này mâu thuẫn với hướng dẫn trong
`entity-mapping.md` rằng file SQL là nguồn chuẩn và Hibernate chỉ validate.
Không chạy full Spring context hoặc khởi động ứng dụng vào database dùng chung
trước khi xác nhận đây là database disposable. Các kiểm tra compile/unit test
không khởi động context là an toàn hơn cho thay đổi auth.

README nói đến Docker/Docker Compose, nhưng repository hiện không có compose
file. `database/database.portgre.sql` và `DataInit` đang là hai bản SQL trùng
lặp; nếu schema đổi, cần giữ chúng nhất quán hoặc loại bỏ cơ chế khởi tạo trùng
lặp một cách có chủ đích.

## Kiểm tra sau thay đổi

Trong môi trường đã kiểm tra, `mvnw.cmd` lỗi trước khi Maven khởi chạy khi gọi
từ PowerShell (`Cannot index into a null array`). Dùng Maven hệ thống:

```powershell
mvn -DskipTests compile
mvn '-Dtest=AuthServiceImplTest,JwtConfigTest,SecurityUtilTest' test
```

Chỉ chạy full integration/context test với PostgreSQL disposable đã được cho
phép, vì lưu ý `DataInit` ở trên. Với reset password, cần kiểm tra tối thiểu:
email không tồn tại/inactive bị từ chối, token cũ bị revoke khi yêu cầu lại,
token hết hạn/sai/đã dùng bị từ chối, mật khẩu mới theo policy và refresh token
bị revoke sau khi đổi mật khẩu. Các unit test hiện có kiểm tra các nhánh reset,
`SecurityUtil` và JWT bị vô hiệu hóa khi `token_version` thay đổi.
