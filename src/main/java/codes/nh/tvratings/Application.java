package codes.nh.tvratings;

import codes.nh.tvratings.configuration.Configuration;
import codes.nh.tvratings.database.ImdbDatabase;
import codes.nh.tvratings.database.ImdbDatasetsImporter;
import codes.nh.tvratings.database.UserDatabase;
import codes.nh.tvratings.mail.MailManager;
import codes.nh.tvratings.server.ImdbServer;
import codes.nh.tvratings.utils.Utils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * this class is the starting point of the application
 */
public class Application {

    /* ===[TODO]===
    -
    */

    private static final File configurationFile = new File("configuration.json");

    public static Configuration configuration = new Configuration(configurationFile);

    public static MailManager mailManager;

    private static final String databaseFileNameSuffix = ".sqlite3";

    private static final String databaseDirectory = "databases";

    private static final File imdbDatabaseDirectory = new File(databaseDirectory, "imdb");

    private static final File userDatabaseFile = new File(databaseDirectory, "users" + databaseFileNameSuffix);

    private static ImdbServer server;

    private static String lastUpdate;

    private static UserDatabase userDatabase;

    private static final boolean doUpdate = true;

    public static void main(String[] args) {
        System.out.println("application started");
        Utils.log("application started");

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

        Utils.doAsync(() -> {
            handleConsole();
        });

        Utils.doAsync(() -> {
            try {
                updateDatabaseAndStartServer();
            } catch (Exception e) {
                Utils.log("error while starting application: " + e.getMessage());
            }
        });

        //Utils.log("application finished");
    }

    /**
     * looks for old database files:<br>
     * - if there are none, imports new datasets first and then starts the server<br>
     * - if there is an outdated one, starts the server and imports new datasets in the background<br>
     * - if there is one that is uptodate, starts the server<br>
     * afterwards an update checker is started too
     *
     * @throws Exception if something goes wrong while connecting, importing or starting
     */
    private static void updateDatabaseAndStartServer() throws Exception {

        imdbDatabaseDirectory.mkdirs();

        String oldImdbDatabaseFileName = Arrays.stream(imdbDatabaseDirectory.list())
                .filter(name -> name.endsWith(databaseFileNameSuffix))
                .max(Comparator.naturalOrder())
                .orElse(null);

        String dateString = Utils.getDateString();
        lastUpdate = dateString;

        String newImdbDatabaseFileName = dateString + databaseFileNameSuffix;
        File newImdbDatabaseFile = new File(imdbDatabaseDirectory, newImdbDatabaseFileName);

        ImdbDatabase imdbDatabase;
        if (oldImdbDatabaseFileName == null) {
            Utils.log("no databases -> import first and then start server");

            imdbDatabase = new ImdbDatabase(newImdbDatabaseFile.getPath());
            imdbDatabase.connect();

            ImdbDatasetsImporter datasetsImporter = new ImdbDatasetsImporter(imdbDatabase.getConnection());
            datasetsImporter.start();

        } else if (!oldImdbDatabaseFileName.equals(newImdbDatabaseFileName)) {
            Utils.log("database is not uptodate -> start server and import in background");

            File oldImdbDatabaseFile = new File(imdbDatabaseDirectory, oldImdbDatabaseFileName);

            imdbDatabase = new ImdbDatabase(oldImdbDatabaseFile.getPath());
            imdbDatabase.connect();

            if (doUpdate) {
                updateDatabaseInBackground(newImdbDatabaseFile, oldImdbDatabaseFile.getPath());
            } else {
                Utils.log("doUpdate=false");
            }

        } else {
            Utils.log("database is uptodate -> start server");

            imdbDatabase = new ImdbDatabase(newImdbDatabaseFile.getPath());
            imdbDatabase.connect();

        }

        userDatabase = new UserDatabase(userDatabaseFile.getPath());
        userDatabase.connect();

        server = new ImdbServer(configuration.serverPort, imdbDatabase, userDatabase);
        server.start();

        //after 5 minutes, check every minute if there's a new day, and if there is, update
        checkForUpdatesRepeatedly(5 * 60, 60);

    }

    private static void updateDatabaseInBackground(File newImdbDatabaseFile, String oldImdbDatabasePath) {
        Utils.doAsync(() -> {
            try {

                ImdbDatabase updatedImdbDatabase = new ImdbDatabase(newImdbDatabaseFile.getPath());
                updatedImdbDatabase.connect();

                ImdbDatasetsImporter datasetsImporter = new ImdbDatasetsImporter(updatedImdbDatabase.getConnection());
                datasetsImporter.start();

                server.setImdbDatabase(updatedImdbDatabase);
                Utils.log("finished updating in background");

                Utils.doAsync(() -> {
                    try {
                        notifyNewEpisodes(updatedImdbDatabase, oldImdbDatabasePath, userDatabaseFile.getPath());
                    } catch (SQLException e) {
                        Utils.log("error while notifying new episodes: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                Utils.log("error updating in background: " + e.getMessage());
            }
        });
    }

    /**
     * checks every {@code intervalSeconds} if there is a new utc day and if there is,
     * updates the database in background
     *
     * @param initialDelaySeconds the initial delay in seconds before the first check happens
     * @param intervalSeconds     the interval in seconds between checks
     */
    private static void checkForUpdatesRepeatedly(long initialDelaySeconds, long intervalSeconds) {
        Utils.repeatAsync(() -> {

            String dateString = Utils.getDateString();
            if (lastUpdate.equals(dateString)) {
                return;
            }

            Utils.log("new day (" + lastUpdate + " -> " + dateString + ") -> updating now");

            lastUpdate = dateString;

            String newDatabaseFileName = dateString + databaseFileNameSuffix;
            File newDatabaseFile = new File(imdbDatabaseDirectory, newDatabaseFileName);

            String oldImdbDatabasePath = server.getImdbDatabase().getDatabasePath();

            updateDatabaseInBackground(newDatabaseFile, oldImdbDatabasePath);

        }, initialDelaySeconds, intervalSeconds);
    }

    record Show(String id, String title) {
    }

    /**
     * Notify users who follow shows that have new episodes via email.
     *
     * @param newImdbDatabase     The new IMDb database.
     * @param oldImdbDatabasePath The old IMDb database.
     * @param userDatabasePath    The user database.
     */
    private static void notifyNewEpisodes(ImdbDatabase newImdbDatabase, String oldImdbDatabasePath, String userDatabasePath) throws SQLException {
        long startTime = System.currentTimeMillis();

        HashMap<String, List<Show>> emailShowsMap = new HashMap<>();
        JSONArray userShowsJson = newImdbDatabase.getUsersFollowingShowsWithNewEpisodes(oldImdbDatabasePath, userDatabasePath);
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
    }

    /**
     * listen for console commands.
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

}
