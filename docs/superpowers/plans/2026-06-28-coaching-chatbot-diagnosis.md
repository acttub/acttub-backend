# Coaching Chatbot Diagnosis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change coaching from a one-question diagnosis flow to a flexible chatbot conversation. The user uploads a video, receives the first AI question, answers through a message API, and continues for at least 3 turns and at most 7 turns before final diagnosis.

**Architecture:** Keep the existing Spring Boot layered structure. `CoachingService.create` still performs video storage and initial AI analysis, but it also stores the first assistant question as conversation turn 1. A new message service stores each user answer, calls AI to decide whether to ask another question or prepare diagnosis, enforces `min-turns=3` and `max-turns=7`, and transitions state. The final diagnosis endpoint runs only after `READY_FOR_DIAGNOSIS` and uses the stored initial context plus conversation history.

**Conversation Policy:**

```yaml
app:
  coaching:
    conversation:
      min-turns: 3
      max-turns: 7
```

- Turns count submitted user answers.
- Turns 1~2 always continue, even if AI says the context is sufficient.
- Turns 3~6 may end when AI says diagnosis is ready.
- Turn 7 always ends and transitions to `READY_FOR_DIAGNOSIS`.

---

## File Structure

- Modify `src/main/java/com/loading/acttub_backend/coaching/domain/CoachingStatus.java`: add `IN_CONVERSATION`, `READY_FOR_DIAGNOSIS`.
- Modify `src/main/java/com/loading/acttub_backend/coaching/domain/Coaching.java`: add conversation summary fields, refined context fields, and state transitions for conversation and diagnosis.
- Create `src/main/java/com/loading/acttub_backend/coaching/domain/CoachingConversationMessage.java`: stores assistant/user messages with role and turn index.
- Create `src/main/java/com/loading/acttub_backend/coaching/domain/ConversationRole.java`: `ASSISTANT`, `USER`.
- Create `src/main/java/com/loading/acttub_backend/coaching/domain/CoachingConversationResult.java`: AI result for next question or diagnosis readiness.
- Modify `src/main/java/com/loading/acttub_backend/coaching/domain/CoachingDiagnosisResult.java`: ensure it uses conversation-refined context.
- Create `src/main/java/com/loading/acttub_backend/coaching/application/model/MessageCommand.java`: user answer input model.
- Modify `src/main/java/com/loading/acttub_backend/coaching/application/model/CoachingResult.java`: include `conversation`, `dialogue`, `context`, `refinedContext`, `result`.
- Modify `src/main/java/com/loading/acttub_backend/coaching/application/port/CoachingAnalyzer.java`: add conversation method.
- Create `src/main/java/com/loading/acttub_backend/coaching/application/port/CoachingConversationMessageRepository.java`.
- Modify `src/main/java/com/loading/acttub_backend/coaching/application/service/CoachingService.java`: add message handling and update diagnosis readiness validation.
- Create `src/main/java/com/loading/acttub_backend/coaching/config/CoachingConversationProperties.java`: bind min/max turns.
- Create `src/main/java/com/loading/acttub_backend/coaching/presentation/dto/MessageRequest.java`.
- Modify `src/main/java/com/loading/acttub_backend/coaching/presentation/dto/CoachingResponse.java`: add `conversation`.
- Modify `src/main/java/com/loading/acttub_backend/coaching/presentation/CoachingController.java`: add `POST /api/v1/coachings/{coachingId}/messages`; change diagnosis endpoint to use stored conversation rather than request answer.
- Modify `src/main/java/com/loading/acttub_backend/coaching/infrastructure/ai/DefaultCoachingAnalyzer.java`: deterministic conversation behavior for tests/local fallback.
- Modify `src/main/java/com/loading/acttub_backend/coaching/infrastructure/ai/GeminiCoachingAnalyzer.java`: add conversation prompt and parser.
- Create `src/main/resources/db/migration/V11__add_coaching_conversation_messages.sql`.
- Create `src/main/resources/db/migration/postgresql/V11__add_coaching_conversation_messages.sql`.
- Modify `src/main/java/com/loading/acttub_backend/global/api/ApiExceptionHandler.java`: map message and conversation AI errors.
- Modify `src/test/java/com/loading/acttub_backend/coaching/CoachingApiIntegrationTest.java`: add message flow tests.
- Modify `src/test/java/com/loading/acttub_backend/coaching/domain/CoachingTests.java`: add state transition tests.
- Modify `src/test/java/com/loading/acttub_backend/coaching/infrastructure/ai/GeminiCoachingAnalyzerTests.java`: add conversation JSON contract tests.
- Modify `docs/specs/current-api-contract.html`: keep API docs aligned.

---

## Task 1: Pin API Contract With Failing Integration Tests

**Files:**
- Modify: `src/test/java/com/loading/acttub_backend/coaching/CoachingApiIntegrationTest.java`

- [ ] **Step 1: Update create test**

Assert that `POST /api/v1/coachings` returns:

