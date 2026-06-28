package com.loading.acttub_backend.coaching.application.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.loading.acttub_backend.coaching.application.model.ApplicationException;
import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.model.CoachingResult;
import com.loading.acttub_backend.coaching.application.model.ConversationState;
import com.loading.acttub_backend.coaching.application.model.MessageCommand;
import com.loading.acttub_backend.coaching.application.model.StoredVideo;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalysisTimeoutException;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import com.loading.acttub_backend.coaching.application.port.CoachingConversationMessageRepository;
import com.loading.acttub_backend.coaching.application.port.CoachingRepository;
import com.loading.acttub_backend.coaching.application.port.VideoStorage;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.Coaching;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import com.loading.acttub_backend.coaching.domain.CoachingConversationMessage;
import com.loading.acttub_backend.coaching.domain.CoachingConversationResult;
import com.loading.acttub_backend.coaching.domain.CoachingDiagnosisResult;
import com.loading.acttub_backend.coaching.domain.CoachingInitialContext;
import com.loading.acttub_backend.coaching.domain.CoachingStatus;
import com.loading.acttub_backend.coaching.domain.ConversationRole;
import com.loading.acttub_backend.coaching.domain.MeasurableSignal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachingService {

	private static final long MAX_VIDEO_SIZE_BYTES = 104857600L;
	private static final Set<String> ALLOWED_VIDEO_CONTENT_TYPES = Set.of(
			"video/mp4",
			"video/quicktime",
			"video/webm"
	);

	private final CoachingRepository coachingRepository;
	private final CoachingConversationMessageRepository messageRepository;
	private final CoachingAnalyzer coachingAnalyzer;
	private final VideoStorage videoStorage;
	private final Clock clock;
	private final int minConversationTurns;
	private final int maxConversationTurns;

	public CoachingService(CoachingRepository coachingRepository, CoachingConversationMessageRepository messageRepository,
			CoachingAnalyzer coachingAnalyzer, VideoStorage videoStorage,
			@Value("${app.coaching.conversation.min-turns:3}") int minConversationTurns,
			@Value("${app.coaching.conversation.max-turns:7}") int maxConversationTurns) {
		this.coachingRepository = coachingRepository;
		this.messageRepository = messageRepository;
		this.coachingAnalyzer = coachingAnalyzer;
		this.videoStorage = videoStorage;
		this.clock = Clock.systemDefaultZone();
		this.minConversationTurns = minConversationTurns;
		this.maxConversationTurns = maxConversationTurns;
	}

	@Transactional(noRollbackFor = ApplicationException.class)
	public CoachingResult create(VideoInput video, CoachingInput input) {
		validate(video, input);

		Coaching coaching = createAnalyzingCoaching(video, input);
		storeVideo(coaching, video);
		CoachingAnalysisResult analysisResult = analyze(coaching, video, input);

		CoachingInitialContext initialContext = initialContext(input, analysisResult);
		coaching.markQuestionReady(initialContext, analysisResult, OffsetDateTime.now(clock));
		coachingRepository.saveAndFlush(coaching);
		messageRepository.saveAndFlush(new CoachingConversationMessage(
				coaching.getId(),
				ConversationRole.ASSISTANT,
				1,
				initialContext.question(),
				OffsetDateTime.now(clock)
		));
		return toResponse(coaching, null, new ConversationState(0, minConversationTurns, maxConversationTurns, false, null, null),
				initialContext.question());
	}

	@Transactional(noRollbackFor = ApplicationException.class)
	public CoachingResult message(Long coachingId, MessageCommand command) {
		String answer = validateMessageCommand(command);
		Coaching coaching = findCoaching(coachingId);
		if (coaching.getStatus() != CoachingStatus.QUESTION_READY && coaching.getStatus() != CoachingStatus.IN_CONVERSATION) {
			throw new ApplicationException(
					"COACHING_NOT_READY_FOR_MESSAGE",
					"Coaching is not ready for message.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", coaching.getStatus().name())
			);
		}

		List<CoachingConversationMessage> messages = messageRepository.findByCoachingIdOrderByTurnIndexAscRoleAsc(coaching.getId());
		int turnIndex = countUserMessages(messages) + 1;
		messageRepository.saveAndFlush(new CoachingConversationMessage(
				coaching.getId(),
				ConversationRole.USER,
				turnIndex,
				answer,
				OffsetDateTime.now(clock)
		));
		messages = messageRepository.findByCoachingIdOrderByTurnIndexAscRoleAsc(coaching.getId());

		CoachingConversationResult aiResult = continueConversation(coaching, messages, turnIndex);
		CoachingConversationResult policyResult = applyConversationPolicy(aiResult, turnIndex);

		if (policyResult.diagnosisReady()) {
			coaching.markReadyForDiagnosis(policyResult, OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
			return toResponse(coaching, null, new ConversationState(
					turnIndex,
					minConversationTurns,
					maxConversationTurns,
					true,
					policyResult.reason(),
					policyResult.summary()
			), null);
		}

		messageRepository.saveAndFlush(new CoachingConversationMessage(
				coaching.getId(),
				ConversationRole.ASSISTANT,
				turnIndex + 1,
				policyResult.nextQuestion(),
				OffsetDateTime.now(clock)
		));
		coaching.continueConversation(OffsetDateTime.now(clock));
		coachingRepository.saveAndFlush(coaching);
		return toResponse(coaching, null, new ConversationState(
				turnIndex,
				minConversationTurns,
				maxConversationTurns,
				false,
				policyResult.reason(),
				null
		), policyResult.nextQuestion());
	}

	@Transactional(noRollbackFor = ApplicationException.class)
	public CoachingResult diagnose(Long coachingId) {
		Coaching coaching = findCoaching(coachingId);
		if (coaching.getStatus() != CoachingStatus.READY_FOR_DIAGNOSIS) {
			throw new ApplicationException(
					"COACHING_NOT_READY_FOR_DIAGNOSIS",
					"Coaching is not ready for diagnosis.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", coaching.getStatus().name())
			);
		}
		List<CoachingConversationMessage> messages = messageRepository.findByCoachingIdOrderByTurnIndexAscRoleAsc(coaching.getId());
		CoachingDiagnosisResult diagnosisResult = diagnose(coaching, messages);
		coaching.completeDiagnosis(diagnosisResult, OffsetDateTime.now(clock));
		coachingRepository.saveAndFlush(coaching);
		int turnIndex = countUserMessages(messages);
		return toResponse(coaching, coaching.getFeedback(), new ConversationState(
				turnIndex,
				minConversationTurns,
				maxConversationTurns,
				true,
				null,
				coaching.getConversationSummary()
		), null);
	}

	private Coaching createAnalyzingCoaching(VideoInput video, CoachingInput input) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		Coaching coaching = Coaching.analyzing(
				input.genreLabel(),
				input.customGenre(),
				input.situation(),
				input.characterSetting(),
				input.subtext(),
				video.originalFilename(),
				video.contentType(),
				video.sizeBytes(),
				now
		);
		return coachingRepository.saveAndFlush(coaching);
	}

	private void storeVideo(Coaching coaching, VideoInput video) {
		try {
			StoredVideo storedVideo = videoStorage.store(coaching.getId(), video);
			coaching.attachVideo(storedVideo.storageKey(), storedVideo.storageUri(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
		} catch (RuntimeException e) {
			coaching.fail("VIDEO_STORAGE_FAILED", e.getMessage(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
			throw new ApplicationException(
					"VIDEO_STORAGE_FAILED",
					"Video storage failed.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", CoachingStatus.FAILED.name())
			);
		}
	}

	private CoachingAnalysisResult analyze(Coaching coaching, VideoInput video, CoachingInput input) {
		try {
			return coachingAnalyzer.analyze(video, input);
		} catch (CoachingAnalysisTimeoutException e) {
			coaching.fail("AI_ANALYSIS_TIMEOUT", e.getMessage(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
			throw new ApplicationException(
					"AI_ANALYSIS_TIMEOUT",
					"Coaching analysis timed out.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", CoachingStatus.FAILED.name())
			);
		} catch (RuntimeException e) {
			coaching.fail("AI_ANALYSIS_FAILED", e.getMessage(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
			throw new ApplicationException(
					"AI_ANALYSIS_FAILED",
					"Coaching analysis failed.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", CoachingStatus.FAILED.name())
			);
		}
	}

	private CoachingInitialContext initialContext(CoachingInput input, CoachingAnalysisResult analysisResult) {
		if (analysisResult.initialContext() != null) {
			return analysisResult.initialContext();
		}
		CoachFeedback feedback = analysisResult.feedback();
		return new CoachingInitialContext(
				"이 장면에서 인물이 가장 숨기고 싶은 감정은 무엇인가요?",
				"대본을 추출하지 못했습니다.",
				feedback == null ? "초기 분석을 생성했습니다." : feedback.overallStrength().text(),
				List.of(new MeasurableSignal("conversation_turn_target", java.math.BigDecimal.valueOf(minConversationTurns), "턴", "최소 대화 턴 정책")),
				"연기 의도를 구체화하기 위한 대화가 필요합니다."
		);
	}

	private CoachingConversationResult continueConversation(Coaching coaching, List<CoachingConversationMessage> messages,
			int turnIndex) {
		try {
			return coachingAnalyzer.continueConversation(
					toInput(coaching),
					coaching.getInitialContext(),
					messages,
					turnIndex,
					minConversationTurns,
					maxConversationTurns
			);
		} catch (CoachingAnalysisTimeoutException e) {
			coaching.fail("AI_CONVERSATION_TIMEOUT", e.getMessage(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
			throw new ApplicationException(
					"AI_CONVERSATION_TIMEOUT",
					"Coaching conversation timed out.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", CoachingStatus.FAILED.name())
			);
		} catch (RuntimeException e) {
			coaching.fail("AI_CONVERSATION_FAILED", e.getMessage(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
			throw new ApplicationException(
					"AI_CONVERSATION_FAILED",
					"Coaching conversation failed.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", CoachingStatus.FAILED.name())
			);
		}
	}

	private CoachingDiagnosisResult diagnose(Coaching coaching, List<CoachingConversationMessage> messages) {
		try {
			return coachingAnalyzer.diagnose(
					toInput(coaching),
					coaching.getInitialContext(),
					messages,
					coaching.getConversationSummary(),
					coaching.getRefinedSituation(),
					coaching.getRefinedIntent()
			);
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

	private Coaching findCoaching(Long coachingId) {
		return coachingRepository.findById(coachingId)
				.orElseThrow(() -> new ApplicationException(
						"COACHING_NOT_FOUND",
						"Coaching not found.",
						Map.of("coachingId", String.valueOf(coachingId))
				));
	}

	private String validateMessageCommand(MessageCommand command) {
		if (command == null || command.normalizedAnswer() == null) {
			throw new ApplicationException(
					"INVALID_MESSAGE_REQUEST",
					"Invalid message request.",
					Map.of("fields", List.of("answer"))
			);
		}
		return command.normalizedAnswer();
	}

	private CoachingConversationResult applyConversationPolicy(CoachingConversationResult result, int turnIndex) {
		if (turnIndex >= maxConversationTurns) {
			return new CoachingConversationResult(
					true,
					null,
					"최대 대화 턴에 도달해 진단을 준비합니다.",
					defaultSummary(result),
					defaultRefinedSituation(result),
					defaultRefinedIntent(result)
			);
		}
		if (turnIndex < minConversationTurns) {
			return new CoachingConversationResult(
					false,
					result.nextQuestion() == null || result.nextQuestion().isBlank()
							? "이 선택을 하게 된 인물의 이유를 한 문장으로 말한다면 무엇인가요?"
							: result.nextQuestion(),
					result.reason(),
					null,
					null,
					null
			);
		}
		return result;
	}

	private String defaultSummary(CoachingConversationResult result) {
		if (result.summary() != null && !result.summary().isBlank()) {
			return result.summary();
		}
		return "누적 대화를 바탕으로 연기 의도와 상황을 구체화했습니다.";
	}

	private String defaultRefinedSituation(CoachingConversationResult result) {
		if (result.refinedSituation() != null && !result.refinedSituation().isBlank()) {
			return result.refinedSituation();
		}
		return "사용자 답변을 반영해 구체화한 상황";
	}

	private String defaultRefinedIntent(CoachingConversationResult result) {
		if (result.refinedIntent() != null && !result.refinedIntent().isBlank()) {
			return result.refinedIntent();
		}
		return "사용자 답변을 반영해 구체화한 연기 의도";
	}

	private int countUserMessages(List<CoachingConversationMessage> messages) {
		return (int) messages.stream()
				.filter(message -> message.getRole() == ConversationRole.USER)
				.count();
	}

	private CoachingResult toResponse(Coaching coaching) {
		return toResponse(coaching, coaching.getStatus() == CoachingStatus.COMPLETED ? coaching.getFeedback() : null,
				null, null);
	}

	private CoachingResult toResponse(Coaching coaching, CoachFeedback feedback, ConversationState conversation,
			String nextQuestion) {
		return new CoachingResult(
				String.valueOf(coaching.getId()),
				coaching.getStatus().name(),
				coaching.getCreatedAt(),
				coaching.getCompletedAt(),
				new CoachingInput(
						coaching.getGenre(),
						coaching.getCustomGenre(),
						coaching.getSituation(),
						coaching.getCharacterSetting(),
						coaching.getSubtext()
				),
				coaching.getInitialContext(),
				conversation,
				nextQuestion,
				coaching.getRefinedSituation(),
				coaching.getRefinedIntent(),
				feedback
		);
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

	private void validate(VideoInput video, CoachingInput input) {
		if (video == null || video.sizeBytes() == 0 || !ALLOWED_VIDEO_CONTENT_TYPES.contains(video.contentType())) {
			throw new ApplicationException(
					"INVALID_COACHING_REQUEST",
					"Invalid coaching request.",
					Map.of("fields", List.of("video"))
			);
		}
		List<String> invalidFields = invalidInputFields(input);
		if (!invalidFields.isEmpty()) {
			throw new ApplicationException(
					"INVALID_COACHING_REQUEST",
					"Invalid coaching request.",
					Map.of("fields", invalidFields)
			);
		}
		if (video.sizeBytes() > MAX_VIDEO_SIZE_BYTES) {
			throw new ApplicationException(
					"PAYLOAD_TOO_LARGE",
					"Uploaded video is too large.",
					Map.of("maxSizeBytes", MAX_VIDEO_SIZE_BYTES)
			);
		}
	}

	private List<String> invalidInputFields(CoachingInput input) {
		if (input == null) {
			return List.of("input");
		}

		List<String> invalidFields = new ArrayList<>();
		if (!input.hasAllowedGenre()) {
			invalidFields.add("genre");
		}
		if (input.requiresCustomGenre() && input.customGenre() == null) {
			invalidFields.add("customGenre");
		}
		if (input.situation() == null) {
			invalidFields.add("situation");
		}
		if (input.characterSetting() == null) {
			invalidFields.add("characterSetting");
		}
		return invalidFields;
	}
}
