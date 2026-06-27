# Coaching Chatbot Diagnosis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change coaching creation from immediate final feedback to a two-step chatbot diagnosis flow: initial video analysis returns one question and context, then user answer produces final diagnosis.

**Architecture:** Keep the existing Spring Boot layered structure. `CoachingService.create` will persist video and initial AI context, mark the coaching `QUESTION_READY`, and return a new initial-response DTO. A new diagnosis endpoint will validate the answer, reuse stored initial context, call AI without re-uploading video, persist refined context and final `CoachFeedback`, then mark the coaching `COMPLETED`.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring MVC, Spring Data JPA, Flyway SQL migrations, Jackson, JUnit 5, MockMvc, H2 test profile, Gemini REST API.

---

## File Structure

- Modify `src/main/java/com/loading/acttub_backend/coaching/domain/CoachingStatus.java`: add `QUESTION_READY`.
- Modify `src/main/java/com/loading/acttub_backend/coaching/domain/Coaching.java`: add initial context fields, refined context fields, state transitions, getters.
- Create `src/main/java/com/loading/acttub_backend/coaching/domain/CoachingInitialContext.java`: immutable domain value for the first AI response.
- Create `src/main/java/com/loading/acttub_backend/coaching/domain/CoachingDiagnosisResult.java`: immutable domain value for the final answer-refined diagnosis.
- Create `src/main/java/com/loading/acttub_backend/coaching/domain/MeasurableSignal.java`: immutable measurable observation value.
- Create `src/main/java/com/loading/acttub_backend/coaching/domain/MeasurableSignalsConverter.java`: JPA converter for storing measurable signals as JSON text.
- Modify `src/main/java/com/loading/acttub_backend/coaching/domain/CoachingAnalysisResult.java`: represent initial context instead of final feedback for video analysis.
- Modify `src/main/java/com/loading/acttub_backend/coaching/application/port/CoachingAnalyzer.java`: add a final diagnosis method.
- Modify `src/main/java/com/loading/acttub_backend/coaching/application/service/CoachingService.java`: split create and diagnosis completion.
- Create `src/main/java/com/loading/acttub_backend/coaching/application/model/DiagnosisCommand.java`: answer input model.
- Modify `src/main/java/com/loading/acttub_backend/coaching/application/model/CoachingResult.java`: include initial context and refined context.
- Create `src/main/java/com/loading/acttub_backend/coaching/presentation/dto/DiagnosisRequest.java`: request body for diagnosis.
- Modify `src/main/java/com/loading/acttub_backend/coaching/presentation/dto/CoachingResponse.java`: include `dialogue`, `context`, and `refinedContext`.
- Modify `src/main/java/com/loading/acttub_backend/coaching/presentation/CoachingController.java`: add diagnosis endpoint and response mapping.
- Modify `src/main/java/com/loading/acttub_backend/coaching/infrastructure/ai/DefaultCoachingAnalyzer.java`: return deterministic initial context and final diagnosis.
- Modify `src/main/java/com/loading/acttub_backend/coaching/infrastructure/ai/GeminiCoachingAnalyzer.java`: add initial-analysis parser and final-diagnosis parser.
- Create `src/main/resources/db/migration/V10__add_coaching_chatbot_diagnosis.sql`: H2-compatible migration.
- Create `src/main/resources/db/migration/postgresql/V10__add_coaching_chatbot_diagnosis.sql`: PostgreSQL migration.
- Modify `src/main/java/com/loading/acttub_backend/global/api/ApiExceptionHandler.java`: map diagnosis errors.
- Modify `src/test/java/com/loading/acttub_backend/coaching/CoachingApiIntegrationTest.java`: update current behavior tests and add diagnosis tests.
- Modify `src/test/java/com/loading/acttub_backend/coaching/domain/CoachingTests.java`: test state transitions.
- Modify `src/test/java/com/loading/acttub_backend/coaching/infrastructure/ai/GeminiCoachingAnalyzerTests.java`: test both AI JSON contracts.
- Modify `docs/specs/current-api-contract.html`: update API docs after implementation.

## Task 1: Pin New API Contract With Failing Integration Tests

**Files:**
- Modify: `src/test/java/com/loading/acttub_backend/coaching/CoachingApiIntegrationTest.java`

- [ ] **Step 1: Replace the immediate-completion expectation**

Replace `createsCompletedCoachingWithAiResult` with:

```java
@Test
void createsQuestionReadyCoachingWithInitialContext() throws Exception {
	MockMultipartFile video = video("video/mp4");

	mockMvc.perform(coachingRequest(video))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.coachingId", notNullValue()))
			.andExpect(jsonPath("$.data.status").value("QUESTION_READY"))
			.andExpect(jsonPath("$.data.completedAt").doesNotExist())
			.andExpect(jsonPath("$.data.input.genre").value("영화"))
			.andExpect(jsonPath("$.data.input.situation").value("헤어진 연인을 우연히 다시 만난 상황"))
			.andExpect(jsonPath("$.data.input.characterSetting").value("감정을 쉽게 드러내지 않는 배우 지망생"))
			.andExpect(jsonPath("$.data.input.subtext").value("아직 미련이 있지만 괜찮은 척한다"))
			.andExpect(jsonPath("$.data.dialogue.question", notNullValue()))
			.andExpect(jsonPath("$.data.context.transcript", notNullValue()))
			.andExpect(jsonPath("$.data.context.analysis", notNullValue()))
			.andExpect(jsonPath("$.data.context.measurableSignals[0].name", notNullValue()))
			.andExpect(jsonPath("$.data.context.measurableSignals[0].value", notNullValue()))
			.andExpect(jsonPath("$.data.context.measurableSignals[0].unit", notNullValue()))
			.andExpect(jsonPath("$.data.context.measurableSignals[0].evidence", notNullValue()))
			.andExpect(jsonPath("$.data.context.diagnosticHypothesis", notNullValue()))
			.andExpect(jsonPath("$.data.result").doesNotExist());
}
```

