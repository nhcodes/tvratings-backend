package codes.nh.tvratings;

import codes.nh.tvratings.utils.Utils;

/**
 * This class is the starting point of the application.
 */
public class Application {

    public static void main(String[] args) {
        Utils.log("application started");

        Utils.doAsync(() -> listenForConsoleCommands());

        Utils.doAsync(() -> new Backend().start());
    }

    private static void listenForConsoleCommands() {
        Utils.log("");
        Utils.log("===[Commands]===");
        Utils.log("- test | This is a test");
        Utils.log("- exit | Stops the application");
        Utils.log("================");
        Utils.log("");

        Utils.listenForConsoleCommands(command -> {

            if (command.equalsIgnoreCase("test")) {
                Utils.log("ok test");
            } else if (command.equalsIgnoreCase("exit")) {
                Utils.log("shutting down...");
                System.exit(0);
            } else {
                Utils.log("command '" + command + "' not found");
            }

        });
    }

}
