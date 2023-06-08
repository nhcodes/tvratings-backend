package codes.nh.tvratings.database;

import codes.nh.tvratings.utils.Utils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.util.List;

/**
 * This class provides methods manage SQLite database connections,
 * execute SQL updates and queries, and convert query results to JSON format.
 * {@link #connect()} has to be called first.
 */
public abstract class SqliteDatabase {

    private final String databaseDriver = "org.sqlite.JDBC";

    private final String databaseUrl = "jdbc:sqlite:";

    private final String databasePath;

    private Connection connection;

    public SqliteDatabase(String databasePath) {
        this.databasePath = databasePath;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    /**
     * @return The previously established connection,
     * or null if {@link #connect()} wasn't or {@link #disconnect()} was called beforehand.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Establishes a new connection.
     *
     * @throws Exception If already connected or a database error occurs.
     */
    public void connect() throws Exception {
        Utils.log("connecting to database");

        if (connection != null) {
            throw new Exception("already connected");
        }

        Class.forName(databaseDriver);
        connection = DriverManager.getConnection(databaseUrl + databasePath);

        Utils.log("connected to database");
    }

    /**
     * Closes a previously established connection.
     *
     * @throws Exception If not connected or a database error occurs.
     */
    public void disconnect() throws Exception {
        Utils.log("disconnecting from database");

        if (connection == null) {
            throw new Exception("not connected");
        }

        connection.close();
        connection = null;

        Utils.log("disconnected from database");
    }

    /**
     * Executes a database statement.
     *
     * @param query The update statement.
     * @return The row count or 0.
     * @throws SQLException
     */
    protected int execute(String query) throws SQLException {
        return execute(query, List.of());
    }

    /**
     * Executes a database update statement.
     *
     * @param query  The update statement.
     * @param values The placeholder values.
     * @return The row count or 0.
     * @throws SQLException If a database error occurs.
     */
    protected int execute(String query, List<String> values) throws SQLException {
        try (PreparedStatement statement = getConnection().prepareStatement(query);) {
            int i = 0;
            for (String conditionValue : values) {
                i++;
                statement.setString(i, conditionValue);
            }
            return statement.executeUpdate();
        }
    }

    /**
     * Executes a database query statement and returns the result in JSON format.
     *
     * @param query The query statement.
     * @return The result in JSON format.
     * @throws SQLException If a database error occurs.
     */
    protected JSONArray queryAndConvertToJson(String query) throws SQLException {
        return queryAndConvertToJson(query, List.of());
    }

    /**
     * Executes a database query statement and returns the result in JSON format.
     *
     * @param query  The query statement.
     * @param values The placeholder values.
     * @return The result in JSON format.
     * @throws SQLException If a database error occurs.
     */
    protected JSONArray queryAndConvertToJson(String query, List<String> values) throws SQLException {
        try (PreparedStatement statement = getConnection().prepareStatement(query);) {
            int i = 0;
            for (String conditionValue : values) {
                i++;
                statement.setString(i, conditionValue);
            }
            ResultSet resultSet = statement.executeQuery();
            return resultSetToJson(resultSet);
        }
    }

    private JSONArray resultSetToJson(ResultSet resultSet) throws SQLException {
        JSONArray array = new JSONArray();
        while (resultSet.next()) {
            JSONObject object = new JSONObject();
            ResultSetMetaData metaData = resultSet.getMetaData();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                object.put(metaData.getColumnName(i), resultSet.getObject(i));
            }
            array.put(object);
        }
        return array;
    }

}