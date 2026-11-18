package clinic.backend.service.impl;

import java.net.URI;

import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-52 BR-52-06: the server reads only its own Cloudinary delivery addresses. */
class CloudinaryResultImageStorageTest {

    @Test
    void acceptsADeliveryAddressOfTheAccount() {
        String url = "https://res.cloudinary.com/demo/image/upload/v1731/clinic/paraclinical/abc.png";

        assertThat(CloudinaryResultImageStorage.deliveryUri(url, "demo")).isEqualTo(URI.create(url));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "http://res.cloudinary.com/demo/image/upload/abc.png",
            "https://res.cloudinary.com/other/image/upload/abc.png",
            "https://evil.example/demo/image/upload/abc.png",
            "https://res.cloudinary.com:8443/demo/image/upload/abc.png",
            "https://user@res.cloudinary.com/demo/image/upload/abc.png",
            "https://res.cloudinary.com/demo/../other/abc.png",
            "file:///etc/passwd",
            "not a url",
    })
    void refusesAnyOtherAddress(String url) {
        assertThatThrownBy(() -> CloudinaryResultImageStorage.deliveryUri(url, "demo"))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_STORAGE_UNAVAILABLE));
    }
}
