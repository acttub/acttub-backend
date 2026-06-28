package com.loading.acttub_backend.waitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WaitlistApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void registersWaitlistEntry() throws Exception {
		mockMvc.perform(post("/api/v1/waitlist")
						.contentType("application/json")
						.content("""
								{
								  "email": "actor@example.com",
								  "actorStatus": "배우 지망생",
								  "helpTopic": "인물의 목적 찾기",
								  "consent": true,
								  "website": ""
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.accepted").value(true))
				.andExpect(jsonPath("$.data.waitlistId", notNullValue()))
				.andExpect(jsonPath("$.data.email").value("actor@example.com"))
				.andExpect(jsonPath("$.data.actorStatus").value("배우 지망생"))
				.andExpect(jsonPath("$.data.helpTopic").value("인물의 목적 찾기"))
				.andExpect(jsonPath("$.data.createdAt", notNullValue()));

		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from waitlist_registrations where email = ?",
				Integer.class,
				"actor@example.com"
		);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void rejectsInvalidWaitlistRequest() throws Exception {
		mockMvc.perform(post("/api/v1/waitlist")
						.contentType("application/json")
						.content("""
								{
								  "email": "not-an-email",
								  "consent": false
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_WAITLIST_REQUEST"))
				.andExpect(jsonPath("$.error.details.fields[0]").value("email"))
				.andExpect(jsonPath("$.error.details.fields[1]").value("consent"));
	}

	@Test
	void rejectsDuplicateEmail() throws Exception {
		register("duplicate@example.com");

		mockMvc.perform(post("/api/v1/waitlist")
						.contentType("application/json")
						.content("""
								{
								  "email": "duplicate@example.com",
								  "consent": true
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("WAITLIST_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.error.details.email").value("duplicate@example.com"));
	}

	@Test
	void acceptsHoneypotRequestWithoutSaving() throws Exception {
		mockMvc.perform(post("/api/v1/waitlist")
						.contentType("application/json")
						.content("""
								{
								  "email": "bot@example.com",
								  "actorStatus": "현역 배우",
								  "helpTopic": "대본 분석",
								  "consent": true,
								  "website": "https://spam.example"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.accepted").value(true))
				.andExpect(jsonPath("$.data.waitlistId").doesNotExist());

		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from waitlist_registrations where email = ?",
				Integer.class,
				"bot@example.com"
		);
		assertThat(count).isZero();
	}

	private void register(String email) throws Exception {
		mockMvc.perform(post("/api/v1/waitlist")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "consent": true
								}
								""".formatted(email)))
				.andExpect(status().isCreated());
	}
}