- [ ] **Step 2: Add final diagnosis success test**

Add:

```java
@Test
void completesDiagnosisWithUserAnswer() throws Exception {
	String coachingId = createCoaching();

	mockMvc.perform(submitDiagnosis(coachingId, "상대가 나를 약하게 볼까 봐 미련을 숨기고 싶었어요."))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.coachingId").value(coachingId))
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.completedAt", notNullValue()))
			.andExpect(jsonPath("$.data.refinedContext.answer").value("상대가 나를 약하게 볼까 봐 미련을 숨기고 싶었어요."))
			.andExpect(jsonPath("$.data.refinedContext.refinedSituation", notNullValue()))
			.andExpect(jsonPath("$.data.refinedContext.refinedIntent", notNullValue()))
			.andExpect(jsonPath("$.data.result.overallStrength.text", notNullValue()))
			.andExpect(jsonPath("$.data.result.feedbackCards[0].title", notNullValue()))
			.andExpect(jsonPath("$.data.result.feedbackCards[0].observations[0].timecode", notNullValue()))
			.andExpect(jsonPath("$.data.result.feedbackCards[0].cause", notNullValue()))
			.andExpect(jsonPath("$.data.result.feedbackCards[0].practiceSteps[0]", notNullValue()))
			.andExpect(jsonPath("$.data.result.feedbackCards[0].expectedEffect", notNullValue()));
}
```

- [ ] **Step 3: Add diagnosis validation tests**

Add:

```java
@Test
void rejectsEmptyDiagnosisAnswer() throws Exception {
	String coachingId = createCoaching();

	mockMvc.perform(submitDiagnosis(coachingId, "   "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_DIAGNOSIS_REQUEST"))
			.andExpect(jsonPath("$.error.details.fields[0]").value("answer"));
}

@Test
void rejectsDiagnosisForMissingCoaching() throws Exception {
	mockMvc.perform(submitDiagnosis("999999", "상대에게 약하게 보이고 싶지 않았어요."))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COACHING_NOT_FOUND"))
			.andExpect(jsonPath("$.error.details.coachingId").value("999999"));
}

@Test
void rejectsDuplicateDiagnosis() throws Exception {
	String coachingId = createCoaching();
	submitDiagnosis(coachingId, "상대에게 약하게 보이고 싶지 않았어요.")
			.andExpect(status().isCreated());

	submitDiagnosis(coachingId, "다시 답변합니다.")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COACHING_NOT_READY_FOR_DIAGNOSIS"))
			.andExpect(jsonPath("$.error.details.status").value("COMPLETED"));
}
```

- [ ] **Step 4: Add diagnosis helper**

Add near `submitEvaluation`:

```java
private org.springframework.test.web.servlet.RequestBuilder submitDiagnosis(String coachingId, String answer) {
	return post("/api/v1/coachings/{coachingId}/diagnosis", coachingId)
			.contentType("application/json")
			.content("""
					{
					  "answer": "%s"
					}
					""".formatted(answer));
}
```

- [ ] **Step 5: Run tests and verify RED**

Run:

```bash
./gradlew test --tests com.loading.acttub_backend.coaching.CoachingApiIntegrationTest
```

Expected: fails because `QUESTION_READY`, diagnosis endpoint, and new response fields do not exist.

## Task 2: Add Domain Values And State Transitions

**Files:**
- Create: `src/main/java/com/loading/acttub_backend/coaching/domain/MeasurableSignal.java`
- Create: `src/main/java/com/loading/acttub_backend/coaching/domain/MeasurableSignalsConverter.java`
- Create: `src/main/java/com/loading/acttub_backend/coaching/domain/CoachingInitialContext.java`
- Create: `src/main/java/com/loading/acttub_backend/coaching/domain/CoachingDiagnosisResult.java`
- Modify: `src/main/java/com/loading/acttub_backend/coaching/domain/CoachingStatus.java`
- Modify: `src/main/java/com/loading/acttub_backend/coaching/domain/Coaching.java`
- Modify: `src/test/java/com/loading/acttub_backend/coaching/domain/CoachingTests.java`

- [ ] **Step 1: Write failing domain tests**

Add to `CoachingTests`:

```java
@Test
void marksQuestionReadyWithInitialContext() {
	Coaching coaching = Coaching.analyzing(
			"영화",
			null,
			"헤어진 연인을 우연히 다시 만난 상황",
			"감정을 쉽게 드러내지 않는 배우 지망생",
			"아직 미련이 있지만 괜찮은 척한다",
			"scene.mp4",
			"video/mp4",
			10L,
			OffsetDateTime.parse("2026-06-28T10:00:00+09:00")
	);

	coaching.markQuestionReady(initialContext(), analysisResult(), OffsetDateTime.parse("2026-06-28T10:00:01+09:00"));

	assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.QUESTION_READY);
	assertThat(coaching.getInitialQuestion()).isEqualTo("이 장면에서 숨기고 싶은 감정은 무엇이었나요?");
	assertThat(coaching.getInitialContext().measurableSignals()).hasSize(1);
	assertThat(coaching.getCompletedAt()).isNull();
}

@Test
void completesDiagnosisWithRefinedContextAndFeedback() {
	Coaching coaching = Coaching.analyzing(
			"영화",
			null,
			"헤어진 연인을 우연히 다시 만난 상황",
			"감정을 쉽게 드러내지 않는 배우 지망생",
			"아직 미련이 있지만 괜찮은 척한다",
			"scene.mp4",
			"video/mp4",
			10L,
			OffsetDateTime.parse("2026-06-28T10:00:00+09:00")
	);
	coaching.markQuestionReady(initialContext(), analysisResult(), OffsetDateTime.parse("2026-06-28T10:00:01+09:00"));

	coaching.completeDiagnosis("상대가 나를 약하게 볼까 봐 숨겼어요.", diagnosisResult(), OffsetDateTime.parse("2026-06-28T10:00:02+09:00"));

	assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.COMPLETED);
	assertThat(coaching.getUserAnswer()).isEqualTo("상대가 나를 약하게 볼까 봐 숨겼어요.");
	assertThat(coaching.getRefinedSituation()).isEqualTo("약하게 보이고 싶지 않아 미련을 숨기는 상황");
	assertThat(coaching.getRefinedIntent()).isEqualTo("미련을 숨기며 상대의 반응을 확인한다");
	assertThat(coaching.getFeedback().feedbackCards()).hasSize(1);
}
```

