package com.loading.acttub_backend.coaching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import com.loading.acttub_backend.coaching.application.port.CoachingRepository;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.Coaching;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoachingLongAiResultIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CoachingRepository coachingRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void storesLongAiResultLabelsWithoutTruncation() throws Exception {
		MvcResult result = mockMvc.perform(multipart("/api/v1/coachings")
						.file(new MockMultipartFile(
								"video",
								"scene.mp4",
								"video/mp4",
								"fake-video".getBytes(StandardCharsets.UTF_8)
						))
						.param("performanceIntent", "차분하지만 단호한 감정"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.coachingId", notNullValue()))
				.andExpect(jsonPath("$.data.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.result.nextStep.action").value(longText("action")))
				.andReturn();

		String coachingId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.coachingId");
		Coaching coaching = coachingRepository.findById(Long.valueOf(coachingId)).orElseThrow();

		assertThat(coaching.getResultStrengthTimecode()).isEqualTo(longText("strength-timecode"));
		assertThat(coaching.getResultStrengthAxis()).isEqualTo(longText("axis"));
		assertThat(coaching.getResultStrengthTier()).isEqualTo(longText("tier"));
		assertThat(coaching.getResultFocusTimecode()).isEqualTo(longText("focus-timecode"));
		assertThat(coaching.getResultNextStepAction()).isEqualTo(longText("action"));
		assertThat(jdbcTemplate.queryForList(
				"select axis from coaching_focus_axes where coaching_id = ? order by axis_order",
				String.class,
				Long.valueOf(coachingId)
		)).containsExactly(longText("focus-axis"));
	}

	@TestConfiguration
	static class LongAiResultConfig {

		@Bean
		@Primary
		CoachingAnalyzer longAiResultAnalyzer() {
			return new LongAiResultAnalyzer();
		}
	}

	private static class LongAiResultAnalyzer implements CoachingAnalyzer {

		@Override
		public CoachingAnalysisResult analyze(VideoInput video, String performanceIntent) {
				CoachFeedback feedback = new CoachFeedback(
						new CoachFeedback.SceneIntent(performanceIntent, "actor_input"),
						new CoachFeedback.Strength(
								longText("strength-timecode"),
								longText("axis"),
								"관찰 신호",
							"좋은 이유",
							longText("tier")
						),
						new CoachFeedback.Focus(
								longText("focus-timecode"),
								List.of(longText("focus-axis")),
							"관찰",
							"원인",
							"차이",
							"처방"
					),
					new CoachFeedback.NextStep("다음 단계", longText("action"))
			);
			return new CoachingAnalysisResult("gemini", "Gemini 3.5 Flash", BigDecimal.ZERO, "analysis-v0.2", feedback);
		}
	}

	private static String longText(String prefix) {
		return prefix + "-" + "x".repeat(150);
	}
}
