# GlowScan Backend — technical map

Tài liệu này phản ánh source hiện tại. Khi có khác biệt, lấy Flyway migration,
`application.properties` và source làm bằng chứng.

## Nền tảng

- Java 21, Maven, Spring Boot 4.1.1, Spring MVC, Validation, Security OAuth2
  Resource Server, Data JPA, Flyway, PostgreSQL, JavaMail, Thymeleaf và
  springdoc 3.0.2.
- Entry point: `com.pawpasta.glowscan_be.GlowScanBeApplication`.
- Swagger UI: `/swagger-ui.html`; OpenAPI: `/v3/api-docs/**`.
- Mọi `/api/**` ngoài các route auth public đều cần Bearer JWT. CORS nhận các
  origin từ `GLOWSCAN_FRONTEND_DOMAIN`.
- `mvnw.cmd -DskipTests compile` hoạt động trên PowerShell. Test context cần
  PostgreSQL và toàn bộ cấu hình runtime.

## Modules hiện có

| Module | Vai trò |
| --- | --- |
| `auth` | Register, xác thực email, login/logout, refresh token, reset/change password và JWT. |
| `profile` | Entity user/skin profile, API đọc/cập nhật hồ sơ cá nhân, upload avatar và đọc skin profile hiện tại. |
| `cloudinary` | Ký signed direct upload và lưu upload intent tạm thời. |
| `email` | Render/gửi email xác thực và reset mật khẩu. |
| `shared` | Security context, OpenAPI, CORS, response wrapper và exception handling. |
| `notification`, `audit` | Entity/repository nền tảng, chưa có API nghiệp vụ. |

Package dùng các tên `application`, `controller`, `domain`, `infrastructure`
theo module; không còn các package `modal`, `implement`, `util` như tài liệu
cũ từng nêu.

## Database

Flyway là nguồn schema duy nhất, chạy trước Hibernate validate. Schema gồm:

- `app_auth`: user, role, device, refresh/action token và audit.
- `app_profile`: `user_profiles`, `skin_profiles`.
- `app_cloudinary`: `upload_intents` cho upload avatar signed.

Với database mới, `GLOWSCAN_FLYWAY_BASELINE_ON_MIGRATE=false` để V1 chạy đầy
đủ. Chỉ đặt biến này thành `true` khi kết nối database auth legacy đã có schema
nhưng chưa có Flyway history.

`V4__create_app_cloudinary_upload_intents.sql` tạo trực tiếp bảng
`app_cloudinary.upload_intents`. Mỗi record gắn với user, public ID, thời điểm
hết hạn và trạng thái
`consumed_at`/`revoked_at`. Bảng này không chứa Cloudinary API key hoặc API
secret. Tạo intent mới sẽ revoke intent chưa dùng trước đó của user.

Registration chỉ tạo account auth. `fullName` và các thông tin khác được cập
nhật qua user profile; luồng xác nhận avatar tạo profile tối thiểu nếu account
chưa có profile.

## Personal profile

| Endpoint | Quyền | Mục đích |
| --- | --- | --- |
| `GET /api/profile` | JWT | Đọc hồ sơ cá nhân hiện tại, gồm `fullName`, `avatarUrl`, `dateOfBirth`, `gender` và timestamps. |
| `PATCH /api/profile` | JWT | Cập nhật từng phần `fullName`, `dateOfBirth`, `gender`; không cho cập nhật avatar trực tiếp. |

`PATCH` tạo `user_profiles` nếu account chưa có profile. Field không xuất hiện
trong JSON được giữ nguyên; field có giá trị `null` sẽ được xoá. Request rỗng bị
từ chối với `400`. `fullName` tối đa 150 ký tự và không được chỉ chứa khoảng
trắng; `dateOfBirth` không được nằm trong tương lai. Giá trị `gender` hợp lệ là
`MALE`, `FEMALE`, `OTHER`, `PREFER_NOT_TO_SAY`. `GET` trả `404` nếu profile chưa
từng được tạo. Avatar tiếp tục chỉ được cập nhật qua luồng signed upload riêng.

## Cloudinary avatar

Backend dùng signed direct upload: file đi thẳng từ FE tới Cloudinary; secret
không rời backend. Chữ ký SHA-1 được tính theo signed upload parameters của
Cloudinary bằng JDK, nên không cần Cloudinary SDK runtime.

| Endpoint | Quyền | Mục đích |
| --- | --- | --- |
| `POST /api/profile/avatar/upload-intent` | JWT | Tạo intent, ký request và trả API key công khai cùng thông số upload. |
| `PATCH /api/profile/avatar` | JWT | Nhận `{ "uploadIntentId": "..." }`, tiêu thụ intent hợp lệ và lưu URL avatar được suy ra từ public ID do BE cấp. |

Response của upload intent có `uploadUrl`, `apiKey`, `timestamp`, `signature`,
`publicId`, `overwrite`, `allowedFormats` và các giới hạn UX. FE phải gửi lại
đúng `public_id`, `timestamp`, `signature`, `overwrite`, `allowed_formats`,
`api_key` và file trong multipart form tới `uploadUrl`, sau đó gọi `PATCH` với
`uploadIntentId`.

