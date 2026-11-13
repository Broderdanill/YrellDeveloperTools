package se.yrell.developertools;
/** Isolated test log stub; never packaged into the production plugin JAR. */
public final class Log {
    public static void info(String message) { System.out.println("[test] " + message); }
    public static void warn(String message) { System.out.println("[test warning] " + message); }
    public static void error(String message, Throwable error) { throw new AssertionError(message, error); }
}
