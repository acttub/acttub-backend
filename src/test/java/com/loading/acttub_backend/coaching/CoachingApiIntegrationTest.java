package com.loading.acttub_backend.coaching;

import static org.hamcrest.Matchers.notNullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoachingApiIntegrationTest {

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
	void createsCompletedCoachingWithAiResult() throws Exception {
		MockMultipartFile video = video("video/mp4");

		mockMvc.perform(multipart("/api/v1/coachings")
						.file(video)
						.param("performanceIntent", "차분하지만 단호한 감정"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.coachingId", notNullValue()))
				.andExpect(jsonPath("$.data.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.input.performanceIntent").value("차분하지만 단호한 감정"))
				.andExpect(jsonPath("$.data.result.sceneIntent.text", notNullValue()))
				.andExpect(jsonPath("$.data.result.focus.prescription", notNullValue()));
	}

	@Test
	void storesUploadedVideoFileAndRecordsStorageLocation() throws Exception {
		MockMultipartFile video = new MockMultipartFile(
				"video",
				"scene.mp4",
				"video/mp4",
				"stored-video".getBytes(StandardCharsets.UTF_8)
		);

		MvcResult result = mockMvc.perform(multipart("/api/v1/coachings")
						.file(video)
						.param("performanceIntent", "차분하지만 단호한 감정"))
				.andExpect(status().isCreated())
				.andReturn();

		String coachingId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.coachingId");
		Coaching coaching = coachingRepository.findById(Long.valueOf(coachingId)).orElseThrow();

		assertThat(coaching.getVideoStorageKey()).isEqualTo("coachings/" + coachingId + "/scene.mp4");
		assertThat(coaching.getVideoStorageUri()).startsWith("file:");
		assertThat(Files.readString(TEST_VIDEO_ROOT.resolve(coaching.getVideoStorageKey()))).isEqualTo("stored-video");
	}

	@Test
	void storesFocusAxesAsNormalizedRows() throws Exception {
		String coachingId = createCoaching();

		List<String> axes = jdbcTemplate.queryForList(
				"select axis from coaching_focus_axes where coaching_id = ? order by axis_order",
				String.class,
				Long.valueOf(coachingId)
		);

		assertThat(axes).containsExactly("emotion", "speech");
	}

	@Test
	void rejectsCoachingRequestWithoutVideo() throws Exception {
		mockMvc.perform(multipart("/api/v1/coachings")
						.param("performanceIntent", "차분하지만 단호한 감정"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_COACHING_REQUEST"))
				.andExpect(jsonPath("$.error.details.fields[0]").value("video"));
	}

	@Test
	void rejectsUnsupportedVideoContentType() throws Exception {
		MockMultipartFile video = video("text/plain");

		mockMvc.perform(multipart("/api/v1/coachings")
						.file(video)
						.param("performanceIntent", "차분하지만 단호한 감정"))
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
				return 314572801L;
			}
		};

		mockMvc.perform(multipart("/api/v1/coachings")
						.file(video)
						.param("performanceIntent", "차분하지만 단호한 감정"))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"))
				.andExpect(jsonPath("$.error.details.maxSizeBytes").value(314572800));
	}

	@Test
	void evaluatesCompletedCoachingOnce() throws Exception {
		String coachingId = createCoaching();

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
		String coachingId = createCoaching();
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

	private String createCoaching() throws Exception {
		MvcResult result = mockMvc.perform(multipart("/api/v1/coachings")
						.file(video("video/mp4"))
						.param("performanceIntent", "차분하지만 단호한 감정"))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.coachingId");
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

	private MockMultipartFile video(String contentType) {
		return new MockMultipartFile(
				"video",
				"scene.mp4",
				contentType,
			"fake-video".getBytes(StandardCharsets.UTF_8)
	);
	}

	private static Path createTempDirectory() {
		try {
			return Files.createTempDirectory("acttub-video-storage-test");
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Failed to create test video storage root.", e);
		}
	}
}
