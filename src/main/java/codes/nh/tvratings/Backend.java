package codes.nh.tvratings;

import codes.nh.tvratings.configuration.Configuration;
import codes.nh.tvratings.database.ImdbDatabase;
import codes.nh.tvratings.database.ImdbDatabaseUpdater;
import codes.nh.tvratings.database.UserDatabase;
import codes.nh.tvratings.mail.MailManager;
import codes.nh.tvratings.server.APIServer;
import codes.nh.tvratings.utils.Utils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is the central point of the application and
 * responsible for loading the configuration, instantiating the MailManager,
 * connecting to the user and IMDb database and starting the API server.
 */
public class Backend {

    private final File configurationFile = new File("configuration.json");

    private final Configuration configuration = new Configuration(configurationFile);

    private MailManager mailManager;

    private APIServer server;

    public void start() {

        try {
            configuration.createIfNotExists();
            configuration.load();
        } catch (Exception e) {
            Utils.log("error while creating or loading the configuration: " + e.getMessage());
        }

        mailManager = new MailManager(
                configuration.smtpHost,
                configuration.smtpPort,
                configuration.smtpAuth,
                configuration.smtpStartTLS,
                configuration.emailUsername,
                configuration.emailPassword,
                configuration.emailFrom
        );

        try {

            UserDatabase userDatabase = new UserDatabase(getUserDatabaseFile().getPath());
            userDatabase.connect();

            ImdbDatabaseUpdater imdbDatabaseUpdater = new ImdbDatabaseUpdater(configuration);
            imdbDatabaseUpdater.setDatabaseUpdateListener(newImdbDatabase -> {

                ImdbDatabase oldImdbDatabase = server.getImdbDatabase();
                server.setImdbDatabase(newImdbDatabase);

                try {
                    oldImdbDatabase.disconnect();
                } catch (Exception e) {
                    Utils.log("error while disconnecting from old imdb database: " + e.getMessage());
                }

                notifyNewEpisodes(server.getUserDatabase(), newImdbDatabase, oldImdbDatabase);

            });

            ImdbDatabase imdbDatabase = imdbDatabaseUpdater.getImdbDatabaseAndCheckForUpdates();

            server = new APIServer(configuration, mailManager, imdbDatabase, userDatabase);
            server.start();

        } catch (Exception e) {
            Utils.log("error while loading the databases: " + e.getMessage());
        }
    }

    private File getUserDatabaseFile() {
        return new File(configuration.databaseDirectory, "users" + configuration.databaseFileExtension);
    }


    //email notifications

    record Show(String id, String title) {
    }

    /**
     * Notify users, who follow shows that have new episodes, via email.
     *
     * @param userDatabase    The user database.
     * @param newImdbDatabase The new IMDb database.
     * @param oldImdbDatabase The old IMDb database.
     */
    private void notifyNewEpisodes(UserDatabase userDatabase, ImdbDatabase newImdbDatabase, ImdbDatabase oldImdbDatabase) {
        try {

            long startTime = System.currentTimeMillis();

            HashMap<String, List<Show>> emailShowsMap = new HashMap<>();
            JSONArray userShowsJson = userDatabase.getUsersFollowingShowsWithNewEpisodes(newImdbDatabase.getDatabasePath(), oldImdbDatabase.getDatabasePath());
            for (int i = 0; i < userShowsJson.length(); i++) {
                JSONObject userShowJson = userShowsJson.getJSONObject(i);
                String email = userShowJson.getString("email");
                Show show = new Show(userShowJson.getString("showId"), userShowJson.getString("title"));
                List<Show> emailShows = emailShowsMap.computeIfAbsent(email, k -> new ArrayList<>());
                emailShows.add(show);
            }

            String subject = "new episodes available";
            emailShowsMap.forEach((email, shows) -> {

                String emailContent = "<html><h3>shows you follow have new episodes: </h3><ul>";
                emailContent += shows.stream().map(
                        (show) -> "<li><a href='https://tvratin.gs?showId=%s'><h4>%s</h4></a></li>".formatted(show.id, show.title)
                ).collect(Collectors.joining());
                emailContent += "</ul></html>";

                try {
                    mailManager.sendMail(email, subject, emailContent);
                } catch (Exception e) {
                    Utils.log("error while sending email to " + email + ": " + e.getMessage());
                }

            });

            long time = System.currentTimeMillis() - startTime;
            Utils.log("notifyNewEpisodes took " + time + " ms");

        } catch (SQLException e) {
            Utils.log("error while notifying users about new episodes");
        }
    }

}
