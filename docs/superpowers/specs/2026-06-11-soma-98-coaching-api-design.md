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

The API does not accept a focus category. The coaching model decides the single most useful focus based on the actor's intent and the video analysis.

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
    "performanceIntent": "차분하지만 단호한 감정"
  },
  "result": {
    "sceneIntent": {
      "text": "차분하지만 단호하게 상대를 설득하려는 장면",
      "source": "actor_input"
    },
    "strength": {
      "timecode": "0:48",
      "axis": "emotion",
      "signal": "시선을 유지한 채 말의 속도를 늦춘 순간",
      "why": "감정을 바로 터뜨리지 않고 버티는 힘이 보여 장면의 의도가 살아났습니다.",
      "tier": "execution"
    },
    "focus": {
      "timecode": "0:00-0:15",
      "axes": ["emotion", "speech"],
      "observedSignal": "첫 대사 전부터 어깨와 목소리가 굳어 있었습니다.",
      "rootCause": "도입부 긴장이 먼저 올라와 후반에 감정이 무너질 높이가 줄었습니다.",
      "intentGap": "참다가 무너지는 흐름보다 처음부터 긴장한 사람처럼 보였습니다.",
      "prescription": "첫 대사는 아직 괜찮은 사람처럼 시작해 보세요."
    },
    "nextStep": {
      "text": "도입부 0:00-0:15만 다시 찍어보세요.",
      "action": "retake_selected_range"
    }
  }
}
```

## Analysis Pipeline

SOMA-98 should reuse the current `acttub/web` coaching analysis logic, excluding the frontend-only focus category idea. Gemini is the default provider for the alpha implementation, but the backend should keep the analysis pipeline behind a provider boundary so validation can compare other LLMs without changing the public API, database contract, or result schema.

1. Write the uploaded video to a temporary file.
2. Call the configured LLM provider adapter. The default adapter uploads the file to Gemini Files API.
3. For Gemini, poll the uploaded file until it becomes `ACTIVE`.
4. Run L0 observer once with the video file. This is the only step that directly sees the video. It extracts neutral observations with timecodes, lines, voice, face, gaze, and body movement.
5. Run L1 persona analysis in parallel over the observer text:
   - `emotion`
   - `speech`
   - `body`
   - `audience`
6. Allow partial persona failure. Continue if at least one persona returns a signal.
7. Run L2 synthesizer over the persona signals to create one single-focus feedback card.
8. Parse provider JSON into the current `CoachFeedback` structure.
9. Delete the temporary local file and any provider-uploaded remote file.

The current frontend pipeline also uses `category`, `startTime`, and `endTime`; SOMA-98 does not expose those fields unless added later. For this API, analyze the full uploaded video and pass a fixed internal category label if the imported prompt still requires one.

## LLM Provider Strategy

The application should expose one internal coaching analysis interface that returns `CoachFeedback`. Gemini is the default implementation because the current `acttub/web` pipeline already uses Gemini video upload, file polling, and JSON generation.

Default alpha provider configuration:

| Setting | Value |
| --- | --- |
| Provider | `gemini` |
| Model | `Gemini 3.0 Flash` |

Provider-specific details must stay inside adapters:

- Upload and polling mechanics
- Prompt request format
- Model name and API key
- Raw response parsing and cleanup

The rest of the coaching API should depend only on the normalized `CoachFeedback` result. During validation, another LLM can be tested by adding a second adapter and selecting it through configuration. The HTTP response shape and persisted result fields should not change when the provider changes. The default Gemini model should also be configurable, not hard-coded, so model changes do not require API changes.

Persist the provider name and model name used for each coaching result. This makes later quality comparison possible without exposing provider details in the public response.

Current result contract to preserve:

| Field | Meaning |
| --- | --- |
| `sceneIntent.text` | Actor intent echoed or inferred. |
| `sceneIntent.source` | `actor_input`, `ai_inferred`, or `actor_confirmed`. |
| `strength.timecode` | Moment where the performance worked. |
| `strength.axis` | Internal axis: `emotion`, `speech`, `face`, or `movement`. |
| `strength.signal` | What was observed. |
| `strength.why` | Why it supported the intent. |
| `strength.tier` | `execution`, `attempt`, or `encouragement`. |
| `focus.timecode` | The single most important focus range. |
| `focus.axes` | Internal axes involved in the focus. |
| `focus.observedSignal` | What was observed. |
| `focus.rootCause` | Root cause behind the observation. |
| `focus.intentGap` | Gap between actor intent and viewer impression. |
| `focus.prescription` | One concrete correction. |
| `nextStep.text` | Immediate next action. |
| `nextStep.action` | Currently `retake_selected_range`. |

Source files in `acttub/web`:

- `web/src/server/coachAnalyze.ts`
- `web/src/coach/personas.ts`
- `web/src/coach/evaluation.ts`
- `web/src/app/api/coach/analyze/route.ts`

## Validation Errors

Invalid input does not create a coaching row.

Examples:

- Missing `video`
- Empty `performanceIntent`
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
- AI provider and model name
- AI result fields: `sceneIntent`, `strength`, `focus`, `nextStep`
- AI failure code/message when analysis fails

The alpha implementation may start with the simplest database shape that preserves these fields. Full member/guest ownership, upload storage abstraction, and asynchronous workers are outside SOMA-98 unless explicitly added later.

## Testing

Add focused API tests for:

- Valid multipart request returns `201 Created` and `COMPLETED`
- Missing required fields return `400 Bad Request`
- Provider adapter result is normalized into the same `CoachFeedback` response shape
- AI failure returns `502 Bad Gateway` and exposes a failed coaching id