Add helper methods in the same test class:

```java
private CoachingInitialContext initialContext() {
	return new CoachingInitialContext(
			"이 장면에서 숨기고 싶은 감정은 무엇이었나요?",
			"안녕. 오랜만이네.",
			"감정을 참는 선택이 보입니다.",
			List.of(new MeasurableSignal("pauseCount", BigDecimal.valueOf(2), "count", "두 번 멈춤")),
			"숨기는 이유가 아직 덜 선명한 것 같아요."
	);
}

private CoachingAnalysisResult analysisResult() {
	return new CoachingAnalysisResult("gemini", "test-model", BigDecimal.ZERO, "test-prompt", initialContext());
}

private CoachingDiagnosisResult diagnosisResult() {
	return new CoachingDiagnosisResult(
			"약하게 보이고 싶지 않아 미련을 숨기는 상황",
			"미련을 숨기며 상대의 반응을 확인한다",
			new CoachFeedback(
					new CoachFeedback.OverallStrength("전반적으로 버티는 선택이 좋았어요."),
					List.of(new CoachFeedback.FeedbackCard(
							1,
							"첫 멈춤 전에 숨기는 이유를 분명히 잡아보세요",
							List.of(new CoachFeedback.Observation("0:28", "대사 사이 멈춤이 보였어요.")),
							"왜냐면 방어의 이유가 아직 충분히 걸리지 않은 것 같아요.",
							List.of("첫 대사 전 시선을 2초 고정하세요."),
							"그럼 숨기는 이유가 선명해져요."
					))
			)
	);
}
```

Required imports: `java.math.BigDecimal`, `java.time.OffsetDateTime`, `java.util.List`, AssertJ `assertThat`.

- [ ] **Step 2: Run domain tests and verify RED**

Run:

```bash
./gradlew test --tests com.loading.acttub_backend.coaching.domain.CoachingTests
```

Expected: compilation fails because new domain classes and methods do not exist.

- [ ] **Step 3: Add domain records**

Create `MeasurableSignal.java`:

```java
package com.loading.acttub_backend.coaching.domain;

import java.math.BigDecimal;

public record MeasurableSignal(
		String name,
		BigDecimal value,
		String unit,
		String evidence
) {
}
```

Create `CoachingInitialContext.java`:

```java
package com.loading.acttub_backend.coaching.domain;

import java.util.List;

public record CoachingInitialContext(
		String question,
		String transcript,
		String analysis,
		List<MeasurableSignal> measurableSignals,
		String diagnosticHypothesis
) {
}
```

Create `CoachingDiagnosisResult.java`:

```java
package com.loading.acttub_backend.coaching.domain;

public record CoachingDiagnosisResult(
		String refinedSituation,
		String refinedIntent,
		CoachFeedback feedback
) {
}
```

Create `MeasurableSignalsConverter.java`:

```java
package com.loading.acttub_backend.coaching.domain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class MeasurableSignalsConverter implements AttributeConverter<List<MeasurableSignal>, String> {

	private static final ObjectMapper objectMapper = new ObjectMapper();
	private static final TypeReference<List<MeasurableSignal>> SIGNALS_TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(List<MeasurableSignal> attribute) {
		if (attribute == null) {
			return "[]";
		}
		try {
			return objectMapper.writeValueAsString(attribute);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to serialize measurable signals.", e);
		}
	}

	@Override
	public List<MeasurableSignal> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return objectMapper.readValue(dbData, SIGNALS_TYPE);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to deserialize measurable signals.", e);
		}
	}
}
```

- [ ] **Step 4: Update status and analysis result**

Change `CoachingStatus.java` to:

```java
package com.loading.acttub_backend.coaching.domain;

public enum CoachingStatus {
	ANALYZING,
	QUESTION_READY,
	COMPLETED,
	FAILED
}
```

Change `CoachingAnalysisResult.java` to:

```java
package com.loading.acttub_backend.coaching.domain;

import java.math.BigDecimal;

public record CoachingAnalysisResult(
		String provider,
		String model,
		BigDecimal temperature,
		String promptVersion,
		CoachingInitialContext initialContext
) {
}
```

- [ ] **Step 5: Add state fields and transition methods**

In `Coaching.java`, add text columns:

```java
@Column(columnDefinition = "text")
private String initialQuestion;
@Column(columnDefinition = "text")
private String initialTranscript;
@Column(columnDefinition = "text")
private String initialAnalysis;
@Convert(converter = MeasurableSignalsConverter.class)
@Column(name = "initial_measurable_signals_json", columnDefinition = "text")
private List<MeasurableSignal> initialMeasurableSignals = new ArrayList<>();
@Column(columnDefinition = "text")
private String initialDiagnosticHypothesis;
@Column(columnDefinition = "text")
private String userAnswer;
@Column(columnDefinition = "text")
private String refinedSituation;
@Column(columnDefinition = "text")
private String refinedIntent;
```

Add transition methods:

```java
public void markQuestionReady(CoachingInitialContext initialContext, CoachingAnalysisResult analysisResult, OffsetDateTime now) {
	this.status = CoachingStatus.QUESTION_READY;
	this.aiProvider = analysisResult.provider();
	this.aiModel = analysisResult.model();
	this.aiTemperature = analysisResult.temperature();
	this.aiPromptVersion = analysisResult.promptVersion();
	this.initialQuestion = initialContext.question();
	this.initialTranscript = initialContext.transcript();
	this.initialAnalysis = initialContext.analysis();
	this.initialMeasurableSignals.clear();
	this.initialMeasurableSignals.addAll(initialContext.measurableSignals());
	this.initialDiagnosticHypothesis = initialContext.diagnosticHypothesis();
	this.updatedAt = now;
}

public void completeDiagnosis(String answer, CoachingDiagnosisResult diagnosisResult, OffsetDateTime now) {
	this.status = CoachingStatus.COMPLETED;
	this.userAnswer = answer;
	this.refinedSituation = diagnosisResult.refinedSituation();
	this.refinedIntent = diagnosisResult.refinedIntent();
	applyFeedback(diagnosisResult.feedback());
	this.completedAt = now;
	this.updatedAt = now;
}
```

