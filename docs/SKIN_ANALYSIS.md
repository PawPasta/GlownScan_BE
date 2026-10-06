# Skin Analysis Core

## Trạng thái

Tài liệu này ghi nhận thiết kế đã chốt cho Skin Analysis. Migration Flyway V5 và
JPA domain persistence model đã được triển khai. Application service, worker,
upload flow, Gemini adapter và HTTP endpoints vẫn là các bước tiếp theo.

## Mục tiêu và phạm vi

GlowScan phân tích **đặc điểm da có thể quan sát trên ảnh khuôn mặt** và kết hợp
chúng với skin assessment do người dùng cung cấp. Kết quả được dùng để xây dựng
skin profile và hỗ trợ skincare guidance.

Skin Analysis không:

- Chẩn đoán bệnh da liễu hoặc bất kỳ tình trạng y khoa nào.
- Kê đơn, đề xuất thuốc, hoặc thay thế chuyên gia da liễu.
- Để AI tự quyết định sản phẩm, routine hoặc lời khuyên điều trị.

Các quyết định recommendation phải do business rules của GlowScan xử lý từ kết quả
đã chuẩn hóa và catalog sản phẩm của ứng dụng.

## Quyết định kiến trúc

- Provider giai đoạn MVP/development: Gemini Developer API.
- Gemini chỉ là `SkinAnalysisProvider`; core domain không phụ thuộc Gemini.
- MCP không tham gia inference ảnh. MCP chỉ có thể được cân nhắc sau này cho
  assistant/admin tools.
- Mỗi lần phân tích là một `AnalysisSession` bất biến. `SkinProfile` chỉ là
  projection hiện tại (current derived profile), không phải nơi lưu lịch sử.
- API chạy bất đồng bộ: client nhận `202 Accepted`, sau đó truy vấn kết quả theo
  `analysisId`.
- Gemini trả structured JSON. Backend kiểm tra lại schema/range/enum trước khi
  lưu dữ liệu hoặc cập nhật profile.

```text
Mobile/Web
  -> upload ảnh skin-analysis riêng
  -> GlowScan API tạo analysis session
  -> worker gọi SkinAnalysisProvider (Gemini)
  -> validate và chuẩn hóa observations
  -> lưu immutable result
  -> update SkinProfile projection
  -> client hiển thị kết quả
```

## Bounded context

Skin Analysis là module mới, tách khỏi `profile`:

```text
skinanalysis/
  application/      SkinAnalysisService, AnalysisWorker, ProfileProjectionService
  controller/       SkinAnalysisController và DTO API
  domain/           AnalysisSession, Observation, AssessmentSnapshot, enums
  infrastructure/   JPA repositories, Gemini adapter, storage adapter
  port/             SkinAnalysisProvider, AnalysisImageStoragePort
```

`profile` tiếp tục sở hữu `UserProfile` và `SkinProfile`; `skinanalysis` sở hữu
lịch sử ảnh, assessment và kết quả AI.

## Session lifecycle

```text
UPLOAD_PENDING -> QUEUED -> RUNNING -> SUCCEEDED
                              |           |
                              v           v
                           FAILED      profile projection updated

UPLOAD_PENDING/QUEUED/RUNNING -> REJECTED
```

- `REJECTED`: ảnh không đạt quality gate, consent không hợp lệ, hoặc input không
  đúng yêu cầu.
- `FAILED`: provider/network/schema lỗi sau khi retry theo chính sách.
- `SUCCEEDED`: Gemini result đã được backend validate và persist thành công.

Một request submit phải hỗ trợ `Idempotency-Key` để mạng chập chờn hoặc thao tác
lặp lại không sinh nhiều analysis session.

## Observable taxonomy ban đầu

Chỉ dùng các mã quan sát được sau trong MVP:

```text
VISIBLE_ACNE
VISIBLE_REDNESS
VISIBLE_DARK_SPOTS
VISIBLE_PORES
VISIBLE_DRYNESS
VISIBLE_OILINESS
UNEAVEN_TEXTURE
```

Mỗi observation có `severity` từ 0 đến 4, `confidence` từ 0 đến 1 và trạng thái
`CLEAR`, `UNCERTAIN` hoặc `NOT_VISIBLE`. Giá trị không quan sát được không đồng
nghĩa với không tồn tại.

## Quality gate

Ảnh cần được đánh giá trước khi tạo result có giá trị:

- Có đúng một khuôn mặt.
- Độ phân giải, độ nét và ánh sáng đủ cho tác vụ.
- Không bị che mạnh bởi tóc, khẩu trang, kính hoặc tay.
- Không trang điểm đậm trong flow đánh giá da.
- Không phải ảnh trùng lặp (so sánh content hash nếu áp dụng).

Nếu ảnh không đủ điều kiện, session trả `REJECTED` hoặc `SUCCEEDED` với
`INSUFFICIENT_IMAGE_QUALITY`; UI phải hướng dẫn chụp lại thay vì hiển thị nhận
định không đáng tin cậy.

## Annotation trên ảnh

Gemini không tạo một ảnh mới đã được khoanh vùng. Nó trả tọa độ marker trong JSON;
Flutter/Web hiển thị overlay trên ảnh gốc.

```text
Ảnh gốc private + annotation coordinates
  -> Flutter CustomPainter hoặc SVG/Canvas
  -> ellipse/marker + số thứ tự
  -> người dùng chạm marker để xem note và confidence
```

Lợi ích: ảnh gốc không bị thay đổi, marker co giãn theo UI và không phải lưu ảnh
khuôn mặt đã chỉnh sửa. Server chỉ tạo derivative raster nếu về sau có chức năng
download/share ảnh đã annotation.

Chi tiết schema annotation ở [SKIN_ANALYSIS_API.md](SKIN_ANALYSIS_API.md).

## Privacy và an toàn

- Ảnh skin analysis là asset riêng, không dùng lại avatar.
- Dùng private storage, `publicId` bất biến theo analysis session và signed URL
  có hạn khi cần hiển thị/tải ảnh.
- Xóa EXIF/GPS trước khi xử lý nếu pipeline storage chưa thực hiện việc này.
- Lưu consent version, thời điểm consent và mục đích xử lý.
- Có retention/deletion policy cho ảnh, analysis result và derived data.
- Không ghi raw image, Gemini API key hoặc provider response chứa dữ liệu nhạy cảm
  vào application log.
- Gemini Free Tier chỉ dùng cho development/demo với ảnh có consent. Không dùng
  ảnh mặt của khách hàng trong production Free Tier.

## Lộ trình triển khai

1. Chốt questionnaire v1, taxonomy và nội dung consent/disclaimer.
2. Thêm Flyway migration cho `skinanalysis` schema/tables.
3. Xây upload intent riêng cho analysis image và session API bất đồng bộ.
4. Tạo `SkinAnalysisProvider` mock cùng state machine và test workflow.
5. Tích hợp `GeminiSkinAnalysisProvider` theo [GEMINI_SKIN_ANALYSIS.md](GEMINI_SKIN_ANALYSIS.md).
6. Implement quality gate, validation, retry/outbox và profile projection.
7. Tích hợp overlay annotation ở Flutter/Web.
