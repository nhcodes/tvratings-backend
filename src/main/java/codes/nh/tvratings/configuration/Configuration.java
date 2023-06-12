package codes.nh.tvratings.configuration;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * This class is responsible for loading and saving the configuration file.
 * It contains all the necessary parameters for the application.
 */
public class Configuration {

    private final File file;

    //server

    public int serverPort = 7070;

    //ssl

    public boolean sslEnabled = false;

    public String sslCertificatePath = "";

    public String sslPrivateKeyPath = "";

    //cors

    public String corsHost = "http://localhost:63342";

    //jwt

    public int jwtExpireSeconds = 60 * 60 * 24 * 7;

    public String jwtSecretKey = "abc123";

    //mail

    public String smtpHost = "smtp.gmail.com";

    public String smtpPort = "587";

    public boolean smtpAuth = true;

    public boolean smtpStartTLS = true;

    public String emailUsername = "user";

    public String emailPassword = "pass";

    public String emailFrom = "";

    //recaptcha

    public String recaptchaSecret = "";

    //database updates

    public boolean updateDatabase = true;

    public Configuration(File file) {
        this.file = file;
    }

    public void createIfNotExists() throws Exception {
        if (file.exists()) return;

        JSONObject configJson = new JSONObject();

        configJson.put("serverPort", serverPort);

        configJson.put("sslEnabled", sslEnabled);
        configJson.put("sslCertificatePath", sslCertificatePath);
        configJson.put("sslPrivateKeyPath", sslPrivateKeyPath);

        configJson.put("corsHost", corsHost);

        configJson.put("jwtExpireSeconds", jwtExpireSeconds);
        configJson.put("jwtSecretKey", jwtSecretKey);

        configJson.put("smtpHost", smtpHost);
        configJson.put("smtpPort", smtpPort);
        configJson.put("smtpAuth", smtpAuth);
        configJson.put("smtpStartTLS", smtpStartTLS);
        configJson.put("emailUsername", emailUsername);
        configJson.put("emailPassword", emailPassword);
        configJson.put("emailFrom", emailFrom);

        configJson.put("recaptchaSecret", recaptchaSecret);

        configJson.put("updateDatabase", updateDatabase);

        Files.writeString(file.toPath(), configJson.toString(2), StandardOpenOption.CREATE);
    }

    public void load() throws Exception {
        String fileContent = Files.readString(file.toPath());
        JSONObject configJson = new JSONObject(fileContent);

        serverPort = configJson.getInt("serverPort");

        sslEnabled = configJson.getBoolean("sslEnabled");
        sslCertificatePath = configJson.getString("sslCertificatePath");
        sslPrivateKeyPath = configJson.getString("sslPrivateKeyPath");

        corsHost = configJson.getString("corsHost");

        jwtExpireSeconds = configJson.getInt("jwtExpireSeconds");
        jwtSecretKey = configJson.getString("jwtSecretKey");

        smtpHost = configJson.getString("smtpHost");
        smtpPort = configJson.getString("smtpPort");
        smtpAuth = configJson.getBoolean("smtpAuth");
        smtpStartTLS = configJson.getBoolean("smtpStartTLS");
        emailUsername = configJson.getString("emailUsername");
        emailPassword = configJson.getString("emailPassword");
        emailFrom = configJson.getString("emailFrom");

        recaptchaSecret = configJson.getString("recaptchaSecret");

        updateDatabase = configJson.getBoolean("updateDatabase");
    }

}
