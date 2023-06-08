package codes.nh.tvratings.database;

import codes.nh.tvratings.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * This class is responsible for downloading and importing the necessary IMDb datasets into a SQL database.
 */
public class ImdbDatasetsImporter {

    private final Connection databaseConnection;

    public ImdbDatasetsImporter(Connection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    private final String baseUrl = "https://datasets.imdbws.com/";

    /**
     * See https://developer.imdb.com/non-commercial-datasets/ and https://datasets.imdbws.com/.
     */
    private final String[] datasets = {
            "title.basics.tsv.gz",
            "title.episode.tsv.gz",
            "title.ratings.tsv.gz",
    };

    /**
     * Starts the import process.
     */
    public void start() throws IOException, SQLException {
        long startTime = System.currentTimeMillis();
        Utils.log("ImdbDatasetsImporter started");

        for (String dataset : datasets) {
            File datasetFile = downloadDataset(dataset);
            importDataset(datasetFile);
            Files.deleteIfExists(datasetFile.toPath());
        }
        optimizeTables();

        long time = System.currentTimeMillis() - startTime;
        Utils.log("ImdbDatasetsImporter finished in " + time + " ms");
    }

    /**
     * Downloads a dataset from IMDb and unzips it.
     *
     * @param datasetName The name of the dataset. Has to be one of {@link #datasets}.
     * @return The downloaded, unzipped dataset file.
     */
    private File downloadDataset(String datasetName) throws IOException {
        long startTime = System.currentTimeMillis();
        Utils.log("downloading " + datasetName + "..");

        URL url = new URL(baseUrl + datasetName);
        String outputFileName = datasetName.replace(".gz", "");
        File outputFile = new File(outputFileName);
        try (InputStream inputStream = url.openStream();
             GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);) {
            Files.copy(gzipInputStream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        long time = System.currentTimeMillis() - startTime;
        Utils.log("downloaded in " + time + " ms");

        return outputFile;
    }

    /**
     * Import a .tsv dataset file into the SQL database.
     *
     * @param datasetFile The dataset file to import. Has to be a .tsv file.
     */
    private void importDataset(File datasetFile) throws SQLException, IOException {
        long startTime = System.currentTimeMillis();
        Utils.log("importing " + datasetFile.getName() + "..");

        //title.basics.tsv -> title_basics
        String sqlTableName = datasetFile.getName()
                .replace(".tsv", "")
                .replace(".", "_");

        try (Stream<String> linesStream = Files.lines(datasetFile.toPath(), StandardCharsets.UTF_8);) {
            Iterator<String> iterator = linesStream.iterator();

            //first line in the .tsv file are the column names
            String[] columnNames = iterator.next().split("\t");

            //column1 TEXT, column2 TEXT, column3 TEXT, ...
            String sqlColumnString = Arrays.stream(columnNames)
                    .map(column -> column + " TEXT")
                    .collect(Collectors.joining(", "));

            databaseConnection.setAutoCommit(false);

            String createTableSql = String.format("CREATE TABLE %s (%s)", sqlTableName, sqlColumnString);
            try (Statement createTableStatement = databaseConnection.createStatement();) {
                createTableStatement.executeUpdate(createTableSql);
            }

            //?, ?, ?, ...
            String placeholders = String.join(", ", Collections.nCopies(columnNames.length, "?"));

            String insertValuesSql = String.format("INSERT INTO %s VALUES (%s)", sqlTableName, placeholders);
            try (PreparedStatement insertValuesStatement = databaseConnection.prepareStatement(insertValuesSql);) {
                while (iterator.hasNext()) {
                    String row = iterator.next();
                    String[] rowValues = row.split("\t");

                    int i = 0;
                    for (String value : rowValues) {
                        i++;

                        //replace \N (which denotes a missing value) by null
                        if (value.equals("\\N")) {
                            value = null;
                        }
                        insertValuesStatement.setString(i, value);
                    }

                    insertValuesStatement.executeUpdate();
                }
            }

        }

        databaseConnection.commit();
        databaseConnection.setAutoCommit(true);

        long time = System.currentTimeMillis() - startTime;
        Utils.log("imported in " + time + " ms");
    }

    /**
     * 1. Combines the downloaded tables into a "show" and an "episodes" table.
     * 2. Creates important indices to improve database performance.
     * 3. Deletes unnecessary shows/episodes (shows with no episodes or episodes not belonging to any show).
     * 4. Deletes the temporary tables.
     * 5. Creates a "genres" table to respect the 1NF.
     */
    private void optimizeTables() throws SQLException {
        long startTime = System.currentTimeMillis();
        Utils.log("optimizing tables..");

        try (Statement statement = databaseConnection.createStatement();) {

            //shows

            Utils.log("creating shows table..");
            String createShowsTableSql =
                    "CREATE TABLE shows (showId TEXT PRIMARY KEY, title TEXT, startYear INTEGER, endYear INTEGER, duration INTEGER, genres TEXT, rating REAL, votes INTEGER) STRICT";
            statement.executeUpdate(createShowsTableSql);

            Utils.log("inserting shows..");
            String insertShowsSql =
                    "INSERT INTO shows " +
                            "SELECT b.tconst, primaryTitle, startYear, endYear, runtimeMinutes, genres, averageRating, numVotes " +
                            "FROM title_basics b " +
                            "LEFT JOIN title_ratings r ON b.tconst = r.tconst " +
                            "WHERE titleType IN ('tvSeries', 'tvMiniSeries') " + //we only want tv shows
                            "AND numVotes IS NOT NULL " +
                            "ORDER BY CAST(numVotes AS INTEGER) DESC";
            statement.executeUpdate(insertShowsSql);

            Utils.log("creating shows(votes) index..");
            String createShowsVotesIndexSql = "CREATE INDEX showsVotesIndex ON shows(votes)";
            statement.executeUpdate(createShowsVotesIndexSql);

            //episodes

            Utils.log("creating episodes table..");
            String createEpisodesTableSql =
                    "CREATE TABLE episodes (episodeId TEXT PRIMARY KEY, showId TEXT, title TEXT, season INTEGER, episode INTEGER, startYear INTEGER, duration INTEGER, rating REAL, votes INTEGER) STRICT";
            statement.executeUpdate(createEpisodesTableSql);

            Utils.log("inserting episodes..");
            // || ' (' || (SELECT title FROM shows WHERE showId = parentTconst) || ')'
            String insertEpisodesSql =
                    "INSERT INTO episodes " +
                            "SELECT e.tconst, parentTconst, primaryTitle, seasonNumber, episodeNumber, startYear, runtimeMinutes, averageRating, numVotes " +
                            "FROM title_episode e " +
                            "LEFT JOIN title_ratings r ON e.tconst = r.tconst " +
                            "LEFT JOIN title_basics b ON e.tconst = b.tconst " +
                            "WHERE seasonNumber IS NOT NULL AND episodeNumber IS NOT NULL " +
                            "ORDER BY CAST(numVotes AS INTEGER) DESC";
            statement.executeUpdate(insertEpisodesSql);

            Utils.log("creating episodes(showId) index..");
            String createEpisodesShowIdIndexSql = "CREATE INDEX episodesShowIdIndex ON episodes(showId)";
            statement.executeUpdate(createEpisodesShowIdIndexSql);

            Utils.log("creating episodes(votes) index..");
            String createEpisodesVotesIndexSql = "CREATE INDEX episodesVotesIndex ON episodes(votes)";
            statement.executeUpdate(createEpisodesVotesIndexSql);

            //cleanup

            Utils.log("deleting shows with no episodes");
            String deleteShows = "DELETE FROM shows WHERE (SELECT COUNT(*) FROM episodes WHERE shows.showId = episodes.showId) = 0";
            statement.executeUpdate(deleteShows);

            Utils.log("deleting episodes with no show");
            String deleteEpisodes = "DELETE FROM episodes WHERE episodes.showId NOT IN (SELECT shows.showId FROM shows)";
            statement.executeUpdate(deleteEpisodes);

            Utils.log("deleting temporary tables..");
            String deleteEpisodesSql = "DROP TABLE title_episode";
            String deleteBasicsSql = "DROP TABLE title_basics";
            String deleteRatingsSql = "DROP TABLE title_ratings";
            statement.executeUpdate(deleteEpisodesSql);
            statement.executeUpdate(deleteBasicsSql);
            statement.executeUpdate(deleteRatingsSql);

            //genres

            Utils.log("creating genres table..");
            String createGenresTableSql = "CREATE TABLE genres (showId TEXT, genre TEXT) STRICT";
            statement.executeUpdate(createGenresTableSql);

            Utils.log("inserting genres..");
            //splits genres by comma and inserts into newly created genres table
            //https://stackoverflow.com/questions/24258878/how-to-split-comma-separated-values
            String insertGenresSql = "INSERT INTO genres WITH RECURSIVE split_genres(id, genre, next) AS (SELECT showId, '', genres || ',' FROM shows UNION ALL SELECT id, substr(next, 0, instr(next, ',')), substr(next, instr(next, ',') + 1) FROM split_genres WHERE next != '') SELECT id, genre FROM split_genres WHERE genre != ''";
            statement.executeUpdate(insertGenresSql);

            Utils.log("creating genres(showId) index..");
            String createGenresIndexSql = "CREATE INDEX genresIndex ON genres(showId)";
            statement.executeUpdate(createGenresIndexSql);

            Utils.log("deleting old genres column..");
            String deleteGenresColumn = "ALTER TABLE shows DROP genres";
            statement.executeUpdate(deleteGenresColumn);

        }

        long time = System.currentTimeMillis() - startTime;
        Utils.log("optimized tables in " + time + " ms");
    }
}
