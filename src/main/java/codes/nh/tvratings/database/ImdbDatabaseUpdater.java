package codes.nh.tvratings.database;

import codes.nh.tvratings.Application;
import codes.nh.tvratings.utils.Utils;

import java.io.File;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;

public class ImdbDatabaseUpdater {

    private static final boolean DO_UPDATES = true;

    private final File imdbDatabaseDirectory = new File(Application.databaseDirectory, "imdb");

    private File getOldImdbDatabaseFile() {
        return Arrays.stream(imdbDatabaseDirectory.listFiles())
                .filter(file -> file.getName().endsWith(Application.databaseFileNameSuffix))
                .max(Comparator.comparing(file -> file.getName()))
                .orElse(null);
    }

    private File getNewImdbDatabaseFile() {
        String dateString = Utils.getDateString();
        String fileName = dateString + Application.databaseFileNameSuffix;
        return new File(imdbDatabaseDirectory, fileName);
    }

    /**
     * Looks for old database files:<br>
     * - If there are none, downloads a new database and then returns it.<br>
     * - If there is an outdated one, returns it and downloads a new database in the background.<br>
     * - If there is one that is uptodate, returns it.<br>
     * Afterwards a task is started that updates the database every day at midnight (UTC time).
     *
     * @return An IMDb Database.
     * @throws Exception If something goes wrong while connecting to the database, or downloading a new one.
     */
    public ImdbDatabase getImdbDatabaseAndCheckForUpdates() throws Exception {

        imdbDatabaseDirectory.mkdirs();

        File oldImdbDatabaseFile = getOldImdbDatabaseFile();
        File newImdbDatabaseFile = getNewImdbDatabaseFile();

        ImdbDatabase imdbDatabase;
        if (oldImdbDatabaseFile == null) {
            Utils.log("no databases -> download first and then start server");

            imdbDatabase = new ImdbDatabase(newImdbDatabaseFile.getPath());
            imdbDatabase.connect();

            ImdbDatasetsImporter datasetsImporter = new ImdbDatasetsImporter(imdbDatabase.getConnection());
            datasetsImporter.start();

        } else if (!oldImdbDatabaseFile.getName().equals(newImdbDatabaseFile.getName())) {
            Utils.log("database is not uptodate -> start server and download in background");

            imdbDatabase = new ImdbDatabase(oldImdbDatabaseFile.getPath());
            imdbDatabase.connect();

            if (DO_UPDATES) {
                Utils.doAsync(() -> {
                    updateDatabase(newImdbDatabaseFile);
                });
            } else {
                Utils.log("doUpdate=false");
            }

        } else {
            Utils.log("database is uptodate -> start server");

            imdbDatabase = new ImdbDatabase(newImdbDatabaseFile.getPath());
            imdbDatabase.connect();

        }

        if (DO_UPDATES) {
            startDailyUpdater();
        }

        return imdbDatabase;
    }

    private void startDailyUpdater() {
        long dayMs = 24 * 60 * 60 * 1000;
        long msUntilNextUtcDay = dayMs - System.currentTimeMillis() % dayMs;

        Utils.doAsync(() -> {

            File newDatabaseFile = getNewImdbDatabaseFile();
            updateDatabase(newDatabaseFile);

            startDailyUpdater();

        }, msUntilNextUtcDay + 60 * 1000L); //00:01 UTC time
    }

    private void updateDatabase(File newImdbDatabaseFile) {
        try {
            Utils.log("updating database..");

            ImdbDatabase newImdbDatabase = new ImdbDatabase(newImdbDatabaseFile.getPath());
            newImdbDatabase.connect();

            ImdbDatasetsImporter datasetsImporter = new ImdbDatasetsImporter(newImdbDatabase.getConnection());
            datasetsImporter.start();

            Utils.log("finished updating database");

            databaseUpdateListener.onUpdate(newImdbDatabase);

        } catch (Exception e) {
            Utils.log("error while updating imdb database: " + e.getMessage());
        }
    }

    //listener

    private Listener databaseUpdateListener = (newImdbDatabase) -> {
    };

    public void setDatabaseUpdateListener(Listener databaseUpdateListener) {
        this.databaseUpdateListener = databaseUpdateListener;
    }

    public interface Listener {
        void onUpdate(ImdbDatabase newImdbDatabase) throws SQLException;
    }

}
