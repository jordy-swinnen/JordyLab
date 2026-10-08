package dev.jordy.jordylab.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.settings.domain.GuestChatUsage;
import dev.jordy.jordylab.settings.domain.repository.GuestChatUsageRepository;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Proves the settings-owned guest chat limit end to end (FR-009, SC — 429 with {@code resetsAt},
 * no AI call, and the count persisted rather than held in memory). The daily limit is overridden
 * to 1 so the boundary is reached without real AI-backed chat traffic; the row is seeded directly
 * through the repository (a legitimate test fixture, not a hand-seed of a live database).
 */
class GuestChatLimitIntegrationTest extends KeycloakIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private ResilientAiService resilientAiService;

    @Autowired
    private GuestChatUsageRepository guestChatUsageRepository;

    @Autowired
    private SettingsProperties settingsProperties;

    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void guestChatLimit(DynamicPropertyRegistry registry) {
        registry.add("jordylab.settings.guest-chat.daily-limit", () -> "1");
    }

    @Test
    @Transactional
    void guestAtTheDailyLimitGetsA429WithResetsAtAndNoAiCall() throws Exception {
        String guest = accessTokenFor("guest-user", "guest-password");
        String subject = subjectOf(guest);
        LocalDate today = LocalDate.now(ZoneId.of(settingsProperties.guestChat().zone()));
        guestChatUsageRepository.incrementUsage(UUID.randomUUID(), subject, today);
        entityManager.clear();

        MvcResult result = mockMvc.perform(chatRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guest))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertSoftly(softly -> {
            softly.assertThat(result.getResponse().getStatus()).isEqualTo(429);
            softly.assertThat(body).contains("CHAT_LIMIT_REACHED");
        });
        // Start-up housekeeping may ask the AI layer for its embedding model name; a refused question must make no model call.
        assertThat(Mockito.mockingDetails(resilientAiService).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName())
                .filter(name -> List.of("call", "callStructured", "embed").contains(name))).isEmpty();

        // A fresh persistence context still finds the seeded count — it is a stored row,
        // not an in-memory counter that a restart would silently reset.
        entityManager.clear();
        Optional<GuestChatUsage> usage = guestChatUsageRepository.findByUserSubjectAndUsageDate(subject, today);
        assertThat(usage).isPresent();
        assertThat(usage.get().getMessageCount()).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder chatRequest() {
        return post("/api/gamecatalog/libbot/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"c1\",\"message\":\"anything\"}");
    }

    private String subjectOf(String accessToken) throws Exception {
        String[] parts = accessToken.split("\\.");
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode payload = OBJECT_MAPPER.readTree(payloadBytes);

        return payload.path("sub").asText();
    }
}
