package clinic.backend.config.properties;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UC-67 result images on Cloudinary. {@code url} is the CLOUDINARY_URL of the account,
 * {@code cloudinary://<api_key>:<api_secret>@<cloud_name>}; it is a secret and comes only from the environment.
 * Without it the backend still starts and an upload answers FILE_STORAGE_NOT_CONFIGURED.
 */
@ConfigurationProperties(prefix = "clinic.cloudinary")
public record CloudinaryProperties(String url, String folder) {

    public CloudinaryProperties {
        folder = folder == null || folder.isBlank() ? "clinic/paraclinical" : folder.strip();
    }

    public boolean isConfigured() {
        return credentials() != null;
    }

    public String cloudName() {
        return credentials()[2];
    }

    public String apiKey() {
        return credentials()[0];
    }

    public String apiSecret() {
        return credentials()[1];
    }

    /** api key, api secret, cloud name; null when the url is missing or malformed. */
    private String[] credentials() {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.strip());
            String userInfo = uri.getRawUserInfo();
            if (!"cloudinary".equals(uri.getScheme()) || userInfo == null || !userInfo.contains(":")
                    || uri.getHost() == null) {
                return null;
            }
            String key = userInfo.substring(0, userInfo.indexOf(':'));
            String secret = userInfo.substring(userInfo.indexOf(':') + 1);
            return key.isBlank() || secret.isBlank() ? null : new String[] {key, secret, uri.getHost()};
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "CloudinaryProperties[url=redacted, folder=" + folder + "]";
    }
}
