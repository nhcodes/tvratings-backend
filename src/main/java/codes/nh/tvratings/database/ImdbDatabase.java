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

    public JSONArray getNewShows(String oldDatabasePath) throws SQLException {
        String attach = "ATTACH DATABASE ? AS old";
        String detach = "DETACH DATABASE old";
        String newShowsQuery = "SELECT n.* FROM shows n WHERE n.showId NOT IN (SELECT o.showId FROM old.shows o) ORDER BY n.votes DESC";
        execute(attach, List.of(oldDatabasePath));
        JSONArray shows = queryAndConvertToJson(newShowsQuery);
        execute(detach);
        return shows;
    }

    public JSONArray getUsersFollowingShowsWithNewEpisodes(String oldDatabasePath, String userDatabasePath) throws SQLException {

        /*
        old queries:

            doesn't work because episodes are often added to the database before they aired
            String newEpisodesQuery = "SELECT n.* FROM episodes n WHERE n.episodeId NOT IN (SELECT o.episodeId FROM old.episodes o) ORDER BY n.votes DESC";

            works but doesn't directly include followed shows
            String newEpisodesQuery = "SELECT DISTINCT n.showId, (SELECT s.title FROM shows s WHERE s.showId = n.showId) AS showTitle FROM episodes n LEFT JOIN old.episodes o ON n.episodeId = o.episodeId WHERE n.votes IS NOT NULL AND o.votes IS NULL ORDER BY n.votes DESC";
        */

        String attachOldDatabase = "ATTACH DATABASE ? AS old";
        String detachOldDatabase = "DETACH DATABASE old";

        String attachUserDatabase = "ATTACH DATABASE ? AS user";
        String detachUserDatabase = "DETACH DATABASE user";

        //when a new episode airs, voting is enabled (n.votes IS NOT NULL AND o.votes IS NULL)
        String newEpisodeQuery = "SELECT DISTINCT f.*, s.title FROM user.follows f LEFT JOIN shows s ON s.showId = f.showId LEFT JOIN episodes n ON f.showId = n.showId LEFT JOIN old.episodes o ON n.episodeId = o.episodeId WHERE n.votes IS NOT NULL AND o.votes IS NULL";

        execute(attachOldDatabase, List.of(oldDatabasePath));
        execute(attachUserDatabase, List.of(userDatabasePath));
        JSONArray shows = queryAndConvertToJson(newEpisodeQuery);
        execute(detachOldDatabase);
        execute(detachUserDatabase);
        return shows;
    }

    private String findElementInArray(String[] array, String element) {
        return Arrays.stream(array).filter(e -> e.equalsIgnoreCase(element)).findFirst().orElse(array[0]);
    }

}