package codes.nh.tvratings.database;

import codes.nh.tvratings.utils.Utils;
import org.json.JSONArray;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * the imdb database contains tv show data and the necessary queries
 */
public class ImdbDatabase extends SqliteDatabase {

    public ImdbDatabase(String databaseFileName) {
        super(databaseFileName);
    }

    /*
    select genres as ordered, comma seperated string
    should be ordered by default, if not:
    , (SELECT GROUP_CONCAT(genre) FROM (SELECT g.genre FROM genres g WHERE t.showId = g.showId ORDER BY g.genre)) AS genres
    */
    private final String genresQuery = "(SELECT GROUP_CONCAT(genre) FROM genres g WHERE t.showId = g.showId) AS genres";

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

        sqlQueryBuilder.append(", ").append(genresQuery);

        //table

        String[] types = {"shows", "episodes"};
        String tableName = Arrays.stream(types).filter(e -> e.equalsIgnoreCase(type)).findFirst().orElse(types[0]);
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

            //genres are already sorted alphabetically in db
            String[] genreArray = genres.split(",");
            Arrays.sort(genreArray);
            conditionValues.add("%" + String.join("%", genreArray) + "%");
        }

        if (!conditions.isEmpty()) {
            sqlQueryBuilder.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        //order by

        String[] sortColumns = {"votes", "rating", "startYear", "title"};
        String finalSortColumn = Arrays.stream(sortColumns).filter(e -> e.equalsIgnoreCase(sortColumn)).findFirst().orElse(sortColumns[0]);
        sqlQueryBuilder.append(" ORDER BY ").append(finalSortColumn);

        String[] sortOrders = {"DESC", "ASC"};
        String finalSortOrder = Arrays.stream(sortOrders).filter(e -> e.equalsIgnoreCase(sortOrder)).findFirst().orElse(sortOrders[0]);
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
        String showQuery = "SELECT *, " + genresQuery + " FROM shows t WHERE showId = ? ORDER BY votes DESC LIMIT 1";
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
        String attach = "ATTACH DATABASE ? as old";
        String detach = "DETACH DATABASE old";
        String newShowsQuery = "SELECT * FROM shows WHERE showId NOT IN (SELECT o.showId FROM old.shows o) ORDER BY votes DESC";

        execute(attach, List.of(oldDatabasePath));
        JSONArray shows = queryAndConvertToJson(newShowsQuery);
        execute(detach);
        return shows;
    }

    public JSONArray getShowsWithNewEpisodes(String oldDatabasePath) throws SQLException {
        String attach = "ATTACH DATABASE ? as old";
        String detach = "DETACH DATABASE old";
        //String newEpisodesQuery = "SELECT * FROM episodes WHERE episodeId NOT IN (SELECT o.episodeId FROM old.episodes o) ORDER BY votes DESC";
        String newEpisodesQuery = "SELECT DISTINCT n.showId FROM episodes n FULL JOIN old.episodes o ON n.episodeId = o.episodeId WHERE n.votes IS NOT NULL AND o.votes IS NULL";
        execute(attach, List.of(oldDatabasePath));
        JSONArray shows = queryAndConvertToJson(newEpisodesQuery);
        execute(detach);
        return shows;
    }

}