- `status = QUESTION_READY`
- `dialogue.question`
- `conversation.turnIndex = 0`
- `conversation.minTurns = 3`
- `conversation.maxTurns = 7`
- `conversation.diagnosisReady = false`
- `context.transcript`
- `context.analysis`
- `context.measurableSignals`
- `context.diagnosticHypothesis`
- no `result`

- [ ] **Step 2: Add message continuation test**

Create a coaching, submit the first message:

```http
POST /api/v1/coachings/{coachingId}/messages
{
  "answer": "미련을 들키고 싶지 않았습니다."
}
```

Assert:

- HTTP `201`
- `status = IN_CONVERSATION`
- `dialogue.question` exists
- `conversation.turnIndex = 1`
- `conversation.diagnosisReady = false`

- [ ] **Step 3: Add min-turn enforcement test**

Use a test analyzer that returns `diagnosisReady=true` on turn 1. Assert the server still returns `IN_CONVERSATION` until turn 3.

- [ ] **Step 4: Add diagnosis-ready test**

Use a test analyzer that returns `diagnosisReady=true` on turn 3. Assert:

- `status = READY_FOR_DIAGNOSIS`
- no next `dialogue.question`
- `conversation.summary` exists
- `refinedContext.refinedSituation` exists
- `refinedContext.refinedIntent` exists

- [ ] **Step 5: Add max-turn cap test**

Use a test analyzer that always returns `diagnosisReady=false`. Submit 7 answers. Assert turn 7 returns `READY_FOR_DIAGNOSIS`.

- [ ] **Step 6: Update final diagnosis test**

Call `POST /api/v1/coachings/{coachingId}/diagnosis` only after `READY_FOR_DIAGNOSIS`. The request body is empty. Assert:

- `status = COMPLETED`
- `result.overallStrength.text`
- `result.feedbackCards[0].title`
- `result.feedbackCards[0].observations[0].timecode`
- `result.feedbackCards[0].cause`
- `result.feedbackCards[0].practiceSteps[0]`
- `result.feedbackCards[0].expectedEffect`

- [ ] **Step 7: Add validation tests**

Cover:

- empty message answer -> `INVALID_MESSAGE_REQUEST`
- missing coaching -> `COACHING_NOT_FOUND`
- message on `READY_FOR_DIAGNOSIS` or `COMPLETED` -> `COACHING_NOT_READY_FOR_MESSAGE`
- diagnosis before readiness -> `COACHING_NOT_READY_FOR_DIAGNOSIS`
- invalid path ID -> `INVALID_COACHING_ID`

- [ ] **Step 8: Run RED**

```bash
./gradlew test --tests com.loading.acttub_backend.coaching.CoachingApiIntegrationTest
```

Expected: fails because message API, conversation state, and turn policy do not exist yet.

---

## Task 2: Add Domain Model And State Transitions

**Files:**
- Modify: `CoachingStatus.java`
- Modify: `Coaching.java`
- Create: `CoachingConversationMessage.java`
- Create: `ConversationRole.java`
- Create: `CoachingConversationResult.java`
- Modify: `CoachingTests.java`

- [ ] Add statuses:

```java
ANALYZING,
QUESTION_READY,
IN_CONVERSATION,
READY_FOR_DIAGNOSIS,
COMPLETED,
FAILED
```

- [ ] Model conversation messages with:

```java
Long id;
Long coachingId;
ConversationRole role;
int turnIndex;
String content;
OffsetDateTime createdAt;
```

- [ ] Add domain methods:

```java
markQuestionReady(CoachingInitialContext context, CoachingAnalysisResult result, OffsetDateTime now)
continueConversation(CoachingConversationResult result, OffsetDateTime now)
markReadyForDiagnosis(CoachingConversationResult result, OffsetDateTime now)
completeDiagnosis(CoachingDiagnosisResult diagnosisResult, OffsetDateTime now)
```

- [ ] Keep validation before mutation for final feedback cards.

- [ ] Add tests for legal and illegal transitions.

---

## Task 3: Add Persistence

**Files:**
- Create: `V11__add_coaching_conversation_messages.sql`
- Create: `postgresql/V11__add_coaching_conversation_messages.sql`
- Create: `CoachingConversationMessageRepository.java`

- [ ] Add nullable columns to `coaching`:

```sql
conversation_summary text,
refined_situation text,
refined_intent text
```

- [ ] Add table:

```sql
create table coaching_conversation_messages (
	id bigint generated by default as identity primary key,
	coaching_id bigint not null,
	role varchar(20) not null,
	turn_index integer not null,
	content text not null,
	created_at timestamp with time zone not null,
	constraint fk_coaching_conversation_messages_coaching
		foreign key (coaching_id) references coachings(id)
);
```

- [ ] Add index:

```sql
create index idx_coaching_conversation_messages_coaching_turn
	on coaching_conversation_messages(coaching_id, turn_index, role);
```