`GLOWSCAN_CLOUDINARY_API_KEY` là định danh công khai cần cho signed upload và
được trả về có chủ đích; `GLOWSCAN_CLOUDINARY_API_SECRET` không được persist,
log hay trả trong API. `maxBytes` và `maxDimension` hiện là constraint UX cho
FE vì file không đi qua backend. Muốn enforce phía Cloudinary cần cấu hình
upload preset/policy ở Cloudinary trước khi đưa lên production.

`expiresAt` trong API giới hạn thời gian backend cho phép xác nhận intent;
Cloudinary vẫn có thời hạn signature riêng dựa trên `timestamp`.

## Skin profile

`GET /api/profile/skin` trả về skin profile hiện tại của user trong JWT. Bản ghi
thuộc `app_profile.skin_profiles`, liên kết một-một với `user_profiles`, và có
các field `skinType`, `sensitivityLevel`, `notes`, `createdAt`, `updatedAt`.
Giá trị enum hợp lệ là `NORMAL`/`DRY`/`OILY`/`COMBINATION` cho skin type và
`LOW`/`MEDIUM`/`HIGH` cho sensitivity. Khi user chưa có skin profile, API trả
404. API không có route để FE tự sửa skin profile; workflow assessment/analysis
sẽ là nơi ghi dữ liệu khi các module đó được bổ sung.

## Runtime configuration

Spring đọc `.env.local` từ working directory; `.env.example` là mẫu không có
secret thật. Cloudinary cần các biến sau:

```dotenv
GLOWSCAN_CLOUDINARY_CLOUD_NAME=
GLOWSCAN_CLOUDINARY_API_KEY=
GLOWSCAN_CLOUDINARY_API_SECRET=
GLOWSCAN_CLOUDINARY_AVATAR_FOLDER=glowscan/profile/avatar
GLOWSCAN_CLOUDINARY_AVATAR_ALLOWED_FORMATS=jpg,jpeg,png,webp
GLOWSCAN_CLOUDINARY_AVATAR_MAX_BYTES=5242880
GLOWSCAN_CLOUDINARY_AVATAR_MAX_DIMENSION=2048
GLOWSCAN_CLOUDINARY_AVATAR_UPLOAD_INTENT_TTL=5m
```

## Auth API notes

`POST /api/auth/register` nhận `email`, `password`, `confirmPassword`; account
được tạo `PENDING_VERIFICATION`, gán `USER` và gửi token xác thực. Các
invariant còn lại của auth nằm trong
`auth.application.AuthService`: password BCrypt, raw token chỉ lưu SHA-256,
refresh token rotation và JWT `token_version` validation.

Thay đổi schema phải thêm migration Flyway mới, không sửa migration đã áp dụng.

`POST /api/auth/resend-verification-email` receives `email`. After the default
30-second cooldown, it revokes the previous verification token, creates a new
token with the configured verification TTL, and sends another email. Calls made
during the cooldown return HTTP `429`.

`POST /api/auth/resend-password-reset-email` applies the same token rotation and
cooldown rules to password reset emails. Repeated calls to the original
`POST /api/auth/reset-password` endpoint also respect this cooldown.

## Quy tắc commit code

Commit message dùng cấu trúc `hành động : message`.

Ví dụ: `feat : resend password function`

Các hành động thường dùng:

- `feat`: thêm chức năng mới.
- `fix`: sửa lỗi.
- `refactor`: thay đổi cấu trúc code nhưng không thay đổi nghiệp vụ.
- `test`: thêm hoặc cập nhật test.
- `docs`: cập nhật tài liệu.
- `chore`: cập nhật cấu hình, dependency hoặc công việc bảo trì.

Message cần ngắn gọn, viết ở dạng mệnh lệnh và mô tả đúng thay đổi chính của
commit.

## Exception handling

## Exception handling

Các class nghiệp vụ gọi trực tiếp `shared.handler.ApiExceptionFactory` và truyền message tại vị trí phát
sinh lỗi, ví dụ `throw ApiExceptionFactory.badRequest("Email cannot be empty")`.
Factory này tạo `ResponseStatusException` cho các status `400`, `401`, `403`,
`404`, `409`, `423`, `406` và `500`.

`shared.handler.GlobalHandlerException` là `@RestControllerAdvice` độc lập,
chịu trách nhiệm chuyển exception thành `ApiResponse.error(...)`. Với
`ResponseStatusException`, API trả reason của lỗi 4xx, còn lỗi 5xx trả message
chung `System Error`. Validation trả field message đầu tiên; `IllegalStateException`
`shared.handler.GlobalHandlerException` là `@RestControllerAdvice` độc lập,
chịu trách nhiệm chuyển exception thành `ApiResponse.error(...)`. Với
`ResponseStatusException`, API trả reason của lỗi 4xx, còn lỗi 5xx trả message
chung `System Error`. Validation trả field message đầu tiên; `IllegalStateException`
