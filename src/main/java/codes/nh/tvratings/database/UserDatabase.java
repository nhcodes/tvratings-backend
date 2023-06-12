package codes.nh.tvratings.database;

import org.json.JSONArray;

import java.sql.SQLException;
import java.util.List;

/**
 * The user database contains login verification codes and followed shows.
 */
public class UserDatabase extends SqliteDatabase {

    public UserDatabase(String databasePath) {
        super(databasePath);
    }

    @Override
    public void connect() throws Exception {
        super.connect();

        createTables();
    }

    private void createTables() throws SQLException {
        String createCodesTableSql = "CREATE TABLE IF NOT EXISTS codes (email TEXT PRIMARY KEY, code TEXT)";
        String createFollowsTableSql = "CREATE TABLE IF NOT EXISTS follows (email TEXT, showId TEXT, PRIMARY KEY (email, showId))";
        execute(createCodesTableSql);
        execute(createFollowsTableSql);
    }

    //

    public int addVerificationCode(String email, String code) throws SQLException {
        String addSql = "INSERT OR REPLACE INTO codes VALUES (?, ?)";
        return execute(addSql, List.of(email, code));
    }

    public boolean checkVerificationCode(String email, String code) throws SQLException {
        String checkSql = "SELECT COUNT(*) AS count FROM codes WHERE email = ? AND code = ?";
        JSONArray matchesJson = queryAndConvertToJson(checkSql, List.of(email, code));
        return matchesJson.getJSONObject(0).getInt("count") > 0;
    }

    //

    public int followShow(String email, String showId) throws SQLException {
        String followSql = "INSERT OR IGNORE INTO follows VALUES (?, ?)";
        return execute(followSql, List.of(email, showId));
    }

    public int unfollowShow(String email, String showId) throws SQLException {
        String unfollowSql = "DELETE FROM follows WHERE email = ? AND showId = ?";
        return execute(unfollowSql, List.of(email, showId));
    }

    public JSONArray getFollowedShows(String email, String imdbDatabasePath) throws SQLException {
        String attach = "ATTACH DATABASE ? AS imdb";
        String detach = "DETACH DATABASE imdb";
        String followsSql = "SELECT f.showId, (SELECT s.title FROM imdb.shows s WHERE s.showId = f.showId) AS title FROM follows f WHERE f.email = ?";
        execute(attach, List.of(imdbDatabasePath));
        JSONArray followedShows = queryAndConvertToJson(followsSql, List.of(email));
        execute(detach);
        return followedShows;
    }

    public JSONArray getUsersFollowingShowsWithNewEpisodes(String newImdbDatabasePath, String oldImdbDatabasePath) throws SQLException {

        /*
        old queries:

            doesn't work because episodes are often added to the database before they aired
            String newEpisodesQuery = "SELECT n.* FROM episodes n WHERE n.episodeId NOT IN (SELECT o.episodeId FROM old.episodes o) ORDER BY n.votes DESC";

            works but doesn't directly include followed shows
            String newEpisodesQuery = "SELECT DISTINCT n.showId, (SELECT s.title FROM shows s WHERE s.showId = n.showId) AS showTitle FROM episodes n LEFT JOIN old.episodes o ON n.episodeId = o.episodeId WHERE n.votes IS NOT NULL AND o.votes IS NULL ORDER BY n.votes DESC";
        */

        String attachNewDatabase = "ATTACH DATABASE ? AS new";
        String detachNewDatabase = "DETACH DATABASE new";

        String attachOldDatabase = "ATTACH DATABASE ? AS old";
        String detachOldDatabase = "DETACH DATABASE old";

        //when a new episode airs, voting is enabled (n.votes IS NOT NULL AND o.votes IS NULL)
        String newEpisodeQuery = "SELECT DISTINCT f.*, s.title FROM follows f LEFT JOIN new.shows s ON s.showId = f.showId LEFT JOIN new.episodes n ON n.showId = f.showId LEFT JOIN old.episodes o ON o.episodeId = n.episodeId WHERE n.votes IS NOT NULL AND o.votes IS NULL";

        execute(attachNewDatabase, List.of(newImdbDatabasePath));
        execute(attachOldDatabase, List.of(oldImdbDatabasePath));
        JSONArray shows = queryAndConvertToJson(newEpisodeQuery);
        execute(detachNewDatabase);
        execute(detachOldDatabase);
        return shows;
    }

}