Move feedback persistence logic out of `complete` into a private `applyFeedback(CoachFeedback feedback)` method. Remove the existing `complete(CoachingAnalysisResult, OffsetDateTime)` method after `CoachingService`, `CoachingTests`, and `EvaluationServiceTests` are updated to use `markQuestionReady` plus `completeDiagnosis`.

Add getters:

```java
public String getInitialQuestion() {
	return initialQuestion;
}

public CoachingInitialContext getInitialContext() {
	return new CoachingInitialContext(
			initialQuestion,
			initialTranscript,
			initialAnalysis,
			List.copyOf(initialMeasurableSignals),
			initialDiagnosticHypothesis
	);
}

public String getUserAnswer() {
	return userAnswer;
}

public String getRefinedSituation() {
	return refinedSituation;
}

public String getRefinedIntent() {
	return refinedIntent;
}
```

- [ ] **Step 6: Run domain tests and verify GREEN**

Run:

```bash
./gradlew test --tests com.loading.acttub_backend.coaching.domain.CoachingTests
```

Expected: domain tests pass.

- [ ] **Step 7: Commit domain changes**

Run:

```bash
git add src/main/java/com/loading/acttub_backend/coaching/domain src/test/java/com/loading/acttub_backend/coaching/domain/CoachingTests.java
git commit -m "feat(coaching): 챗봇 진단 도메인 상태 추가"
```

## Task 3: Add Database Migration

**Files:**
- Create: `src/main/resources/db/migration/V10__add_coaching_chatbot_diagnosis.sql`
- Create: `src/main/resources/db/migration/postgresql/V10__add_coaching_chatbot_diagnosis.sql`

- [ ] **Step 1: Add H2 migration**

Create `src/main/resources/db/migration/V10__add_coaching_chatbot_diagnosis.sql`:

```sql
alter table coachings add column initial_question text;
alter table coachings add column initial_transcript text;
alter table coachings add column initial_analysis text;
alter table coachings add column initial_measurable_signals_json text;
alter table coachings add column initial_diagnostic_hypothesis text;
alter table coachings add column user_answer text;
alter table coachings add column refined_situation text;
alter table coachings add column refined_intent text;
```

- [ ] **Step 2: Add PostgreSQL migration**

Create `src/main/resources/db/migration/postgresql/V10__add_coaching_chatbot_diagnosis.sql` with the same SQL:

```sql
alter table coachings add column initial_question text;
alter table coachings add column initial_transcript text;
alter table coachings add column initial_analysis text;
alter table coachings add column initial_measurable_signals_json text;
alter table coachings add column initial_diagnostic_hypothesis text;
alter table coachings add column user_answer text;
alter table coachings add column refined_situation text;
alter table coachings add column refined_intent text;
```

- [ ] **Step 3: Run migration-backed integration test and verify current failure narrows**

Run:

```bash
./gradlew test --tests com.loading.acttub_backend.coaching.CoachingApiIntegrationTest
```

Expected: schema errors are gone. Tests still fail because service, DTO, and controller behavior are not implemented.

- [ ] **Step 4: Commit migration**

Run:

```bash
git add src/main/resources/db/migration/V10__add_coaching_chatbot_diagnosis.sql src/main/resources/db/migration/postgresql/V10__add_coaching_chatbot_diagnosis.sql
git commit -m "feat(coaching): 챗봇 진단 저장 컬럼 추가"
```

## Task 4: Update Application Models And Service Flow

**Files:**
- Create: `src/main/java/com/loading/acttub_backend/coaching/application/model/DiagnosisCommand.java`
- Modify: `src/main/java/com/loading/acttub_backend/coaching/application/model/CoachingResult.java`
- Modify: `src/main/java/com/loading/acttub_backend/coaching/application/port/CoachingAnalyzer.java`
- Modify: `src/main/java/com/loading/acttub_backend/coaching/application/service/CoachingService.java`
- Modify: `src/main/java/com/loading/acttub_backend/global/api/ApiExceptionHandler.java`

- [ ] **Step 1: Add command model**

Create:

```java
package com.loading.acttub_backend.coaching.application.model;

public record DiagnosisCommand(String answer) {
	public String normalizedAnswer() {
		if (answer == null || answer.isBlank()) {
			return null;
		}
		return answer.trim();
	}
}
```

- [ ] **Step 2: Extend analyzer port**

Change `CoachingAnalyzer.java` to:

```java
package com.loading.acttub_backend.coaching.application.port;

import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import com.loading.acttub_backend.coaching.domain.CoachingDiagnosisResult;
import com.loading.acttub_backend.coaching.domain.CoachingInitialContext;

public interface CoachingAnalyzer {

	CoachingAnalysisResult analyze(VideoInput video, CoachingInput input);

	CoachingDiagnosisResult diagnose(CoachingInput input, CoachingInitialContext initialContext, String answer);
}
```

- [ ] **Step 3: Extend `CoachingResult`**

Change record fields to include nullable initial/final context:

```java
public record CoachingResult(
		String coachingId,
		String status,
		OffsetDateTime createdAt,
		OffsetDateTime completedAt,
		CoachingInput input,
		CoachingInitialContext initialContext,
		String userAnswer,
		String refinedSituation,
		String refinedIntent,
		CoachFeedback feedback
) {
}
```

- [ ] **Step 4: Update `create` service behavior**

In `CoachingService.create`, replace final completion with question-ready transition:

