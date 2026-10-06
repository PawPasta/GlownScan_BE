# Gemini Skin Analysis Integration

## Trạng thái và giới hạn

Gemini là provider giai đoạn MVP/development. Nó là mô hình multimodal tổng quát,
không phải công cụ chẩn đoán da liễu. Kết quả chỉ được mô tả là observable skincare
characteristics và luôn đi kèm disclaimer của GlowScan.

Gemini Free Tier chỉ dùng với ảnh development/demo có consent. Không gửi ảnh mặt
khách hàng vào Free Tier production vì chính sách xử lý dữ liệu/quota có thể khác
Paid Tier.

## API key setup

1. Tạo Google Cloud project riêng cho môi trường development tại Google AI Studio.
2. Tạo Gemini API key và hạn chế key cho Gemini API.
3. Lưu key server-side; không commit hoặc trả về client.

`.env.local`:

```dotenv
GLOWSCAN_GEMINI_API_KEY=replace-with-server-only-key
GLOWSCAN_GEMINI_MODEL=gemini-3.8-flash
GLOWSCAN_GEMINI_TIMEOUT=30s
```

`application.properties`:

```properties
app.gemini.api-key=${GLOWSCAN_GEMINI_API_KEY}
app.gemini.model=${GLOWSCAN_GEMINI_MODEL}
app.gemini.timeout=${GLOWSCAN_GEMINI_TIMEOUT:30s}
```

Model name và quota phải được xác nhận trong Google AI Studio trước mỗi môi trường
deploy. Không hard-code model hoặc key trong source.

## Java adapter shape

Vòng đầu nên gọi Gemini REST API qua Spring `RestClient`; project không cần thêm
Google SDK chỉ để thực hiện request đơn giản.

```java
public interface SkinAnalysisProvider {
    SkinAnalysisProviderResult analyze(AnalysisImage image);
}
```

```java
@Component
@RequiredArgsConstructor
class GeminiSkinAnalysisProvider implements SkinAnalysisProvider {

    private final RestClient geminiRestClient;
    private final GeminiProperties properties;

    @Override
    public SkinAnalysisProviderResult analyze(AnalysisImage image) {
        // Build an Interactions API request with image bytes and JSON schema.
        // Parse the model output, validate it and map it to domain DTOs.
        throw new UnsupportedOperationException("Implementation pending");
    }
}
```

`GeminiProperties` là `@ConfigurationProperties(prefix = "app.gemini")` và phải
được validate `apiKey`, `model` và `timeout` lúc startup.

## Image transport

Không để client gửi Gemini image URL trực tiếp. Backend có thể:

1. Lấy bytes của ảnh private từ storage.
2. Chuẩn hóa MIME type/kích thước, loại EXIF nhạy cảm nếu cần.
3. Gửi inline base64 cho Gemini với `image/jpeg` hoặc `image/webp`.

Gemini Files API có thể được cân nhắc cho ảnh lớn hoặc ảnh cần dùng nhiều lần.
Không tạo public Cloudinary URL chỉ để Gemini đọc được ảnh.

## Structured output contract

Gemini request phải đặt response MIME type `application/json` và JSON Schema. Schema
chỉ cho phép taxonomy nội bộ của GlowScan. Backend vẫn phải validate semantic range
và không tin tuyệt đối vào model output.

```json
{
  "type": "object",
  "properties": {
    "status": {
      "type": "string",
      "enum": ["ANALYZED", "INSUFFICIENT_IMAGE_QUALITY"]
    },
    "qualityIssues": {
      "type": "array",
      "items": { "type": "string" }
    },
    "observations": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "type": { "type": "string" },
          "severity": { "type": "integer" },
          "confidence": { "type": "number" },
          "visibility": { "type": "string" },
          "note": { "type": "string" },
          "box2d": {
            "type": "array",
            "items": { "type": "integer" }
          }
        },
        "required": ["type", "severity", "confidence", "visibility", "box2d"]
      }
    },
    "limitations": {
      "type": "array",
      "items": { "type": "string" }
    }
  },
  "required": ["status", "qualityIssues", "observations", "limitations"]
}
```

Post-parse validation:

- `severity` phải thuộc `0..4`.
- `confidence` phải thuộc `0..1`.
- `box2d` có đúng bốn phần tử, mỗi phần tử thuộc `0..1000` và đúng thứ tự.
- Observation `type` và `visibility` phải nằm trong enum domain.
- Không chấp nhận diagnosis, treatment, drug hoặc recommendation text trong `note`.

## Fixed instruction

Instruction phía server phải cố định, không nhận từ client:

```text
Analyze only observable facial skin appearance for a consumer skincare app.
Do not diagnose diseases, prescribe treatment, infer medical conditions, or make
health claims. Return only the requested JSON schema.

For every annotation, provide box2d as [ymin, xmin, ymax, xmax] normalized from
0 to 1000. Return at most eight annotations. If a feature is unclear, use
NOT_VISIBLE or report an image quality issue rather than guessing.
```

Không đưa product catalog, recommendation tools hoặc quyền ghi dữ liệu vào model
prompt ở analysis flow.

## Failure handling

| Provider result | Hành động GlowScan |
| --- | --- |
| HTTP 401/403 | Log an toàn, báo configuration error nội bộ; không retry vô hạn. |
| HTTP 429 | Backoff/retry theo quota policy; session vẫn là `QUEUED`/`RUNNING`. |
| HTTP 5xx/timeout | Retry giới hạn qua worker/outbox; sau đó `FAILED`. |
| Schema/semantic invalid | Lưu diagnostic đã redacted, đánh dấu `FAILED` với `GEMINI_RESULT_INVALID`. |
| Low-quality image | `REJECTED` hoặc `INSUFFICIENT_IMAGE_QUALITY`, yêu cầu user chụp lại. |

Không trả Gemini raw response/error body ra client và không log base64 ảnh.

## Testing

- Unit test prompt payload/schema mapper với fixture JSON hợp lệ và không hợp lệ.
- Contract test dùng mock `SkinAnalysisProvider`; không cần Gemini key khi test.
- Integration test chỉ chạy khi có environment riêng và ảnh fixture có consent.
- Theo dõi quota, latency, rejection rate, validation failures và phân bố confidence
  trước khi mở public beta.
