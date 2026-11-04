package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import dev.jordy.jordylab.identity.rest.controller.TestSignInController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The e2e sign-in helper exists only under the {@code e2e} profile (spec 019 US7): production registers no controller for
 * {@code /api/identity/test/sign-in}, so the path is a plain 404 there. The runner proves the profile condition itself,
 * without booting either environment.
 */
class ProdTestSignInAbsenceTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(UserAccountRepository.class, () -> mock(UserAccountRepository.class))
            .withUserConfiguration(TestSignInController.class);

    @Test
    void theProdProfileDoesNotRegisterTheHelper() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).doesNotHaveBean(TestSignInController.class));
    }

    @Test
    void theLocalProfileDoesNotRegisterTheHelper() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context).doesNotHaveBean(TestSignInController.class));
    }

    @Test
    void theE2eProfileRegistersIt() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local", "e2e"))
                .run(context -> assertThat(context).hasSingleBean(TestSignInController.class));
    }

}
