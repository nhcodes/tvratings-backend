package codes.nh.tvratings.mail;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/**
 * This class provides functionality for sending emails using the Jakarta Mail API.
 */
public class MailManager {

    private final Session session;

    private final String emailFrom;

    public MailManager(String host, String port, boolean auth, boolean startTls, String username, String password, String emailFrom) {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.auth", auth);
        properties.put("mail.smtp.starttls.enable", startTls);
        if (auth) {
            this.session = Session.getInstance(properties, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
        } else {
            this.session = Session.getInstance(properties);
        }
        this.emailFrom = emailFrom;
    }

    public void sendMail(String email, String subject, String htmlContent) throws Exception {
        MimeMessage message = new MimeMessage(session);
        if (!emailFrom.isBlank()) {
            message.setFrom(emailFrom);
        }
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
        message.setSubject(subject);
        message.setText(htmlContent, "utf-8", "html");
        Transport.send(message);
    }

}
