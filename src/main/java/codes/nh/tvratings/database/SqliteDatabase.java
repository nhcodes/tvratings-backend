package codes.nh.tvratings.database;

import codes.nh.tvratings.utils.Utils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.util.List;

/**
 * this class allows to manage sqlite database connections.
 * {@link #connect()} has to be called first
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
     * @return the previously established connection,
     * or null if {@link #connect()} wasn't or {@link #disconnect()} was called beforehand
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * establishes a new connection
     *
     * @throws Exception if already connected
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
     * closes a previously established connection
     *
     * @throws Exception if not connected
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

    protected int execute(String query) throws SQLException {
        return execute(query, List.of());
    }

    //TODO

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

    protected JSONArray queryAndConvertToJson(String query) throws SQLException {
        return queryAndConvertToJson(query, List.of());
    }

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
    //TODO

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