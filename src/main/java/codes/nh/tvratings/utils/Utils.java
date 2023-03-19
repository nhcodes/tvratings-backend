package codes.nh.tvratings.utils;

import codes.nh.tvratings.Application;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * this class contains several utilities
 */
public class Utils {

    //==========[LOG]==========

    private static final Logger logger = LoggerFactory.getLogger("app"); //todo

    private static final String logTag = "[tvratings] ";

    public static void log(String message) {
        //System.out.println(logTag + message);
        logger.info(logTag + message);
    }

    //==========[ASYNC]==========

    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    /**
     * executes a function in a new thread
     *
     * @param function the function to execute asynchronously
     */
    public static Future<?> doAsync(Runnable function) {
        return threadPool.submit(function);
    }

    private static final ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(1);

    /**
     * executes a function in a new thread repeatedly
     *
     * @param function            the function to execute asynchronously & repeatedly
     * @param initialDelaySeconds the initial delay in seconds before the first execution happens
     * @param intervalSeconds     the interval in seconds between the function executions
     */
    public static ScheduledFuture<?> repeatAsync(Runnable function, long initialDelaySeconds, long intervalSeconds) {
        return scheduledThreadPool.scheduleAtFixedRate(function, initialDelaySeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    //==========[VERIFICATION CODE]==========

    private static final int codeLength = 6;

    private static final char[] codeCharacters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private static final SecureRandom random = new SecureRandom();

    /**
     * generates a random verification code (36^6 possible values)
     *
     * @return the generated code
     */
    public static String generateVerificationCode() {
        StringBuilder stringBuilder = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            int randomIndex = random.nextInt(codeCharacters.length);
            char randomCharacter = codeCharacters[randomIndex];
            stringBuilder.append(randomCharacter);
        }
        return stringBuilder.toString();
    }

    //==========[JWT]==========

    /*
    https://londonappdeveloper.com/json-web-tokens-vs-token-authentication/
    https://jwt.io/introduction
    https://auth0.com/learn/token-based-authentication-made-easy
    https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie
    https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies
     */

    private static final Charset charset = StandardCharsets.UTF_8;

    private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder decoder = Base64.getUrlDecoder();

    private static final String algorithm = "HmacSHA256";

    private static Mac mac;

    public static Mac initMac(String key) {
        try {
            mac = Mac.getInstance(algorithm);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(charset), algorithm);
            mac.init(secretKey);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            Utils.log("error while initializing mac: " + ex.getMessage());
        }
        return null;
    }

    /**
     * creates a JSON Web Token
     *
     * @param email the user email
     * @return the created token
     */
    public static String createJWT(String email) {
        JSONObject headerJson = new JSONObject();
        headerJson.put("alg", "HS256");
        headerJson.put("typ", "JWT");
        String encodedHeader = encoder.encodeToString(headerJson.toString().getBytes(charset));

        JSONObject payloadJson = new JSONObject();
        payloadJson.put("email", email);
        String encodedPayload = encoder.encodeToString(payloadJson.toString().getBytes(charset));

        String data = encodedHeader + "." + encodedPayload;

        String encodedSignature = encoder.encodeToString(getSignature(data));

        return data + "." + encodedSignature;
    }

    /**
     * verifies a JSON Web Token
     *
     * @param token the token
     * @return the email if the token was verified successfully, null otherwise
     */
    public static String verifyJWT(String token) {
        try {

            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            String encodedHeader = parts[0];
            String encodedPayload = parts[1];
            String encodedSignature = parts[2];

            //JSONObject headerJson = new JSONObject(new String(decoder.decode(encodedHeader), charset));
            JSONObject payloadJson = new JSONObject(new String(decoder.decode(encodedPayload), charset));

            String data = encodedHeader + "." + encodedPayload;

            String validSignature = encoder.encodeToString(getSignature(data));

            boolean verified = encodedSignature.equals(validSignature);
            return verified ? payloadJson.optString("email") : null;

        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] getSignature(String data) {
        return mac.doFinal(data.getBytes(charset));
    }

    //==========[Google Recaptcha]==========

    public static boolean verifyRecaptchaToken(String token) throws Exception {
        String secret = Application.configuration.recaptchaSecret;

        String parameters = "secret=%s&response=%s".formatted(secret, token);
        URI url = new URI("https://www.google.com/recaptcha/api/siteverify?" + parameters);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(url)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.of(10, ChronoUnit.SECONDS))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        JSONObject jsonResponse = new JSONObject(response.body());
        boolean success = jsonResponse.getBoolean("success");
        Utils.log(statusCode + " - " + jsonResponse);
        return success;
    }

    //==========[OTHER]==========

    /**
     * listens for new console messages. this method is blocking
     *
     * @param listener gets called when there is a new message
     */
    public static void listenForConsoleCommands(Consumer<String> listener) {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String command = scanner.nextLine();
            listener.accept(command);
        }
    }

    public static Integer stringToIntOrNull(String string) {
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Boolean stringToBooleanOrNull(String string) {
        if (string == null) return null;
        if (string.equalsIgnoreCase("true")) return true;
        else if (string.equalsIgnoreCase("false")) return false;
        else return null;
    }

    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * @return the current utc date in the format yyyyMMdd
     */
    public static String getDateString() {
        ZonedDateTime utcTime = ZonedDateTime.now(ZoneOffset.UTC);
        return utcTime.format(dateFormat);
    }

}
