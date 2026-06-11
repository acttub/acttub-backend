# SOMA-98 Coaching API Design

## Scope

SOMA-98 implements one alpha-stage coaching execution API:

```http
POST /api/v1/coachings
Content-Type: multipart/form-data
```

This API accepts a performance video and coaching context, runs AI coaching synchronously, stores the input and AI result in the database, and returns the completed result in the same response.

User evaluation of the AI result is out of scope for this endpoint. It will be stored by a separate follow-up API after the user reviews the result.

## Request

Fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `video` | file | yes | Performance video uploaded by the user. |
| `performanceIntent` | string | yes | What the actor intended to express. |
| `focusCategory` | enum | yes | Category the user wants feedback on. |

Allowed `focusCategory` values:

| Value | Meaning |
| --- | --- |
| `VOICE` | Voice and vocal delivery |
| `EMOTION` | Emotional expression |
| `MOVEMENT` | Body and movement |
| `DELIVERY` | Line delivery |

## Successful Flow

1. Validate multipart request fields.
2. Store the coaching row with status `ANALYZING`.
3. Store video metadata and the input context.
4. Run AI coaching analysis synchronously in the request.
5. Store the AI result and feedback card fields.
6. Update coaching status to `COMPLETED`.
7. Return `201 Created` with the completed coaching result.

## Success Response

```json
{
  "coachingId": "coaching_123",
  "status": "COMPLETED",
  "input": {
    "performanceIntent": "차분하지만 단호한 감정",
    "focusCategory": "EMOTION"
  },
  "result": {
    "sceneIntent": "인물이 감정을 억누르며 상대를 설득하려는 장면으로 해석됩니다.",
    "strength": "시선 유지와 말의 속도가 의도한 단호함을 잘 받쳐줍니다.",
    "focus": "감정이 커지는 지점에서 호흡이 먼저 흔들려 대사의 끝이 약해집니다.",
    "nextStep": "핵심 문장 직전에 한 박자 숨을 고르고 마지막 음절까지 힘을 유지해 보세요."
  }
}
```

## Validation Errors

Invalid input does not create a coaching row.

Examples:

- Missing `video`
- Empty `performanceIntent`
- Unsupported `focusCategory`
- Unsupported video content type

Response:

```http
400 Bad Request
```

```json
{
  "code": "INVALID_COACHING_REQUEST",
  "message": "Invalid coaching request."
}
```

## AI Failure

If validation succeeds but AI analysis fails, the API keeps the coaching input in the database, marks the coaching status as `FAILED`, stores the failure reason, and returns:

```http
502 Bad Gateway
```

```json
{
  "coachingId": "coaching_123",
  "status": "FAILED",
  "code": "AI_ANALYSIS_FAILED",
  "message": "Coaching analysis failed."
}
```

## Persistence

The implementation should persist enough data to support later result lookup and user evaluation:

- Coaching status and timestamps
- Uploaded video metadata
- `performanceIntent`
- `focusCategory`
- AI result fields: `sceneIntent`, `strength`, `focus`, `nextStep`
- AI failure code/message when analysis fails

The alpha implementation may start with the simplest database shape that preserves these fields. Full member/guest ownership, upload storage abstraction, and asynchronous workers are outside SOMA-98 unless explicitly added later.

## Testing

Add focused API tests for:

- Valid multipart request returns `201 Created` and `COMPLETED`
- Missing required fields return `400 Bad Request`
- Invalid `focusCategory` returns `400 Bad Request`
- AI failure returns `502 Bad Gateway` and exposes a failed coaching id
