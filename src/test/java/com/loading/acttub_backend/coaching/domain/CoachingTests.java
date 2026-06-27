package com.loading.acttub_backend.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class CoachingTests {

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
				1024L,
				OffsetDateTime.parse("2026-06-13T00:00:00+09:00")
		);
		MeasurableSignal signal = new MeasurableSignal("pauseCount", BigDecimal.valueOf(2), "count", "두 번 멈춤");
		CoachingInitialContext initialContext = new CoachingInitialContext(
				"이 장면에서 숨기고 싶은 감정은 무엇이었나요?",
				"안녕. 오랜만이네.",
				"감정을 참는 선택이 보입니다.",
				List.of(signal),
				"숨기는 이유가 아직 덜 선명한 것 같아요."
		);

		coaching.markQuestionReady(initialContext, new CoachingAnalysisResult(
				"gemini",
				"Gemini 3.0 Flash",
				BigDecimal.ZERO,
				"analysis-v0.1",
				initialContext
		), OffsetDateTime.parse("2026-06-13T00:01:00+09:00"));

		assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.QUESTION_READY);
		assertThat(coaching.getInitialQuestion()).isEqualTo("이 장면에서 숨기고 싶은 감정은 무엇이었나요?");
		assertThat(coaching.getInitialContext()).isEqualTo(initialContext);
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
				1024L,
				OffsetDateTime.parse("2026-06-13T00:00:00+09:00")
		);
		MeasurableSignal signal = new MeasurableSignal("pauseCount", BigDecimal.valueOf(2), "count", "두 번 멈춤");
		CoachingInitialContext initialContext = new CoachingInitialContext(
				"이 장면에서 숨기고 싶은 감정은 무엇이었나요?",
				"안녕. 오랜만이네.",
				"감정을 참는 선택이 보입니다.",
				List.of(signal),
				"숨기는 이유가 아직 덜 선명한 것 같아요."
		);
		coaching.markQuestionReady(initialContext, new CoachingAnalysisResult(
				"gemini",
				"Gemini 3.0 Flash",
				BigDecimal.ZERO,
				"analysis-v0.1",
				initialContext
		), OffsetDateTime.parse("2026-06-13T00:01:00+09:00"));

		coaching.completeDiagnosis(
				"상대가 나를 약하게 볼까 봐 숨겼어요.",
				new CoachingDiagnosisResult(
						"약하게 보이고 싶지 않아 미련을 숨기는 상황",
						"미련을 숨기며 상대의 반응을 확인한다",
						new CoachFeedback(
								new CoachFeedback.OverallStrength("전반적으로 버티는 선택이 좋았어요."),
								List.of(new CoachFeedback.FeedbackCard(
										1,
										"장면 의도",
										List.of(new CoachFeedback.Observation("0:00-0:15", "관찰")),
										"원인",
										List.of("처방1", "처방2"),
										"기대 효과"
								))
						)
				),
				OffsetDateTime.parse("2026-06-13T00:02:00+09:00")
		);

		CoachFeedback feedback = coaching.getFeedback();

		assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.COMPLETED);
		assertThat(coaching.getUserAnswer()).isEqualTo("상대가 나를 약하게 볼까 봐 숨겼어요.");
		assertThat(coaching.getRefinedSituation()).isEqualTo("약하게 보이고 싶지 않아 미련을 숨기는 상황");
		assertThat(coaching.getRefinedIntent()).isEqualTo("미련을 숨기며 상대의 반응을 확인한다");
		assertThat(feedback.overallStrength().text()).isEqualTo("전반적으로 버티는 선택이 좋았어요.");
		assertThat(feedback.feedbackCards()).hasSize(1);
		assertThat(feedback.feedbackCards().getFirst().practiceSteps()).containsExactly("처방1", "처방2");
		assertThat(coaching.getCompletedAt()).isEqualTo(OffsetDateTime.parse("2026-06-13T00:02:00+09:00"));
	}

	@Test
	void markQuestionReadyNormalizesNullMeasurableSignals() {
		Coaching coaching = Coaching.analyzing(
				"영화",
				null,
				"헤어진 연인을 우연히 다시 만난 상황",
				"감정을 쉽게 드러내지 않는 배우 지망생",
				"아직 미련이 있지만 괜찮은 척한다",
				"scene.mp4",
				"video/mp4",
				1024L,
				OffsetDateTime.parse("2026-06-13T00:00:00+09:00")
		);
		CoachingInitialContext initialContext = new CoachingInitialContext(
				"이 장면에서 숨기고 싶은 감정은 무엇이었나요?",
				"안녕. 오랜만이네.",
				"감정을 참는 선택이 보입니다.",
				null,
				"숨기는 이유가 아직 덜 선명한 것 같아요."
		);

		coaching.markQuestionReady(initialContext, new CoachingAnalysisResult(
				"gemini",
				"Gemini 3.0 Flash",
				BigDecimal.ZERO,
				"analysis-v0.1",
				initialContext
		), OffsetDateTime.parse("2026-06-13T00:01:00+09:00"));

		assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.QUESTION_READY);
		assertThat(coaching.getInitialContext().measurableSignals()).isEmpty();
	}

	@Test
	void markQuestionReadyFiltersNullMeasurableSignals() {
		Coaching coaching = Coaching.analyzing(
				"영화",
				null,
				"헤어진 연인을 우연히 다시 만난 상황",
				"감정을 쉽게 드러내지 않는 배우 지망생",
				"아직 미련이 있지만 괜찮은 척한다",
				"scene.mp4",
				"video/mp4",
				1024L,
				OffsetDateTime.parse("2026-06-13T00:00:00+09:00")
		);
		MeasurableSignal signal = new MeasurableSignal("pauseCount", BigDecimal.valueOf(2), "count", "두 번 멈춤");
		CoachingInitialContext initialContext = new CoachingInitialContext(
				"이 장면에서 숨기고 싶은 감정은 무엇이었나요?",
				"안녕. 오랜만이네.",
				"감정을 참는 선택이 보입니다.",
				Arrays.asList(null, signal),
				"숨기는 이유가 아직 덜 선명한 것 같아요."
		);

		coaching.markQuestionReady(initialContext, new CoachingAnalysisResult(
				"gemini",
				"Gemini 3.0 Flash",
				BigDecimal.ZERO,
				"analysis-v0.1",
				initialContext
		), OffsetDateTime.parse("2026-06-13T00:01:00+09:00"));

		assertThat(coaching.getInitialContext().measurableSignals()).containsExactly(signal);
	}

	@Test
	void invalidDiagnosisFeedbackLeavesQuestionReady() {
		Coaching coaching = questionReadyCoaching();

		assertThatThrownBy(() -> coaching.completeDiagnosis(
				"상대가 나를 약하게 볼까 봐 숨겼어요.",
				new CoachingDiagnosisResult(
						"약하게 보이고 싶지 않아 미련을 숨기는 상황",
						"미련을 숨기며 상대의 반응을 확인한다",
						null
				),
				OffsetDateTime.parse("2026-06-13T00:02:00+09:00")
		)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Feedback is required.");

		assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.QUESTION_READY);
		assertThat(coaching.getCompletedAt()).isNull();
	}

	@Test
	void compatibilityCompleteRequiresFeedbackBeforeMutation() {
		Coaching coaching = Coaching.analyzing(
				"영화",
				null,
				"헤어진 연인을 우연히 다시 만난 상황",
				"감정을 쉽게 드러내지 않는 배우 지망생",
				"아직 미련이 있지만 괜찮은 척한다",
				"scene.mp4",
				"video/mp4",
				1024L,
				OffsetDateTime.parse("2026-06-13T00:00:00+09:00")
		);
		CoachingInitialContext initialContext = new CoachingInitialContext(
				"이 장면에서 숨기고 싶은 감정은 무엇이었나요?",
				"안녕. 오랜만이네.",
				"감정을 참는 선택이 보입니다.",
				List.of(new MeasurableSignal("pauseCount", BigDecimal.valueOf(2), "count", "두 번 멈춤")),
				"숨기는 이유가 아직 덜 선명한 것 같아요."
		);

		assertThatThrownBy(() -> coaching.complete(new CoachingAnalysisResult(
				"gemini",
				"Gemini 3.0 Flash",
				BigDecimal.ZERO,
				"analysis-v0.1",
				initialContext
		), OffsetDateTime.parse("2026-06-13T00:01:00+09:00")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Feedback is required.");

		assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.ANALYZING);
		assertThat(coaching.getCompletedAt()).isNull();
	}

	@Test
	void measurableSignalsConverterNormalizesJsonNull() {
		MeasurableSignalsConverter converter = new MeasurableSignalsConverter();

		assertThat(converter.convertToEntityAttribute("null")).isEmpty();
	}

	@Test
	void measurableSignalsConverterFiltersNullElements() {
		MeasurableSignalsConverter converter = new MeasurableSignalsConverter();

		assertThat(converter.convertToEntityAttribute("[null]")).isEmpty();
	}

	@Test
	void measurableSignalsConverterPreservesRoundTripSignal() {
		MeasurableSignalsConverter converter = new MeasurableSignalsConverter();
		MeasurableSignal signal = new MeasurableSignal("pauseCount", BigDecimal.valueOf(2), "count", "두 번 멈춤");

		String dbValue = converter.convertToDatabaseColumn(List.of(signal));

		assertThat(converter.convertToEntityAttribute(dbValue)).containsExactly(signal);
	}

	private Coaching questionReadyCoaching() {
		Coaching coaching = Coaching.analyzing(
				"영화",
				null,
				"헤어진 연인을 우연히 다시 만난 상황",
				"감정을 쉽게 드러내지 않는 배우 지망생",
				"아직 미련이 있지만 괜찮은 척한다",
				"scene.mp4",
				"video/mp4",
				1024L,
				OffsetDateTime.parse("2026-06-13T00:00:00+09:00")
		);
		MeasurableSignal signal = new MeasurableSignal("pauseCount", BigDecimal.valueOf(2), "count", "두 번 멈춤");
		CoachingInitialContext initialContext = new CoachingInitialContext(
				"이 장면에서 숨기고 싶은 감정은 무엇이었나요?",
				"안녕. 오랜만이네.",
				"감정을 참는 선택이 보입니다.",
				List.of(signal),
				"숨기는 이유가 아직 덜 선명한 것 같아요."
		);
		coaching.markQuestionReady(initialContext, new CoachingAnalysisResult(
				"gemini",
				"Gemini 3.0 Flash",
				BigDecimal.ZERO,
				"analysis-v0.1",
				initialContext
		), OffsetDateTime.parse("2026-06-13T00:01:00+09:00"));
		return coaching;
	}
}
