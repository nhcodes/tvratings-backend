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
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is the starting point of the application.
 */
public class Application {

    private static final File configurationFile = new File("configuration.json");

    public static final Configuration configuration = new Configuration(configurationFile);

    public static final String databaseFileNameSuffix = ".sqlite3";

    public static final String databaseDirectory = "databases";

    public static MailManager mailManager;

    private static APIServer server;

    public static void main(String[] args) {
        Utils.log("application started");

        Utils.doAsync(() -> handleConsole());

        Utils.doAsync(() -> startApplication());
    }

    private static void startApplication() {

        try {
            configuration.createIfNotExists();
            configuration.load();
        } catch (IOException e) {
            Utils.log("error while creating or loading configuration: " + e.getMessage());
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

            ImdbDatabaseUpdater imdbDatabaseUpdater = new ImdbDatabaseUpdater();
            imdbDatabaseUpdater.setDatabaseUpdateListener(newImdbDatabase -> {

                ImdbDatabase oldImdbDatabase = server.getImdbDatabase();
                server.setImdbDatabase(newImdbDatabase);

                try {
                    oldImdbDatabase.disconnect();
                } catch (Exception e) {
                    Utils.log("error while disconnecting from old imdb database: " + e.getMessage());
                }

                notifyNewEpisodes(newImdbDatabase, oldImdbDatabase, getUserDatabaseFile().getPath());

            });

            ImdbDatabase imdbDatabase = imdbDatabaseUpdater.getImdbDatabaseAndCheckForUpdates();

            UserDatabase userDatabase = new UserDatabase(getUserDatabaseFile().getPath());
            userDatabase.connect();

            server = new APIServer(configuration.serverPort, imdbDatabase, userDatabase);
            server.start();

        } catch (Exception e) {
            Utils.log("error while starting application: " + e.getMessage());
        }
    }

    private static File getUserDatabaseFile() {
        return new File(databaseDirectory, "users" + databaseFileNameSuffix);
    }

    /**
     * Listen for console commands.
     */
    private static void handleConsole() {
        Utils.log("");
        Utils.log("===[Commands]===");
        Utils.log("- test | This is a test");
        Utils.log("- exit | Stops the application");
        Utils.log("================");
        Utils.log("");

        Utils.listenForConsoleCommands(command -> {

            if (command.equalsIgnoreCase("test")) {
                Utils.log("ok test");
            } else if (command.equalsIgnoreCase("exit")) {
                Utils.log("shutting down...");
                System.exit(0);
            } else {
                Utils.log("command '" + command + "' not found");
            }

        });
    }

    record Show(String id, String title) {
    }

    /**
     * Notify users who follow shows that have new episodes via email.
     *
     * @param newImdbDatabase  The new IMDb database.
     * @param oldImdbDatabase  The old IMDb database.
     * @param userDatabasePath The user database.
     */
    private static void notifyNewEpisodes(ImdbDatabase newImdbDatabase, ImdbDatabase oldImdbDatabase, String userDatabasePath) {
        try {

            long startTime = System.currentTimeMillis();

            HashMap<String, List<Show>> emailShowsMap = new HashMap<>();
            JSONArray userShowsJson = newImdbDatabase.getUsersFollowingShowsWithNewEpisodes(oldImdbDatabase.getDatabasePath(), userDatabasePath);
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
                    Application.mailManager.sendMail(email, subject, emailContent);
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