- [ ] Run migration-backed integration tests.

---

## Task 4: Add Service Flow

**Files:**
- Modify: `CoachingService.java`
- Create: `CoachingConversationProperties.java`
- Create: `MessageCommand.java`
- Modify: `CoachingAnalyzer.java`

- [ ] Bind:

```yaml
app.coaching.conversation.min-turns=3
app.coaching.conversation.max-turns=7
```

- [ ] On create:

1. Save coaching as `ANALYZING`.
2. Store video.
3. Run initial analysis.
4. Mark `QUESTION_READY`.
5. Save first assistant question as turn 1.

- [ ] On message:

1. Validate answer.
2. Load coaching and messages.
3. Allow only `QUESTION_READY` or `IN_CONVERSATION`.
4. Save user answer using the current pending assistant turn.
5. Calculate submitted turn count.
6. Call AI conversation method.
7. If turn count < 3, force continuation.
8. If turn count >= 7, force readiness.
9. If continuing, save next assistant question and mark `IN_CONVERSATION`.
10. If ready, save summary/refined context and mark `READY_FOR_DIAGNOSIS`.

- [ ] On diagnosis:

1. Allow only `READY_FOR_DIAGNOSIS`.
2. Load initial context and full conversation messages.
3. Call final diagnosis AI.
4. Save final feedback and mark `COMPLETED`.

---

## Task 5: Update Presentation DTOs And Controller

**Files:**
- Create: `MessageRequest.java`
- Modify: `CoachingResponse.java`
- Modify: `CoachingController.java`

- [ ] Add response nested object:

```java
record Conversation(
	int turnIndex,
	int minTurns,
	int maxTurns,
	boolean diagnosisReady,
	String reason,
	String summary
) {}
```

- [ ] Add endpoint:

```java
@PostMapping(value = "/api/v1/coachings/{coachingId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
ResponseEntity<ApiEnvelope<CoachingResponse>> message(@PathVariable Long coachingId, @RequestBody MessageRequest request)
```

- [ ] Change diagnosis endpoint to not require `DiagnosisRequest`.

- [ ] Keep evaluation endpoint unchanged.

---

## Task 6: Update AI Adapters

**Files:**
- Modify: `DefaultCoachingAnalyzer.java`
- Modify: `GeminiCoachingAnalyzer.java`
- Modify: `GeminiCoachingAnalyzerTests.java`

- [ ] Add `continueConversation(...)` to `CoachingAnalyzer`.

- [ ] Gemini conversation prompt must include:

- initial input
- initial transcript
- initial analysis
- diagnostic hypothesis
- all assistant/user messages
- min turns
- max turns
- current turn count

- [ ] Gemini conversation JSON must include:

```json
{
  "diagnosisReady": false,
  "nextQuestion": "...",
  "reason": "...",
  "summary": null,
  "refinedSituation": null,
  "refinedIntent": null
}
```

or:

```json
{
  "diagnosisReady": true,
  "nextQuestion": null,
  "reason": "...",
  "summary": "...",
  "refinedSituation": "...",
  "refinedIntent": "..."
}
```

- [ ] Parser rejects invalid readiness responses:

- ready but missing summary/refined context
- not ready but missing next question

- [ ] Final diagnosis prompt uses conversation history and must not invent video facts, timecodes, or dialogue.

---

## Task 7: Update Error Handling

**Files:**
- Modify: `ApiExceptionHandler.java`

- [ ] Map:

```java
INVALID_MESSAGE_REQUEST -> 400
COACHING_NOT_READY_FOR_MESSAGE -> 409
AI_CONVERSATION_FAILED -> 502
AI_CONVERSATION_TIMEOUT -> 504
```

- [ ] Keep existing mappings for coaching, diagnosis, evaluation errors.

---

## Task 8: Update Documentation And Verify

**Files:**
- Modify: `docs/specs/current-api-contract.html`
- Keep: `docs/superpowers/specs/2026-06-28-coaching-chatbot-diagnosis-design.html`

- [ ] Ensure API docs include:

- `POST /api/v1/coachings`
- `POST /api/v1/coachings/{coachingId}/messages`
- `POST /api/v1/coachings/{coachingId}/diagnosis`
- `POST /api/v1/coachings/{coachingId}/evaluation`
- statuses
- min/max turn policy
- all new error codes

- [ ] Run:

```bash
rg -n "IN_CONVERSATION|READY_FOR_DIAGNOSIS|/messages|minTurns|maxTurns|INVALID_MESSAGE_REQUEST" docs
git diff --check
./gradlew test
```

Expected: tests pass and docs contain the new conversation contract.

---

## Self-Review Notes

- The previous one-question diagnosis flow is superseded by this plan.
- The final diagnosis endpoint no longer accepts an `answer`; answers are submitted through the message API.
- The turn count is configurable by properties but the accepted product values are min 3 and max 7.
- The plan keeps video analysis single-pass and uses stored context for all later AI calls.
