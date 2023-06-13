package codes.nh.tvratings;

import codes.nh.tvratings.utils.ConsoleManager;
import codes.nh.tvratings.utils.Utils;

/**
 * This class is the starting point of the application.
 */
public class Application {

    public static void main(String[] args) {
        Utils.log("application started");

        Utils.doAsync(() -> new ConsoleManager().start());

        Utils.doAsync(() -> new Backend().start());
    }

}
