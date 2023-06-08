package codes.nh.tvratings.utils;

import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class JWTManager {

    /*
    https://jwt.io/introduction
    https://auth0.com/learn/token-based-authentication-made-easy
    */

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private static final String ALGORITHM = "HmacSHA256";

    private final Mac mac;

    public JWTManager(String secretKey) {
        this.mac = initMac(secretKey);
    }

    private Mac initMac(String key) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(CHARSET), ALGORITHM);
            mac.init(secretKey);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Utils.log("error while initializing mac: " + e.getMessage());
        }
        return null;
    }

    private byte[] getSignature(String data) {
        return mac.doFinal(data.getBytes(CHARSET));
    }

    /**
     * Creates a JSON Web Token.
     *
     * @param email The user email.
     * @return The created token.
     */
    public String createJWT(String email) {
        JSONObject headerJson = new JSONObject();
        headerJson.put("alg", "HS256");
        headerJson.put("typ", "JWT");
        String encodedHeader = ENCODER.encodeToString(headerJson.toString().getBytes(CHARSET));

        JSONObject payloadJson = new JSONObject();
        payloadJson.put("email", email);
        String encodedPayload = ENCODER.encodeToString(payloadJson.toString().getBytes(CHARSET));

        String data = encodedHeader + "." + encodedPayload;

        String encodedSignature = ENCODER.encodeToString(getSignature(data));

        return data + "." + encodedSignature;
    }

    /**
     * Verifies a JSON Web Token.
     *
     * @param token The token.
     * @return The user email if the token was verified successfully, null otherwise.
     */
    public String verifyJWT(String token) {
        try {

            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            String encodedHeader = parts[0];
            String encodedPayload = parts[1];
            String encodedSignature = parts[2];

            //JSONObject headerJson = new JSONObject(new String(DECODER.decode(encodedHeader), CHARSET));
            JSONObject payloadJson = new JSONObject(new String(DECODER.decode(encodedPayload), CHARSET));

            String data = encodedHeader + "." + encodedPayload;

            String validSignature = ENCODER.encodeToString(getSignature(data));

            boolean verified = encodedSignature.equals(validSignature);
            return verified ? payloadJson.optString("email") : null;

        } catch (Exception e) {
            Utils.log("error while verifying jwt: " + e.getMessage());
        }
        return null;
    }

}
