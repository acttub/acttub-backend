package com.loading.acttub_backend.coaching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.loading.acttub_backend.coaching.application.port.CoachingRepository;
import com.loading.acttub_backend.coaching.domain.Coaching;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoachingApiIntegrationTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final Path TEST_VIDEO_ROOT = createTempDirectory();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CoachingRepository coachingRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("app.storage.video.root", TEST_VIDEO_ROOT::toString);
	}

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
				.andExpect(jsonPath("$.data.dialogue.question", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.context.transcript", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.context.analysis", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.context.measurableSignals[0].name", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.context.measurableSignals[0].value", notNullValue()))
				.andExpect(jsonPath("$.data.context.measurableSignals[0].unit", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.context.measurableSignals[0].evidence", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.context.diagnosticHypothesis", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.result").doesNotExist());
	}

	@Test
	void createsCoachingWithCustomGenre() throws Exception {
		mockMvc.perform(coachingRequest(video("video/mp4"), "기타", "웹드라마"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.input.genre").value("기타"))
				.andExpect(jsonPath("$.data.input.customGenre").value("웹드라마"));
	}

	@Test
	void createsCoachingWithoutSubtext() throws Exception {
		mockMvc.perform(coachingRequestWithoutSubtext(video("video/mp4")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.input.subtext").doesNotExist());
	}

	@Test
	void storesUploadedVideoFileAndRecordsStorageLocation() throws Exception {
		MockMultipartFile video = new MockMultipartFile(
				"video",
				"scene.mp4",
				"video/mp4",
				"stored-video".getBytes(StandardCharsets.UTF_8)
		);

		MvcResult result = mockMvc.perform(coachingRequest(video))
				.andExpect(status().isCreated())
				.andReturn();

		String coachingId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.coachingId");
		Coaching coaching = coachingRepository.findById(Long.valueOf(coachingId)).orElseThrow();

		assertThat(coaching.getVideoStorageKey()).isEqualTo("coachings/" + coachingId + "/scene.mp4");
		assertThat(coaching.getVideoStorageUri()).startsWith("file:");
		assertThat(Files.readString(TEST_VIDEO_ROOT.resolve(coaching.getVideoStorageKey()))).isEqualTo("stored-video");
	}

	@Test
	void doesNotKeepLegacyFocusAxesTable() {
		Integer tableCount = jdbcTemplate.queryForObject(
				"select count(*) from information_schema.tables where table_name = 'COACHING_FOCUS_AXES'",
				Integer.class
		);

		assertThat(tableCount).isZero();
	}

	@Test
	void storesFeedbackCardsAsNormalizedRows() throws Exception {
		String coachingId = createCompletedCoaching();

		List<String> cardTitles = jdbcTemplate.queryForList(
				"select title from coaching_feedback_cards where coaching_id = ? order by card_order",
				String.class,
				Long.valueOf(coachingId)
		);
		List<String> observations = jdbcTemplate.queryForList(
				"""
						select o.text
						from coaching_feedback_observations o
						join coaching_feedback_cards c on c.id = o.feedback_card_id
						where c.coaching_id = ?
						order by c.card_order, o.observation_order
						""",
				String.class,
				Long.valueOf(coachingId)
		);
		List<String> practiceSteps = jdbcTemplate.queryForList(
				"""
						select s.text
						from coaching_practice_steps s
						join coaching_feedback_cards c on c.id = s.feedback_card_id
						where c.coaching_id = ?
						order by c.card_order, s.step_order
						""",
				String.class,
				Long.valueOf(coachingId)
		);

		assertThat(cardTitles).hasSize(1);
		assertThat(observations).hasSize(2);
		assertThat(practiceSteps).hasSize(3);
	}

	@Test
	void rejectsCoachingRequestWithoutVideo() throws Exception {
		mockMvc.perform(coachingRequest())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_COACHING_REQUEST"))
				.andExpect(jsonPath("$.error.details.fields[0]").value("video"));
	}

	@Test
	void rejectsUnsupportedVideoContentType() throws Exception {
		MockMultipartFile video = video("text/plain");

		mockMvc.perform(coachingRequest(video))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_COACHING_REQUEST"))
				.andExpect(jsonPath("$.error.details.fields[0]").value("video"));
	}

	@Test
	void rejectsOversizedVideo() throws Exception {
		MockMultipartFile video = new MockMultipartFile(
				"video",
				"scene.mp4",
				"video/mp4",
				"fake-video".getBytes(StandardCharsets.UTF_8)
		) {
			@Override
			public long getSize() {
				return 104857601L;
			}
		};

		mockMvc.perform(coachingRequest(video))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"))
				.andExpect(jsonPath("$.error.details.maxSizeBytes").value(104857600));
	}

	@Test
	void rejectsUnsupportedGenre() throws Exception {
		mockMvc.perform(coachingRequest(video("video/mp4"), "광고", null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_COACHING_REQUEST"))
				.andExpect(jsonPath("$.error.details.fields[0]").value("genre"));
	}

	@Test
	void rejectsCustomGenreMissingWhenGenreIsEtc() throws Exception {
		mockMvc.perform(coachingRequest(video("video/mp4"), "기타", null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_COACHING_REQUEST"))
				.andExpect(jsonPath("$.error.details.fields[0]").value("customGenre"));
	}

	@Test
	void completesDiagnosisWithUserAnswer() throws Exception {
		String coachingId = createCoaching();

		mockMvc.perform(submitDiagnosis(coachingId, "상대가 나를 약하게 볼까 봐 미련을 숨기고 싶었어요."))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.coachingId").value(coachingId))
				.andExpect(jsonPath("$.data.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.completedAt", notNullValue()))
				.andExpect(jsonPath("$.data.refinedContext.answer").value("상대가 나를 약하게 볼까 봐 미련을 숨기고 싶었어요."))
				.andExpect(jsonPath("$.data.refinedContext.refinedSituation", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.refinedContext.refinedIntent", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.result.overallStrength.text", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.result.feedbackCards[0].title", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.result.feedbackCards[0].observations[0].timecode", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.result.feedbackCards[0].cause", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.result.feedbackCards[0].practiceSteps[0]", not(emptyOrNullString())))
				.andExpect(jsonPath("$.data.result.feedbackCards[0].expectedEffect", not(emptyOrNullString())));
	}

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
		mockMvc.perform(submitDiagnosis(coachingId, "상대에게 약하게 보이고 싶지 않았어요."))
				.andExpect(status().isCreated());

		mockMvc.perform(submitDiagnosis(coachingId, "다시 답변합니다."))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("COACHING_NOT_READY_FOR_DIAGNOSIS"))
				.andExpect(jsonPath("$.error.details.status").value("COMPLETED"));
	}

	@Test
	void evaluatesCompletedCoachingOnce() throws Exception {
		String coachingId = createCompletedCoaching();

		mockMvc.perform(post("/api/v1/coachings/{coachingId}/evaluation", coachingId)
						.contentType("application/json")
						.content("""
								{
								  "rating": 4,
								  "comment": "감정 흐름은 좋았고 발성 조언은 더 구체적이면 좋겠습니다."
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.evaluationId", notNullValue()))
				.andExpect(jsonPath("$.data.coachingId").value(coachingId))
				.andExpect(jsonPath("$.data.rating").value(4))
				.andExpect(jsonPath("$.data.comment").value("감정 흐름은 좋았고 발성 조언은 더 구체적이면 좋겠습니다."));
	}

	@Test
	void doesNotExposeStoredCoachingLookup() throws Exception {
		String coachingId = createCoaching();

		mockMvc.perform(get("/api/v1/coachings/{coachingId}", coachingId))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsDuplicateEvaluation() throws Exception {
		String coachingId = createCompletedCoaching();
		submitEvaluation(coachingId);

		submitEvaluation(coachingId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("COACHING_EVALUATION_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.error.details.coachingId").value(coachingId));
	}

	@Test
	void rejectsUnreadableEvaluationBodyWithEnvelope() throws Exception {
		mockMvc.perform(post("/api/v1/coachings/{coachingId}/evaluation", 1)
						.contentType("application/json")
						.content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_EVALUATION_REQUEST"))
				.andExpect(jsonPath("$.error.details.fields[0]").value("body"));
	}

	@Test
	void rejectsEmptyEvaluationBodyWithEnvelope() throws Exception {
		mockMvc.perform(post("/api/v1/coachings/{coachingId}/evaluation", 1)
						.contentType("application/json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_EVALUATION_REQUEST"))
				.andExpect(jsonPath("$.error.details.fields[0]").value("body"));
	}

	@Test
	void rejectsNullEvaluationBodyWithEnvelope() throws Exception {
		mockMvc.perform(post("/api/v1/coachings/{coachingId}/evaluation", 1)
						.contentType("application/json")
						.content("null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_EVALUATION_REQUEST"))
				.andExpect(jsonPath("$.error.details.fields[0]").value("body"));
	}

	@Test
	void rejectsInvalidCoachingIdPathWithEnvelope() throws Exception {
		mockMvc.perform(post("/api/v1/coachings/{coachingId}/evaluation", "abc")
						.contentType("application/json")
						.content("""
								{
								  "rating": 5,
								  "comment": ""
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_COACHING_ID"))
				.andExpect(jsonPath("$.error.details.coachingId").value("abc"));
	}

	@Test
	void rejectsUnsupportedCoachingContentTypeWithEnvelope() throws Exception {
		mockMvc.perform(post("/api/v1/coachings")
						.contentType("application/json")
						.content("""
								{
								  "genre": "영화",
								  "situation": "헤어진 연인을 우연히 다시 만난 상황",
								  "characterSetting": "감정을 쉽게 드러내지 않는 배우 지망생",
								  "subtext": "아직 미련이 있지만 괜찮은 척한다"
								}
								"""))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"))
				.andExpect(jsonPath("$.error.details.contentType").value("application/json"));
	}

	@Test
	void rejectsUnsupportedEvaluationContentTypeWithEnvelope() throws Exception {
		mockMvc.perform(post("/api/v1/coachings/{coachingId}/evaluation", 1)
						.contentType("text/plain")
						.content("rating=5"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"))
				.andExpect(jsonPath("$.error.details.contentType").value("text/plain"));
	}

	private String createCoaching() throws Exception {
		MvcResult result = mockMvc.perform(coachingRequest(video("video/mp4")))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.coachingId");
	}

	private String createCompletedCoaching() throws Exception {
		String coachingId = createCoaching();
		mockMvc.perform(submitDiagnosis(coachingId, "상대에게 약하게 보이고 싶지 않았어요."))
				.andExpect(status().isCreated());
		return coachingId;
	}

	private org.springframework.test.web.servlet.ResultActions submitEvaluation(String coachingId) throws Exception {
		return mockMvc.perform(post("/api/v1/coachings/{coachingId}/evaluation", coachingId)
				.contentType("application/json")
				.content("""
						{
						  "rating": 5,
						  "comment": ""
						}
						"""));
	}

	private org.springframework.test.web.servlet.RequestBuilder submitDiagnosis(String coachingId, String answer) throws Exception {
		return post("/api/v1/coachings/{coachingId}/diagnosis", coachingId)
				.contentType("application/json")
				.content(OBJECT_MAPPER.writeValueAsString(Map.of("answer", answer)));
	}

	private MockMultipartFile video(String contentType) {
		return new MockMultipartFile(
				"video",
				"scene.mp4",
				contentType,
			"fake-video".getBytes(StandardCharsets.UTF_8)
	);
	}

	private MockMultipartHttpServletRequestBuilder coachingRequest() {
		return coachingRequest("영화", null);
	}

	private MockMultipartHttpServletRequestBuilder coachingRequest(String genre, String customGenre) {
		MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/coachings");
		builder.param("genre", genre);
		builder.param("situation", "헤어진 연인을 우연히 다시 만난 상황");
		builder.param("characterSetting", "감정을 쉽게 드러내지 않는 배우 지망생");
		builder.param("subtext", "아직 미련이 있지만 괜찮은 척한다");
		if (customGenre != null) {
			builder.param("customGenre", customGenre);
		}
		return builder;
	}

	private MockMultipartHttpServletRequestBuilder coachingRequest(MockMultipartFile video) {
		return coachingRequest().file(video);
	}

	private MockMultipartHttpServletRequestBuilder coachingRequest(MockMultipartFile video, String genre, String customGenre) {
		return coachingRequest(genre, customGenre).file(video);
	}

	private MockMultipartHttpServletRequestBuilder coachingRequestWithoutSubtext(MockMultipartFile video) {
		MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/coachings");
		builder.file(video);
		builder.param("genre", "영화");
		builder.param("situation", "헤어진 연인을 우연히 다시 만난 상황");
		builder.param("characterSetting", "감정을 쉽게 드러내지 않는 배우 지망생");
		return builder;
	}

	private static Path createTempDirectory() {
		try {
			return Files.createTempDirectory("acttub-video-storage-test");
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Failed to create test video storage root.", e);
		}
	}
}
