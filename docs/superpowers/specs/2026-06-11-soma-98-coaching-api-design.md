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
  "result": {
    "summary": "감정을 억누르는 의도는 잘 보이지만, 도입부 긴장이 먼저 올라와 후반의 무너짐이 덜 살아납니다.",
    "card": {
      "sceneIntent": {
        "text": "차분하지만 단호하게 상대를 설득하려는 장면",
        "source": "ACTOR_INPUT"
      },
      "strength": {
        "timecode": "0:48",
        "signal": "시선을 유지한 채 말의 속도를 늦춘 순간",
        "why": "감정을 바로 터뜨리지 않고 버티는 힘이 보여 장면의 의도가 살아났습니다."
      },
      "focus": {
        "timecode": "0:00-0:15",
        "observedSignal": "첫 대사 전부터 어깨와 목소리가 굳어 있었습니다.",
        "rootCause": "도입부 긴장이 먼저 올라와 후반에 감정이 무너질 높이가 줄었습니다.",
        "intentGap": "참다가 무너지는 흐름보다 처음부터 긴장한 사람처럼 보였습니다.",
        "prescription": "첫 대사는 아직 괜찮은 사람처럼 시작해 보세요."
      },
      "nextStep": {
        "text": "도입부 0:00-0:15만 다시 찍어보세요.",
        "action": "RETAKE_SELECTED_RANGE",
        "targetRange": "0:00-0:15"
      }
    }
  }
}
```

The response intentionally omits the request input. The server stores request input for persistence, but the API response stays result-focused.

The result shape follows the Confluence output schema:

- `summary`: result-level one-line summary, outside the card.
- `card.sceneIntent`: intent echo used as the interpretation anchor.
- `card.strength`: one concrete good moment.
- `card.focus`: exactly one root cause and prescription.
- `card.nextStep`: one immediate action for the user.

Do not expose internal analysis labels such as score gauges, severity, axis labels, root labels, or tier labels in this alpha response. Keep those in raw/internal persistence if needed.

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
- AI result fields: `summary`, `sceneIntent`, `strength`, `focus`, `nextStep`
- AI failure code/message when analysis fails

The alpha implementation may start with the simplest database shape that preserves these fields. Full member/guest ownership, upload storage abstraction, and asynchronous workers are outside SOMA-98 unless explicitly added later.

## Source Alignment

This result contract is based on the current Confluence design:

- [SOMA-62 피드백 출력 스키마](https://hiws99.atlassian.net/wiki/spaces/TSSNN/pages/15040590/SOMA-62): canonical output schema. It defines the two-layer model and the surface card fields `scene_intent`, `strength`, `focus`, and `next_step`.
- [SOMA-60 피드백 카드 구조](https://hiws99.atlassian.net/wiki/spaces/TSSNN/pages/12124212/SOMA-60): canonical user-facing card flow: intent echo, good moment, exactly one focus, next action.
- [SOMA-84 비회원 단발성 코칭 도메인·ERD·API 설계](https://hiws99.atlassian.net/wiki/spaces/TSSNN/pages/13369345/SOMA-84+ERD+API): storage model for `CoachingResult` and `FeedbackCard`.

## Testing

Add focused API tests for:

- Valid multipart request returns `201 Created` and `COMPLETED`
- Missing required fields return `400 Bad Request`
- Invalid `focusCategory` returns `400 Bad Request`
- AI failure returns `502 Bad Gateway` and exposes a failed coaching id
