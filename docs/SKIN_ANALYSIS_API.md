# Skin Analysis API và Data Contract

## Trạng thái

`app_skin_analysis` database schema, JPA domain model và upload-intent table đã
có trong Flyway V5/V6. `POST` upload intent, `POST` submit và `GET` theo ID đã
được triển khai; history pagination vẫn là endpoint kế tiếp.

## API endpoints

| Method | Path | Mục đích |
| --- | --- | --- |
| `POST` | `/api/skin-analyses/upload-intent` | Tạo signed upload intent riêng cho ảnh phân tích. |
| `POST` | `/api/skin-analyses` | Submit assessment + ảnh đã upload, tạo analysis session. |
| `GET` | `/api/skin-analyses/{analysisId}` | Lấy trạng thái và kết quả của một session thuộc current user. |
| `GET` | `/api/skin-analyses` | Lấy lịch sử sessions của current user, có pagination. _Chưa triển khai._ |
| `GET` | `/api/profile/skin` | Lấy current derived skin profile. |

Tất cả endpoints yêu cầu Bearer JWT. Client không gọi Gemini trực tiếp và không
nhận Gemini API key.

## Submit a skin analysis

```http
POST /api/skin-analyses
Authorization: Bearer <access-token>
Idempotency-Key: 33ad2d76-790f-4d36-b2b3-a996f516bd7d
Content-Type: application/json
```

```json
{
  "uploadIntentId": "1f0b41df-42f5-4969-8d00-2d04148d4ab4",
  "analysisKind": "INITIAL",
  "consentVersion": "2026-10-01",
  "assessment": {
    "questionnaireVersion": "v1",
    "answers": {
      "oiliness": "HIGH",
      "sensitivity": "MEDIUM",
      "primaryConcerns": ["ACNE", "DARK_SPOTS"]
    }
  }
}
```

Response HTTP `202 Accepted`:

```json
{
  "success": true,
  "message": "Skin analysis queued",
  "data": {
    "analysisId": "f492b49d-f0a8-492b-b4f0-4c2fa2a7b7d9",
    "status": "QUEUED"
  }
}
```

## Analysis result response

```json
{
  "success": true,
  "message": "Skin analysis retrieved successfully",
  "data": {
    "analysisId": "f492b49d-f0a8-492b-b4f0-4c2fa2a7b7d9",
    "status": "SUCCEEDED",
    "completedAt": "2026-10-06T08:30:00Z",
    "image": {
      "url": "https://signed-delivery-url.example/…",
      "width": 1080,
      "height": 1440
    },
    "quality": {
      "accepted": true,
      "issues": []
    },
    "observations": [
      {
        "id": "d5f7acd0-7c91-43e8-a735-f6b126457b1a",
        "type": "VISIBLE_REDNESS",
        "severity": 2,
        "confidence": 0.78,
        "visibility": "CLEAR",
        "note": "Visible redness",
        "box2d": [520, 160, 690, 340]
      }
    ],
    "limitations": [
      "Cheek area is partly shadowed"
    ]
  }
}
```

## Annotation coordinates

`box2d` có format:

```text
[yMin, xMin, yMax, xMax]
```

Tất cả giá trị là số nguyên chuẩn hóa trong khoảng `0..1000`. Với ảnh kích thước
`imageWidth × imageHeight`, UI tính:

```text
left   = xMin / 1000 × imageWidth
top    = yMin / 1000 × imageHeight
right  = xMax / 1000 × imageWidth
bottom = yMax / 1000 × imageHeight
```

UI vẽ ellipse vừa với rectangle này. Nếu model trả `mask` polygon trong tương lai,
frontend có thể dùng polygon để vẽ overlay thay cho ellipse.

## Response và error code

`ApiResponse<T>` hiện tại vẫn là response envelope chuẩn. Khi mở rộng, thêm trường
`code` cho client xử lý machine-readable thay vì dựa vào `message`:

```text
SKIN_ANALYSIS_QUEUED
SKIN_ANALYSIS_SUCCEEDED
SKIN_ANALYSIS_NOT_FOUND
IMAGE_QUALITY_INSUFFICIENT
GEMINI_QUOTA_EXCEEDED
GEMINI_PROVIDER_UNAVAILABLE
GEMINI_RESULT_INVALID
```

HTTP statuses:

| Status | Ý nghĩa |
| --- | --- |
| `201` | Upload intent được tạo. |
| `202` | Analysis đã được queue. |
| `200` | Đọc session/result thành công. |
| `400` | Input/consent/upload intent không hợp lệ. |
| `404` | Không tìm thấy session thuộc current user. |
| `409` | Xung đột idempotency hoặc policy session. |
| `422` | Ảnh không đủ điều kiện phân tích. |
| `429` | Quota/rate limit của provider hoặc application. |
| `503` | Provider tạm thời không sẵn sàng. |

Không trả raw exception hoặc API key/provider internals cho client.

## Data model đề xuất

Đã được triển khai bằng `V5__create_skin_analysis_schema.sql`; các migration sau
chỉ được thêm mới, không sửa migration đã áp dụng.

| Object | Trường/trách nhiệm cốt lõi |
| --- | --- |
| `analysis_sessions` | `id`, `user_id`, `status`, `analysis_kind`, provider/model/prompt/schema/rules version, timestamps, retry/error metadata. |
| `analysis_images` | Session, storage public ID, MIME type, dimensions, SHA-256, asset lifecycle. |
| `assessment_snapshots` | Session, questionnaire version, answers JSONB. |
| `analysis_observations` | Session, type, severity, confidence, visibility, note, `box2d`/mask. |
| `analysis_results` | Validated raw provider JSON, schema version, limitations, quality result. |
| `analysis_consents` | Session/user, consent version, accepted timestamp, purpose. |
| `analysis_outbox` | Event/command để worker thực thi bền vững sau transaction. |

`SkinProfile` có thể lưu `last_analysis_id` và `rules_version` trong migration sau,
nhưng historical data luôn thuộc `analysis_sessions`.
