package codes.nh.tvratings.utils;

import java.util.Scanner;
import java.util.function.Consumer;

public class ConsoleManager {

    public void start() {
        Utils.log("");
        Utils.log("===[Commands]===");
        Utils.log("- test | This is a test");
        Utils.log("- exit | Stops the application");
        Utils.log("================");
        Utils.log("");

        listenForConsoleCommands(command -> {

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

    /**
     * Listens for new console messages. This method is blocking.
     *
     * @param listener Gets called when there is a new message.
     */
    private void listenForConsoleCommands(Consumer<String> listener) {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String command = scanner.nextLine();
            listener.accept(command);
        }
    }

}
