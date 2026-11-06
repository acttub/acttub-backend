package com.loading.acttub_backend.coaching;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalysisTimeoutException;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import com.loading.acttub_backend.coaching.application.port.CoachingRepository;
import com.loading.acttub_backend.coaching.domain.Coaching;
import com.loading.acttub_backend.coaching.domain.CoachingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoachingAiFailureIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CoachingRepository coachingRepository;

	@MockitoBean
	private CoachingAnalyzer coachingAnalyzer;

	@Test
	void keepsFailedCoachingWhenAiAnalysisFails() throws Exception {
		when(coachingAnalyzer.analyze(any(), any(CoachingInput.class))).thenThrow(new RuntimeException("provider unavailable"));

		MvcResult result = mockMvc.perform(coachingRequest(new MockMultipartFile(
								"video",
								"scene.mp4",
								"video/mp4",
								"fake-video".getBytes(StandardCharsets.UTF_8)
						)))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error.code").value("AI_ANALYSIS_FAILED"))
				.andExpect(jsonPath("$.error.details.coachingId").exists())
				.andExpect(jsonPath("$.error.details.status").value("FAILED"))
				.andReturn();

		String coachingId = JsonPath.read(result.getResponse().getContentAsString(), "$.error.details.coachingId");
		Coaching coaching = coachingRepository.findById(Long.valueOf(coachingId)).orElseThrow();

		org.assertj.core.api.Assertions.assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.FAILED);
	}

	@Test
	void mapsAiAnalysisTimeoutToGatewayTimeout() throws Exception {
		when(coachingAnalyzer.analyze(any(), any(CoachingInput.class)))
				.thenThrow(new CoachingAnalysisTimeoutException("provider timeout", new RuntimeException("timeout")));

		MvcResult result = mockMvc.perform(coachingRequest(new MockMultipartFile(
								"video",
								"scene.mp4",
								"video/mp4",
								"fake-video".getBytes(StandardCharsets.UTF_8)
						)))
				.andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.error.code").value("AI_ANALYSIS_TIMEOUT"))
				.andExpect(jsonPath("$.error.details.coachingId").exists())
				.andExpect(jsonPath("$.error.details.status").value("FAILED"))
				.andReturn();

		String coachingId = JsonPath.read(result.getResponse().getContentAsString(), "$.error.details.coachingId");
		Coaching coaching = coachingRepository.findById(Long.valueOf(coachingId)).orElseThrow();

		org.assertj.core.api.Assertions.assertThat(coaching.getStatus()).isEqualTo(CoachingStatus.FAILED);
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
