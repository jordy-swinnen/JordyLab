package dev.jordy.jordylab.mobile.rest.controller;

import dev.jordy.jordylab.mobile.MobileProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssetLinksController.class)
@Import(AssetLinksControllerTest.PropertiesConfiguration.class)
class AssetLinksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesTheDigitalAssetLinksStatementAsJson() throws Exception {
        mockMvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].relation[0]").value("delegate_permission/common.handle_all_urls"))
                .andExpect(jsonPath("$[0].target.namespace").value("android_app"))
                .andExpect(jsonPath("$[0].target.package_name").value("dev.jordy.jordylab.mobile"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[0]").value("b".repeat(64)));
    }

    @TestConfiguration
    static class PropertiesConfiguration {

        @Bean
        MobileProperties mobileProperties() {
            return new MobileProperties(
                    new MobileProperties.Release(null, "b".repeat(64)),
                    new MobileProperties.DownloadLink(null, 5),
                    new MobileProperties.App("dev.jordy.jordylab.mobile", "example.test"),
                    null);
        }
    }
}
