package ui;

/**
 * Native-package and executable-JAR entry point.
 *
 * <p>This class deliberately does not extend JavaFX Application. Starting through a plain
 * Java main class allows the bundled JavaFX runtime to be loaded correctly by jpackage and
 * by the shaded executable JAR.</p>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