```java
CoachingAnalysisResult analysisResult = analyze(coaching, video, input);
coaching.markQuestionReady(analysisResult.initialContext(), analysisResult, OffsetDateTime.now(clock));
return toResponse(coaching);
```

- [ ] **Step 5: Add diagnosis service method**

Add:

```java
@Transactional(noRollbackFor = ApplicationException.class)
public CoachingResult diagnose(Long coachingId, DiagnosisCommand command) {
	String answer = validateDiagnosisCommand(command);
	Coaching coaching = coachingRepository.findById(coachingId)
			.orElseThrow(() -> new ApplicationException(
					"COACHING_NOT_FOUND",
					"Coaching not found.",
					Map.of("coachingId", String.valueOf(coachingId))
			));
	if (coaching.getStatus() != CoachingStatus.QUESTION_READY) {
		throw new ApplicationException(
				"COACHING_NOT_READY_FOR_DIAGNOSIS",
				"Coaching is not ready for diagnosis.",
				Map.of("coachingId", String.valueOf(coachingId), "status", coaching.getStatus().name())
		);
	}

	try {
		CoachingDiagnosisResult diagnosisResult = coachingAnalyzer.diagnose(toInput(coaching), coaching.getInitialContext(), answer);
		coaching.completeDiagnosis(answer, diagnosisResult, OffsetDateTime.now(clock));
		return toResponse(coaching);
	} catch (CoachingAnalysisTimeoutException e) {
		coaching.fail("AI_DIAGNOSIS_TIMEOUT", e.getMessage(), OffsetDateTime.now(clock));
		coachingRepository.saveAndFlush(coaching);
		throw new ApplicationException(
				"AI_DIAGNOSIS_TIMEOUT",
				"Coaching diagnosis timed out.",
				Map.of("coachingId", String.valueOf(coaching.getId()), "status", CoachingStatus.FAILED.name())
		);
	} catch (RuntimeException e) {
		coaching.fail("AI_DIAGNOSIS_FAILED", e.getMessage(), OffsetDateTime.now(clock));
		coachingRepository.saveAndFlush(coaching);
		throw new ApplicationException(
				"AI_DIAGNOSIS_FAILED",
				"Coaching diagnosis failed.",
				Map.of("coachingId", String.valueOf(coaching.getId()), "status", CoachingStatus.FAILED.name())
		);
	}
}
```

Add helpers:

```java
private String validateDiagnosisCommand(DiagnosisCommand command) {
	if (command == null || command.normalizedAnswer() == null) {
		throw new ApplicationException(
				"INVALID_DIAGNOSIS_REQUEST",
				"Invalid diagnosis request.",
				Map.of("fields", List.of("answer"))
		);
	}
	return command.normalizedAnswer();
}

private CoachingInput toInput(Coaching coaching) {
	return new CoachingInput(
			coaching.getGenre(),
			coaching.getCustomGenre(),
			coaching.getSituation(),
			coaching.getCharacterSetting(),
			coaching.getSubtext()
	);
}
```

- [ ] **Step 6: Update exception status mapping**

In `ApiExceptionHandler.statusFor`, add:

```java
case "INVALID_COACHING_REQUEST", "INVALID_EVALUATION_REQUEST", "INVALID_DIAGNOSIS_REQUEST" -> HttpStatus.BAD_REQUEST;
case "COACHING_NOT_FOUND" -> HttpStatus.NOT_FOUND;
case "COACHING_NOT_EVALUABLE", "COACHING_EVALUATION_ALREADY_EXISTS", "COACHING_NOT_READY_FOR_DIAGNOSIS" -> HttpStatus.CONFLICT;
case "AI_ANALYSIS_FAILED", "AI_DIAGNOSIS_FAILED" -> HttpStatus.BAD_GATEWAY;
case "AI_ANALYSIS_TIMEOUT", "AI_DIAGNOSIS_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
```

- [ ] **Step 7: Run focused tests**

Run:

```bash
./gradlew test --tests com.loading.acttub_backend.coaching.domain.CoachingTests --tests com.loading.acttub_backend.coaching.CoachingApiIntegrationTest
```

Expected: compilation moves to controller/DTO/analyzer implementation gaps.

## Task 5: Update DTOs And Controller

**Files:**
- Create: `src/main/java/com/loading/acttub_backend/coaching/presentation/dto/DiagnosisRequest.java`
- Modify: `src/main/java/com/loading/acttub_backend/coaching/presentation/dto/CoachingResponse.java`
- Modify: `src/main/java/com/loading/acttub_backend/coaching/presentation/CoachingController.java`

- [ ] **Step 1: Add request DTO**

Create:

```java
package com.loading.acttub_backend.coaching.presentation.dto;

public record DiagnosisRequest(String answer) {
}
```

- [ ] **Step 2: Extend response DTO**

Change `CoachingResponse` to:

```java
public record CoachingResponse(
		String coachingId,
		String status,
		OffsetDateTime createdAt,
		OffsetDateTime completedAt,
		CoachingInput input,
		Dialogue dialogue,
		InitialContext context,
		RefinedContext refinedContext,
		CoachFeedback result
) {
	public record CoachingInput(String genre, String customGenre, String situation, String characterSetting, String subtext) {
	}

	public record Dialogue(String question) {
	}

	public record InitialContext(
			String transcript,
			String analysis,
			List<MeasurableSignalResponse> measurableSignals,
			String diagnosticHypothesis
	) {
	}

	public record MeasurableSignalResponse(String name, BigDecimal value, String unit, String evidence) {
	}

	public record RefinedContext(String answer, String refinedSituation, String refinedIntent) {
	}
}
```

Required imports: `java.math.BigDecimal`, `java.time.OffsetDateTime`, `java.util.List`, `com.loading.acttub_backend.coaching.domain.CoachFeedback`.

- [ ] **Step 3: Add controller endpoint**

In `CoachingController`, add:

