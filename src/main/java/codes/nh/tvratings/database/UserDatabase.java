package codes.nh.tvratings.database;

import org.json.JSONArray;

import java.sql.SQLException;
import java.util.List;

/**
 * the user database contains login verification codes and the shows that users followed
 */
public class UserDatabase extends SqliteDatabase {

    public UserDatabase(String databaseFileName) {
        super(databaseFileName);
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

    public int follow(String email, String showId) throws SQLException {
        String followSql = "INSERT OR IGNORE INTO follows VALUES (?, ?)";
        return execute(followSql, List.of(email, showId));
    }

    public int unfollow(String email, String showId) throws SQLException {
        String unfollowSql = "DELETE FROM follows WHERE email = ? AND showId = ?";
        return execute(unfollowSql, List.of(email, showId));
    }

    public JSONArray getFollows(String email, String imdbDatabasePath) throws SQLException {
        String attach = "ATTACH DATABASE ? as imdb";
        String detach = "DETACH DATABASE imdb";
        String followsSql = "SELECT f.showId, (SELECT s.title FROM imdb.shows s WHERE s.showId = f.showId) as title FROM follows f WHERE f.email = ?";
        execute(attach, List.of(imdbDatabasePath));
        JSONArray follows = queryAndConvertToJson(followsSql, List.of(email));
        execute(detach);
        return follows;
    }

    public JSONArray getEmailsFollowingShow(String showId) throws SQLException {
        String emailsSql = "SELECT email FROM follows WHERE showId = ?";
        return queryAndConvertToJson(emailsSql, List.of(showId));
    }
}