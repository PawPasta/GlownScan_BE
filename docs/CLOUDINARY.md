# Cloudinary profile avatar

Avatar dùng signed direct upload. File đi từ FE tới Cloudinary, không đi qua
backend. `api_secret` chỉ tồn tại trong `GLOWSCAN_CLOUDINARY_API_SECRET` ở
backend. `api_key` là identifier công khai của Cloudinary nên được trả cho FE
trong upload intent; nó không cần, và không được, lưu trong database.

## API flow

```text
FE -- JWT --> POST /api/profile/avatar/upload-intent -- signed params --> FE
FE -- multipart signed upload --> Cloudinary
FE -- JWT + uploadIntentId --> PATCH /api/profile/avatar --> app_profile.user_profiles
```

`POST /api/profile/avatar/upload-intent` trả:

```json
{
  "success": true,
  "message": "Avatar upload intent created successfully",
  "data": {
    "uploadIntentId": "1f0b41df-42f5-4969-8d00-2d04148d4ab4",
    "uploadUrl": "https://api.cloudinary.com/v1_1/<cloud-name>/image/upload",
    "cloudName": "<cloud-name>",
    "apiKey": "<api-key>",
    "timestamp": 1790827000,
    "signature": "<sha1-signature>",
    "publicId": "glowscan/profile/avatar/<current-user-id>",
    "overwrite": true,
    "expiresAt": "2026-10-01T05:05:00Z",
    "constraints": {
      "allowedFormats": ["jpg", "jpeg", "png", "webp"],
      "maxBytes": 5242880,
      "maxDimension": 2048
    }
  }
}
```

FE gửi `multipart/form-data` đến `uploadUrl` với `file`, `api_key`,
`timestamp`, `signature`, `public_id`, `overwrite` và `allowed_formats` nhận
từ response. Không thêm hoặc sửa tham số đã được ký. Khi Cloudinary trả upload
thành công, FE gọi:

```http
PATCH /api/profile/avatar
Authorization: Bearer <access-token>
Content-Type: application/json

{ "uploadIntentId": "1f0b41df-42f5-4969-8d00-2d04148d4ab4" }
```

Backend kiểm tra intent thuộc user hiện tại, chưa dùng/revoke và chưa hết hạn.
Sau đó backend suy ra delivery URL từ public ID do chính nó cấp, lưu vào
`app_profile.user_profiles.avatar_url` và đánh dấu intent đã dùng. FE không gửi
URL hoặc public ID để backend lưu.

`expiresAt` là thời hạn backend chấp nhận xác nhận intent. Cloudinary chấp nhận
signature theo `timestamp` của họ trong tối đa một giờ; nếu FE upload trễ hơn
`expiresAt`, asset có thể được Cloudinary nhận nhưng backend sẽ không liên kết
nó với profile.

## Database

`V4__create_app_cloudinary_upload_intents.sql` tạo trực tiếp schema
`app_cloudinary` và bảng `app_cloudinary.upload_intents`:

| Column | Mục đích |
| --- | --- |
| `id` | `uploadIntentId` duy nhất trả cho FE. |
| `user_id` | Ownership; FK tới `app_auth.users`. |
| `public_id` | Target avatar cố định theo user. |
| `expires_at` | Thời hạn xác nhận của intent. |
| `consumed_at`, `revoked_at` | Ngăn replay và thay thế intent cũ. |

Một user chỉ có một intent chưa dùng tại một thời điểm. Intent mới revoke intent
cũ. Bảng không lưu credential Cloudinary.

## Configuration

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

`allowed_formats` được ký vào request upload. `maxBytes` và `maxDimension`
được trả để FE kiểm tra UX; direct upload không cho backend đọc file. Cần cấu
hình policy/upload preset phía Cloudinary nếu muốn Cloudinary enforce giới hạn
dung lượng hoặc kích thước ảnh.

## Code boundaries

- `profile.application.ProfileService` quyết định ownership, TTL và persist
  avatar.
- `cloudinary.adapter.CloudinaryStorageAdapter` tạo SHA-1 signature và URL.
- `app_cloudinary.upload_intents` chỉ là state của backend, không phải credential
  store.
- Controller không biết API secret và không nhận `MultipartFile`.