```java
@PostMapping(value = "/api/v1/coachings/{coachingId}/diagnosis", consumes = MediaType.APPLICATION_JSON_VALUE)
@Operation(
		summary = "코칭 최종 진단 생성",
		description = "초기 챗봇 질문에 대한 사용자 답변을 받아 최종 진단을 생성합니다."
)
@ApiResponse(responseCode = "201", description = "코칭 최종 진단 생성 완료")
public ResponseEntity<ApiEnvelope<CoachingResponse>> diagnose(
		@Parameter(description = "코칭 ID")
		@PathVariable Long coachingId,
		@RequestBody DiagnosisRequest request
) {
	if (request == null) {
		throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"INVALID_DIAGNOSIS_REQUEST",
				"Invalid diagnosis request.",
				Map.of("fields", List.of("answer"))
		);
	}
	CoachingResult result = coachingService.diagnose(coachingId, new DiagnosisCommand(request.answer()));
	return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.data(toResponse(result)));
}
```

- [ ] **Step 4: Update response mapper**

Map nullable context based on status:

```java
private CoachingResponse toResponse(CoachingResult result) {
	return new CoachingResponse(
			result.coachingId(),
			result.status(),
			result.createdAt(),
			result.completedAt(),
			new CoachingResponse.CoachingInput(
					result.input().genreLabel(),
					result.input().customGenre(),
					result.input().situation(),
					result.input().characterSetting(),
					result.input().subtext()
			),
			toDialogue(result),
			toInitialContext(result),
			toRefinedContext(result),
			result.feedback()
	);
}
```

Add mapper helpers:

```java
private CoachingResponse.Dialogue toDialogue(CoachingResult result) {
	if (result.initialContext() == null) {
		return null;
	}
	return new CoachingResponse.Dialogue(result.initialContext().question());
}

private CoachingResponse.InitialContext toInitialContext(CoachingResult result) {
	if (result.initialContext() == null) {
		return null;
	}
	return new CoachingResponse.InitialContext(
			result.initialContext().transcript(),
			result.initialContext().analysis(),
			result.initialContext().measurableSignals().stream()
					.map(signal -> new CoachingResponse.MeasurableSignalResponse(
							signal.name(),
							signal.value(),
							signal.unit(),
							signal.evidence()
					))
					.toList(),
			result.initialContext().diagnosticHypothesis()
	);
}

private CoachingResponse.RefinedContext toRefinedContext(CoachingResult result) {
	if (result.userAnswer() == null) {
		return null;
	}
	return new CoachingResponse.RefinedContext(result.userAnswer(), result.refinedSituation(), result.refinedIntent());
}
```

- [ ] **Step 5: Run API test**

Run:

```bash
./gradlew test --tests com.loading.acttub_backend.coaching.CoachingApiIntegrationTest
```

Expected: remaining failures are in analyzer behavior or persistence serialization.

## Task 6: Update Stub Analyzer

**Files:**
- Modify: `src/main/java/com/loading/acttub_backend/coaching/infrastructure/ai/DefaultCoachingAnalyzer.java`

- [ ] **Step 1: Make stub analyzer satisfy new contract**

In `DefaultCoachingAnalyzer`, change `analyze` to return:

```java
CoachingInitialContext initialContext = new CoachingInitialContext(
		"이 장면에서 상대에게 가장 들키고 싶지 않았던 감정은 무엇이었나요?",
		"안녕. 오랜만이네. 잘 지냈어?",
		"초반에는 괜찮은 척하는 표면이 유지되지만, 중간 멈춤에서 미련이 새어 나오는 흐름이 보입니다.",
		List.of(
				new MeasurableSignal("pauseCount", BigDecimal.valueOf(3), "count", "0:12, 0:28, 0:41에서 대사 사이 멈춤이 확인됩니다."),
				new MeasurableSignal("longestPauseSeconds", BigDecimal.valueOf(2.4), "seconds", "0:28 이후 약 2.4초 동안 다음 대사로 넘어가지 않습니다.")
		),
		"상대를 놓지 못한 마음은 보이지만, 왜 그것을 숨겨야 하는지가 아직 선명하지 않은 것 같아요."
);
return new CoachingAnalysisResult("gemini", "Gemini 3.0 Flash", BigDecimal.ZERO, "analysis-v0.2", initialContext);
```

Add `diagnose` returning:

```java
@Override
public CoachingDiagnosisResult diagnose(CoachingInput input, CoachingInitialContext initialContext, String answer) {
	CoachFeedback feedback = new CoachFeedback(
			new CoachFeedback.OverallStrength("전반적으로 감정을 바로 터뜨리지 않고 버티는 선택이 장면의 방향과 잘 맞았어요."),
			List.of(new CoachFeedback.FeedbackCard(
					1,
					"상대에게 약하게 보이지 않으려는 이유를 첫 멈춤 전에 더 분명히 잡아보세요",
					List.of(new CoachFeedback.Observation("0:28", "대사 사이 멈춤이 길어지며 다음 말을 고르는 시간이 보였어요.")),
					"왜냐면 미련을 숨기는 선택은 보이지만, 약하게 보이고 싶지 않다는 방어의 이유가 몸과 시선에 아직 충분히 걸리지 않은 것 같아요.",
					List.of(
							"첫 대사 전 상대가 나를 불쌍하게 보는 장면을 한 번 떠올리고 시선을 2초 고정하세요.",
							"0:28 지점의 멈춤에서는 고개를 숙이지 말고 숨만 한 번 삼키세요.",
							"그 상태로 첫 세 문장만 바로 다시 녹화하세요."
					),
					"그럼 감정을 누르는 이유가 선명해져서 장면이 더 구체적으로 잡혀요."
			))
	);
	return new CoachingDiagnosisResult(
			input.situation() + "에서 " + answer,
			"미련을 숨기면서도 상대가 자신을 쉽게 지나치지 못하게 만들고 싶다",
			feedback
	);
}
```

- [ ] **Step 2: Run API integration tests**

Run:

```bash
./gradlew test --tests com.loading.acttub_backend.coaching.CoachingApiIntegrationTest
```

