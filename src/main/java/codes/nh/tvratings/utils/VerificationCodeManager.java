package codes.nh.tvratings.utils;

import java.security.SecureRandom;

public class VerificationCodeManager {

    private static final int CODE_LENGTH = 6;

    private static final char[] CODE_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a random verification code (36^6 possible values).
     *
     * @return The generated code.
     */
    public String generateVerificationCode() {
        StringBuilder stringBuilder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int randomIndex = random.nextInt(CODE_CHARACTERS.length);
            char randomCharacter = CODE_CHARACTERS[randomIndex];
            stringBuilder.append(randomCharacter);
        }
        return stringBuilder.toString();
    }

}
