package codes.nh.tvratings.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This class contains several utilities.
 */
public class Utils {

    //==========[LOG]==========

    private static final Logger logger = LoggerFactory.getLogger("app"); //todo

    private static final String logTag = "[tvratings] ";

    public static void log(String message) {
        System.out.println(logTag + message);
        logger.info(logTag + message);
    }

    //==========[ASYNC]==========

    private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

    /**
     * Executes a function in a new thread.
     *
     * @param function The function to execute asynchronously.
     */
    public static Future<?> doAsync(Runnable function) {
        return THREAD_POOL.submit(function);
    }

    /**
     * Executes a function in a new thread.
     *
     * @param function The function to execute asynchronously.
     * @param delayMs  The delay in milliseconds.
     */
    public static Future<?> doAsync(Runnable function, long delayMs) {
        return Executors.newSingleThreadScheduledExecutor().schedule(function, delayMs, TimeUnit.MILLISECONDS);
    }

    //==========[OTHER]==========

    public static Integer stringToIntOrNull(String string) {
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Boolean stringToBooleanOrNull(String string) {
        if (string == null) return null;
        if (string.equalsIgnoreCase("true")) return true;
        else if (string.equalsIgnoreCase("false")) return false;
        else return null;
    }

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * @return The current UTC date in the format "yyyyMMdd".
     */
    public static String getDateString() {
        ZonedDateTime utcTime = ZonedDateTime.now(ZoneOffset.UTC);
        return utcTime.format(DATE_FORMAT);
    }

}