Expected: all API integration tests pass or failures are limited to legacy tests expecting completed coaching.

- [ ] **Step 3: Commit service, DTO, stub, migration behavior**

Run:

```bash
git add src/main/java/com/loading/acttub_backend/coaching src/main/java/com/loading/acttub_backend/global/api/ApiExceptionHandler.java src/test/java/com/loading/acttub_backend/coaching
git commit -m "feat(coaching): 챗봇형 진단 API 구현"
```

## Task 7: Update Gemini Analyzer Contract

**Files:**
- Modify: `src/main/java/com/loading/acttub_backend/coaching/infrastructure/ai/GeminiCoachingAnalyzer.java`
- Modify: `src/test/java/com/loading/acttub_backend/coaching/infrastructure/ai/GeminiCoachingAnalyzerTests.java`

- [ ] **Step 1: Update Gemini success test response for initial analysis**

Change default feedback JSON in `startGeminiServer` to:

```json
{
  "question": "이 장면에서 숨기고 싶은 감정은 무엇이었나요?",
  "transcript": "안녕. 오랜만이네.",
  "analysis": "감정을 참는 선택이 보입니다.",
  "measurableSignals": [
    {
      "name": "pauseCount",
      "value": 2,
      "unit": "count",
      "evidence": "두 번 멈춤"
    }
  ],
  "diagnosticHypothesis": "숨기는 이유가 아직 덜 선명한 것 같아요."
}
```

Update assertions:

```java
assertThat(result.initialContext().question()).isEqualTo("이 장면에서 숨기고 싶은 감정은 무엇이었나요?");
assertThat(result.initialContext().measurableSignals().getFirst().name()).isEqualTo("pauseCount");
```

- [ ] **Step 2: Add missing field test for initial context**

Use JSON without `diagnosticHypothesis` and assert:

```java
assertThatThrownBy(() -> analyzer.analyze(videoInput(videoBytes), coachingInput()))
		.isInstanceOf(IllegalStateException.class)
		.hasMessageContaining("Gemini response missing required field: diagnosticHypothesis");
```

- [ ] **Step 3: Add a final diagnosis parser test**

Add a server response for diagnosis:

```json
{
  "refinedSituation": "약하게 보이고 싶지 않아 미련을 숨기는 상황",
  "refinedIntent": "미련을 숨기며 상대의 반응을 확인한다",
  "feedback": {
    "overallStrength": {
      "text": "전반적으로 버티는 선택이 좋았어요."
    },
    "feedbackCards": [
      {
        "order": 1,
        "title": "첫 멈춤 전에 숨기는 이유를 분명히 잡아보세요",
        "observations": [
          {
            "timecode": "0:28",
            "text": "대사 사이 멈춤이 보였어요."
          }
        ],
        "cause": "왜냐면 방어의 이유가 아직 충분히 걸리지 않은 것 같아요.",
        "practiceSteps": ["첫 대사 전 시선을 2초 고정하세요."],
        "expectedEffect": "그럼 숨기는 이유가 선명해져요."
      }
    ]
  }
}
```

Call:

```java
CoachingDiagnosisResult result = analyzer.diagnose(coachingInput(), initialContext(), "상대에게 약하게 보이고 싶지 않았어요.");
```

Assert refined fields and feedback title.

- [ ] **Step 4: Implement Gemini initial parser**

Replace `parseFeedback(response)` use in `analyze` with `parseInitialContext(response)`.

Parser shape:

```java
private CoachingInitialContext parseInitialContext(String response) throws IOException {
	String text = responseText(response);
	JsonNode root = objectMapper.readTree(stripJsonFence(text));
	return new CoachingInitialContext(
			requiredText(root, "question"),
			root.path("transcript").asText(""),
			requiredText(root, "analysis"),
			requiredMeasurableSignals(root),
			requiredText(root, "diagnosticHypothesis")
	);
}
```

Add `requiredMeasurableSignals(JsonNode root)` that requires a non-empty `measurableSignals` array and maps `name`, numeric `value`, `unit`, `evidence`.

- [ ] **Step 5: Implement Gemini diagnosis generation**

Add `diagnose` method that posts to the same Gemini endpoint without file data. Body contains one text part with a final-diagnosis prompt. Parse with:

```java
private CoachingDiagnosisResult parseDiagnosisResult(String response) throws IOException {
	String text = responseText(response);
	JsonNode root = objectMapper.readTree(stripJsonFence(text));
	return new CoachingDiagnosisResult(
			requiredText(root, "refinedSituation"),
			requiredText(root, "refinedIntent"),
			parseFeedback(root.path("feedback"))
	);
}
```

Refactor existing feedback parser to accept a `JsonNode`.

- [ ] **Step 6: Run Gemini tests**

Run:

```bash
./gradlew test --tests com.loading.acttub_backend.coaching.infrastructure.ai.GeminiCoachingAnalyzerTests
```

Expected: all Gemini analyzer tests pass.

- [ ] **Step 7: Commit Gemini changes**

Run:

```bash
git add src/main/java/com/loading/acttub_backend/coaching/infrastructure/ai/GeminiCoachingAnalyzer.java src/test/java/com/loading/acttub_backend/coaching/infrastructure/ai/GeminiCoachingAnalyzerTests.java
git commit -m "feat(coaching): Gemini 챗봇 진단 계약 반영"
```

## Task 8: Update Evaluation And Remaining Tests

**Files:**
- Modify: `src/test/java/com/loading/acttub_backend/coaching/CoachingApiIntegrationTest.java`
- Modify: `src/test/java/com/loading/acttub_backend/coaching/application/service/EvaluationServiceTests.java`
- Read: `src/main/java/com/loading/acttub_backend/coaching/application/service/EvaluationService.java` to confirm it already keys off `CoachingStatus.COMPLETED`.

- [ ] **Step 1: Ensure evaluation tests complete diagnosis first**

Change `createCoaching()` helper to still create `QUESTION_READY`, and add:

