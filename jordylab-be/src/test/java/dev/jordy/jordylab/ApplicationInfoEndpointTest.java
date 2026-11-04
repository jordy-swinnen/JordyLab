package dev.jordy.jordylab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "JORDYLAB_GIT_COMMIT=" + ApplicationInfoEndpointTest.BUILT_COMMIT)
@AutoConfigureMockMvc
class ApplicationInfoEndpointTest {

    static final String BUILT_COMMIT = "9f3c2a1e0b7d4c6a8e5f1b2c3d4e5f6071829304";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void infoEndpointReportsTheCommitTheImageWasBuiltFromWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commit").value(BUILT_COMMIT));
    }
}
