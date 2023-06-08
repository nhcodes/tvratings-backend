package codes.nh.tvratings.utils;

import codes.nh.tvratings.Application;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class RecaptchaManager {

    private static final int TIMEOUT_SECONDS = 10;

    private final HttpClient client = HttpClient.newHttpClient();

    public boolean verifyToken(String token) {
        if (token == null) return false;

        try {

            String secret = Application.configuration.recaptchaSecret;
            String url = "https://www.google.com/recaptcha/api/siteverify?secret=%s&response=%s".formatted(secret, token);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.of(TIMEOUT_SECONDS, ChronoUnit.SECONDS))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            JSONObject jsonResponse = new JSONObject(response.body());
            boolean success = jsonResponse.getBoolean("success");
            Utils.log(statusCode + " - " + jsonResponse);
            return success;

        } catch (Exception e) {
            Utils.log("error while verifying recaptcha token");
        }
        return false;
    }

}