```java
private String createCompletedCoaching() throws Exception {
	String coachingId = createCoaching();
	submitDiagnosis(coachingId, "상대에게 약하게 보이고 싶지 않았어요.")
			.andExpect(status().isCreated());
	return coachingId;
}
```

Update evaluation tests to call `createCompletedCoaching()`.

- [ ] **Step 2: Keep stored video test on initial creation**

`storesUploadedVideoFileAndRecordsStorageLocation` should continue using `createCoaching` or direct create request. It should assert video storage while status is `QUESTION_READY`.

- [ ] **Step 3: Update `EvaluationServiceTests` fixture**

Replace direct calls to `coaching.complete(...)` with:

```java
coaching.markQuestionReady(initialContext(), analysisResult(), now.plusSeconds(1));
coaching.completeDiagnosis("상대에게 약하게 보이고 싶지 않았어요.", diagnosisResult(), now.plusSeconds(2));
```

Add these helper methods to `EvaluationServiceTests`:

```java
private CoachingInitialContext initialContext() {
	return new CoachingInitialContext(
			"이 장면에서 숨기고 싶은 감정은 무엇이었나요?",
			"안녕. 오랜만이네.",
			"감정을 참는 선택이 보입니다.",
			List.of(new MeasurableSignal("pauseCount", BigDecimal.valueOf(2), "count", "두 번 멈춤")),
			"숨기는 이유가 아직 덜 선명한 것 같아요."
	);
}

private CoachingAnalysisResult analysisResult() {
	return new CoachingAnalysisResult("gemini", "test-model", BigDecimal.ZERO, "test-prompt", initialContext());
}

private CoachingDiagnosisResult diagnosisResult() {
	return new CoachingDiagnosisResult(
			"약하게 보이고 싶지 않아 미련을 숨기는 상황",
			"미련을 숨기며 상대의 반응을 확인한다",
			new CoachFeedback(
					new CoachFeedback.OverallStrength("전반적으로 버티는 선택이 좋았어요."),
					List.of(new CoachFeedback.FeedbackCard(
							1,
							"첫 멈춤 전에 숨기는 이유를 분명히 잡아보세요",
							List.of(new CoachFeedback.Observation("0:28", "대사 사이 멈춤이 보였어요.")),
							"왜냐면 방어의 이유가 아직 충분히 걸리지 않은 것 같아요.",
							List.of("첫 대사 전 시선을 2초 고정하세요."),
							"그럼 숨기는 이유가 선명해져요."
					))
			)
	);
}
```

Required imports: `java.math.BigDecimal`, `java.util.List`, `CoachingInitialContext`, `CoachingAnalysisResult`, `CoachingDiagnosisResult`, `MeasurableSignal`, `CoachFeedback`.

- [ ] **Step 4: Run full coaching integration test**

Run:

```bash
./gradlew test --tests com.loading.acttub_backend.coaching.CoachingApiIntegrationTest
```

Expected: all tests in the class pass.

- [ ] **Step 5: Run evaluation service tests**

Run:

```bash
./gradlew test --tests com.loading.acttub_backend.coaching.application.service.EvaluationServiceTests
```

Expected: evaluation service tests pass with completed two-step coachings.

- [ ] **Step 6: Commit test adjustments**

Run:

```bash
git add src/test/java/com/loading/acttub_backend/coaching/CoachingApiIntegrationTest.java src/test/java/com/loading/acttub_backend/coaching/application/service/EvaluationServiceTests.java src/main/java/com/loading/acttub_backend/coaching/application/service/EvaluationService.java
git commit -m "test(coaching): 챗봇 진단 흐름에 평가 테스트 맞춤"
```

## Task 9: Update API Documentation

**Files:**
- Modify: `docs/specs/current-api-contract.html`
- Keep: `docs/superpowers/specs/2026-06-28-coaching-chatbot-diagnosis-design.html`

- [ ] **Step 1: Update `/api/v1/coachings` response section**

Change status to `QUESTION_READY`, remove immediate `result`, and add `dialogue` and `context` fields matching the design doc.

- [ ] **Step 2: Add `/api/v1/coachings/{coachingId}/diagnosis` section**

Document request body:

```json
{
  "answer": "상대가 나를 약하게 볼까 봐 미련을 숨기고 싶었어요."
}
```

Document response with `refinedContext` and `result`.

- [ ] **Step 3: Run documentation sanity checks**

Run:

```bash
rg -n "QUESTION_READY|/api/v1/coachings/.*/diagnosis|dialogue|refinedContext" docs/specs/current-api-contract.html
```

Expected: all new contract terms appear.

- [ ] **Step 4: Commit docs**

Run:

```bash
git add docs/specs/current-api-contract.html
git commit -m "docs(coaching): 챗봇 진단 API 명세 갱신"
```

## Task 10: Full Verification

**Files:**
- No new file edits unless verification reveals a defect.

- [ ] **Step 1: Run full test suite**

Run:

```bash
./gradlew test
```

Expected: build succeeds and all tests pass.

- [ ] **Step 2: Inspect git status**

Run:

```bash
git status --short --branch
```

Expected: branch is `feature/SOMA-152-chatbot-acting-diagnosis`; no unstaged or uncommitted files remain unless intentionally left for review.

- [ ] **Step 3: Review commit history**

Run:

```bash
git log --oneline --decorate -8
```

Expected: commits are small and Korean commit messages follow repository workflow.

## Self-Review

- Spec coverage: This plan covers `QUESTION_READY`, one chatbot question, AI transcript, initial analysis, measurable signals, diagnostic hypothesis, answer-based final diagnosis, no video re-upload in diagnosis, DB persistence, error handling, tests, and API docs.
- Placeholder scan: The plan avoids deferred implementation markers and includes concrete file paths, commands, snippets, expected outcomes, and commit messages.
- Type consistency: The plan consistently uses `CoachingInitialContext`, `MeasurableSignal`, `CoachingDiagnosisResult`, `DiagnosisCommand`, `DiagnosisRequest`, `dialogue`, `context`, `refinedContext`, and `QUESTION_READY`.
