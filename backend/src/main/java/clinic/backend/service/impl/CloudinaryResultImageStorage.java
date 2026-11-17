package clinic.backend.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import clinic.backend.config.properties.CloudinaryProperties;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.service.ResultImageStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// luu anh ket qua len Cloudinary bang request co chu ky (khong dung SDK)
// API secret chi o server; anh dung public id ngau nhien de khong doan duoc link
@Slf4j
@Component
public class CloudinaryResultImageStorage implements ResultImageStorage {

    private static final String API = "https://api.cloudinary.com/v1_1/";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final CloudinaryProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public CloudinaryResultImageStorage(CloudinaryProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    // da cau hinh Cloudinary chua
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public String folder() {
        return properties.folder();
    }

    @Override
    // tai anh len, tra ve link anh
    public String upload(String publicId, byte[] content, String contentType) {
        requireConfigured();
        // gui file dang data URI base64, kem chu ky
        Map<String, String> params = signed(Map.of("public_id", publicId));
        params.put("file", "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(content));
        JsonNode body = post("image/upload", params);
        String url = body.path("secure_url").asText(null);
        if (url == null || url.isBlank()) {
            log.warn("Cloudinary upload answered without secure_url [publicId={}]", publicId);
            throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        }
        return url;
    }

    @Override
    // xoa anh tren Cloudinary
    public void delete(String publicId) {
        requireConfigured();
        post("image/destroy", signed(Map.of("public_id", publicId, "invalidate", "true")));
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new AppException(ErrorCode.FILE_STORAGE_NOT_CONFIGURED);
        }
    }

    // ky request: sha1(cac tham so sap xep a-z + api secret)
    private Map<String, String> signed(Map<String, String> values) {
        Map<String, String> sorted = new TreeMap<>(values);
        sorted.put("timestamp", String.valueOf(clock.instant().getEpochSecond()));
        String toSign = sorted.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        Map<String, String> params = new LinkedHashMap<>(sorted);
        params.put("api_key", properties.apiKey());
        params.put("signature", sha1Hex(toSign + properties.apiSecret()));
        return params;
    }

    // goi API Cloudinary, loi thi bao FILE_STORAGE_UNAVAILABLE
    private JsonNode post(String action, Map<String, String> params) {
        String form = params.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + properties.cloudName() + "/" + action))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // khong log body: co the chua thong tin tai khoan
                log.warn("Cloudinary {} failed [status={}]", action, response.statusCode());
                throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            log.warn("Cloudinary {} unreachable [error={}]", action, exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        }
    }

    private static String sha1Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
