package clinic.backend.security;

import java.security.SecureRandom;
import java.util.stream.IntStream;

import clinic.backend.entity.StaffAccount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator(new SecureRandom());

    @Test
    void generatesTwelveCharacterPasswordsThatSatisfyTheAccountPolicy() {
        assertThat(IntStream.range(0, 100)
                        .mapToObj(ignored -> generator.generate()))
                .allSatisfy(password -> {
                    assertThat(password).hasSize(TemporaryPasswordGenerator.PASSWORD_LENGTH);
                    assertThat(StaffAccount.checkPasswordPolicy(password)).isEmpty();
                });
    }
}
