package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.MarkResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.VoteTotalsResponse;
import dev.jordy.jordylab.gamecatalog.service.MarkService;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(MarkController.class)
class MarkControllerTest {

    private static final UUID GAME_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final String URL = "/api/gamecatalog/games/" + GAME_ID + "/mark";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarkService markService;

    @Test
    void setsTheMarkOfTheAuthenticatedSubjectNotOfAnythingTheBodySays() throws Exception {
        when(markService.setMark(GAME_ID, "guest-subject", MarkType.WANT_TO_PLAY)).thenReturn(
                Optional.of(new MarkResponse(new VoteTotalsResponse(3, 1, 0), MarkType.WANT_TO_PLAY)));

        mockMvc.perform(put(URL).with(jwt().jwt(token -> token.subject("guest-subject")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mark\":\"WANT_TO_PLAY\",\"userSubject\":\"somebody-else\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myMark").value("WANT_TO_PLAY"))
                .andExpect(jsonPath("$.votes.wantToPlay").value(3))
                .andExpect(jsonPath("$.votes.playedLiked").value(1));
    }

    @Test
    void aNullMarkClearsIt() throws Exception {
        when(markService.setMark(GAME_ID, "guest-subject", null)).thenReturn(
                Optional.of(new MarkResponse(VoteTotalsResponse.none(), null)));

        mockMvc.perform(put(URL).with(jwt().jwt(token -> token.subject("guest-subject")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mark\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myMark").doesNotExist());
    }

    @Test
    void aGameThatIsNotVisibleIsNotFound() throws Exception {
        when(markService.setMark(GAME_ID, "guest-subject", MarkType.PLAYED_LIKED)).thenReturn(Optional.empty());

        mockMvc.perform(put(URL).with(jwt().jwt(token -> token.subject("guest-subject")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mark\":\"PLAYED_LIKED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownMarkValueIsABadRequest() throws Exception {
        mockMvc.perform(put(URL).with(jwt().jwt(token -> token.subject("guest-subject")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mark\":\"LOVED_IT\"}"))
                .andExpect(status().isBadRequest());
    }
}
