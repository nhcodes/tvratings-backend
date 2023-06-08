package codes.nh.tvratings.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * this class contains several utilities
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

    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    /**
     * executes a function in a new thread
     *
     * @param function the function to execute asynchronously
     */
    public static Future<?> doAsync(Runnable function) {
        return threadPool.submit(function);
    }

    private static final ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(1);

    /**
     * executes a function in a new thread repeatedly
     *
     * @param function            the function to execute asynchronously & repeatedly
     * @param initialDelaySeconds the initial delay in seconds before the first execution happens
     * @param intervalSeconds     the interval in seconds between the function executions
     */
    public static ScheduledFuture<?> repeatAsync(Runnable function, long initialDelaySeconds, long intervalSeconds) {
        return scheduledThreadPool.scheduleAtFixedRate(function, initialDelaySeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    //==========[OTHER]==========

    /**
     * listens for new console messages. this method is blocking
     *
     * @param listener gets called when there is a new message
     */
    public static void listenForConsoleCommands(Consumer<String> listener) {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String command = scanner.nextLine();
            listener.accept(command);
        }
    }

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

    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * @return the current utc date in the format yyyyMMdd
     */
    public static String getDateString() {
        ZonedDateTime utcTime = ZonedDateTime.now(ZoneOffset.UTC);
        return utcTime.format(dateFormat);
    }

}
