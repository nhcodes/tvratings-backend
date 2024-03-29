package codes.nh.tvratings.database;

import codes.nh.tvratings.utils.Utils;
import org.json.JSONArray;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The IMDb database contains TV show data.
 */
public class ImdbDatabase extends SqliteDatabase {

    public ImdbDatabase(String databasePath) {
        super(databasePath);
    }

    /*
    Select genres as a sorted, comma separated string. It should be sorted by default, if not:
    (SELECT GROUP_CONCAT(genre) FROM (SELECT g.genre FROM genres g WHERE t.showId = g.showId ORDER BY g.genre)) AS genres
    */
    private final String selectGenresQuery = "(SELECT GROUP_CONCAT(genre) FROM genres g WHERE t.showId = g.showId) AS genres";

    public JSONArray search(
            String type,
            String titleSearch,
            String minVotes,
            String maxVotes,
            String minRating,
            String maxRating,
            String minYear,
            String maxYear,
            String minDuration,
            String maxDuration,
            String genres,
            String sortColumn,
            String sortOrder,
            String pageNumber,
            String pageLimit
    ) throws SQLException {

        StringBuilder sqlQueryBuilder = new StringBuilder();
        sqlQueryBuilder.append("SELECT *");

        //genres

        sqlQueryBuilder.append(", ").append(selectGenresQuery);

        //table

        String[] types = {"shows", "episodes"};
        String tableName = findElementInArray(types, type);
        sqlQueryBuilder.append(" FROM ").append(tableName).append(" t");

        //conditions

        List<String> conditions = new ArrayList<>();
        List<String> conditionValues = new ArrayList<>();

        if (titleSearch != null) {
            conditions.add("title LIKE ?");

            //replace every non-alphanumeric character (regex \W) with the wildcard %
            //% represents zero, one, or multiple numbers or characters
            String likeString = "%" + titleSearch.replaceAll("\\W", "%") + "%";
            conditionValues.add(likeString);
        }

        conditions.add("votes IS NOT NULL");

        if (minVotes != null) {
            conditions.add("votes >= ?");
            conditionValues.add(minVotes);
        }

        if (maxVotes != null) {
            conditions.add("votes <= ?");
            conditionValues.add(maxVotes);
        }

        if (minRating != null) {
            conditions.add("rating >= ?");
            conditionValues.add(minRating);
        }

        if (maxRating != null) {
            conditions.add("rating <= ?");
            conditionValues.add(maxRating);
        }

        if (minYear != null) {
            conditions.add("startYear >= ?");
            conditionValues.add(minYear);
        }

        if (maxYear != null) {
            conditions.add("startYear <= ?");
            conditionValues.add(maxYear);
        }

        if (minDuration != null) {
            conditions.add("duration >= ?");
            conditionValues.add(minDuration);
        }

        if (maxDuration != null) {
            conditions.add("duration <= ?");
            conditionValues.add(maxDuration);
        }

        if (genres != null) {
            conditions.add("genres LIKE ?");

            //genres need to be sorted alphabetically for this to work
            String[] genreArray = genres.split(",");
            Arrays.sort(genreArray);
            conditionValues.add("%" + String.join("%", genreArray) + "%");
        }

        if (!conditions.isEmpty()) {
            sqlQueryBuilder.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        //order by

        String[] sortColumns = {"votes", "rating", "startYear", "title"};
        String finalSortColumn = findElementInArray(sortColumns, sortColumn);
        sqlQueryBuilder.append(" ORDER BY ").append(finalSortColumn);

        String[] sortOrders = {"DESC", "ASC"};
        String finalSortOrder = findElementInArray(sortOrders, sortOrder);
        sqlQueryBuilder.append(" ").append(finalSortOrder);

        //always sort by votes (if not already the case)
        if (!finalSortColumn.equals("votes")) {
            sqlQueryBuilder.append(", votes DESC");
        }

        //limit & offset

        Integer finalPageNumber = Utils.stringToIntOrNull(pageNumber);
        if (finalPageNumber == null || finalPageNumber < 0) {
            finalPageNumber = 0;
        }

        int maxPageLimit = 100;
        Integer finalPageLimit = Utils.stringToIntOrNull(pageLimit);
        if (finalPageLimit == null || finalPageLimit < 0 || finalPageLimit > maxPageLimit) {
            finalPageLimit = maxPageLimit;
        }

        sqlQueryBuilder.append(" LIMIT ").append(finalPageLimit).append(" OFFSET ").append(finalPageNumber * finalPageLimit);

        //execute query

        String query = sqlQueryBuilder.toString();
        String values = String.join(" | ", conditionValues);
        Utils.log(query + " (" + values + ")");

        return queryAndConvertToJson(query, conditionValues);
    }

    public JSONArray getShow(String showId) throws SQLException {
        String showQuery = "SELECT *, " + selectGenresQuery + " FROM shows t WHERE showId = ? ORDER BY votes DESC LIMIT 1";
        return queryAndConvertToJson(showQuery, List.of(showId));
    }

    public JSONArray getShowEpisodes(String showId) throws SQLException {
        String episodesQuery = "SELECT * FROM episodes WHERE showId = ? ORDER BY season, episode";
        return queryAndConvertToJson(episodesQuery, List.of(showId));
    }

    public JSONArray getGenres() throws SQLException {
        String genresQuery = "SELECT DISTINCT genre FROM genres ORDER BY genre";
        return queryAndConvertToJson(genresQuery);
    }

    private String findElementInArray(String[] array, String element) {
        return Arrays.stream(array).filter(e -> e.equalsIgnoreCase(element)).findFirst().orElse(array[0]);
    }

}