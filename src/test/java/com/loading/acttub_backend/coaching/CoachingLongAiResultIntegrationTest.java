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
import com.loading.acttub_backend.coaching.application.model.CoachingInput;
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

	@Test
	void storesLongAiResultLabelsWithoutTruncation() throws Exception {
		MvcResult result = mockMvc.perform(coachingRequest(new MockMultipartFile(
								"video",
								"scene.mp4",
								"video/mp4",
								"fake-video".getBytes(StandardCharsets.UTF_8)
						)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.coachingId", notNullValue()))
				.andExpect(jsonPath("$.data.status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.result.feedbackCards[0].expectedEffect").value(longText("effect")))
				.andReturn();

		String coachingId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.coachingId");
		Coaching coaching = coachingRepository.findById(Long.valueOf(coachingId)).orElseThrow();

		assertThat(coaching.getResultSceneIntent()).isEqualTo(longText("title"));
		assertThat(coaching.getResultStrengthSignal()).isEqualTo(longText("strength"));
		assertThat(coaching.getResultFocusTimecode()).isEqualTo(longText("timecode"));
		assertThat(coaching.getResultFocusObservedSignal()).isEqualTo(longText("observation"));
		assertThat(coaching.getResultFocusRootCause()).isEqualTo(longText("cause"));
		assertThat(coaching.getResultFocusPrescription()).isEqualTo(longText("step"));
		assertThat(coaching.getResultNextStepText()).isEqualTo(longText("effect"));
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
		public CoachingAnalysisResult analyze(VideoInput video, CoachingInput input) {
				CoachFeedback feedback = new CoachFeedback(
						new CoachFeedback.OverallStrength(longText("strength")),
						List.of(new CoachFeedback.FeedbackCard(
								1,
								longText("title"),
								List.of(new CoachFeedback.Observation(longText("timecode"), longText("observation"))),
								longText("cause"),
								List.of(longText("step")),
								longText("effect")
						))
			);
			return new CoachingAnalysisResult("gemini", "Gemini 3.5 Flash", BigDecimal.ZERO, "analysis-v0.2", feedback);
		}
	}

	private static String longText(String prefix) {
		return prefix + "-" + "x".repeat(150);
	}

	private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder coachingRequest(MockMultipartFile video) {
		org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/coachings");
		builder.file(video);
		builder.param("genre", "영화");
		builder.param("situation", "헤어진 연인을 우연히 다시 만난 상황");
		builder.param("characterSetting", "감정을 쉽게 드러내지 않는 배우 지망생");
		builder.param("subtext", "아직 미련이 있지만 괜찮은 척한다");
		return builder;
	}
}
