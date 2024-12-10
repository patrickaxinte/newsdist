package server.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static volatile Logger instance;
    private final PrintWriter writer;
    private static final String DEFAULT_TIMEZONE = "Europe/Bucharest";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Logger() throws IOException {
        String logFilePath = ConfigManager.getInstance().getLogFilePath();
        writer = new PrintWriter(new FileWriter(logFilePath, true), true);
    }

    public static Logger getInstance() {
        if (instance == null) {
            synchronized (Logger.class) {
                if (instance == null) {
                    try {
                        instance = new Logger();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to initialize Logger: " + e.getMessage());
                    }
                }
            }
        }
        return instance;
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).format(FORMATTER);
    }

    public synchronized void log(String message) {
        writer.println(getCurrentTimestamp() + " [INFO] " + message);
        writer.flush();
    }

    public synchronized void error(String message) {
        writer.println(getCurrentTimestamp() + " [ERROR] " + message);
        writer.flush();
    }
}

//package server.utils;
//
//import java.io.FileWriter;
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
//
//public class Logger {
//    private static volatile Logger instance;
//    private final PrintWriter writer;
//    private final String timezone;
//    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//
//    private Logger() throws IOException {
//        String logFilePath = ConfigManager.getInstance().getLogFilePath();
//        this.timezone = ConfigManager.getInstance().getTimezone();
//        writer = new PrintWriter(new FileWriter(logFilePath, true), true);
//    }
//
//    public static Logger getInstance() {
//        if (instance == null) {
//            synchronized (Logger.class) {
//                if (instance == null) {
//                    try {
//                        instance = new Logger();
//                    } catch (IOException e) {
//                        throw new RuntimeException("Failed to initialize Logger: " + e.getMessage());
//                    }
//                }
//            }
//        }
//        return instance;
//    }
//
//    private String getCurrentTimestamp() {
//        return LocalDateTime.now(ZoneId.of(timezone)).format(FORMATTER);
//    }
//
//    public synchronized void log(String message) {
//        writer.println(getCurrentTimestamp() + " [INFO] " + message);
//        writer.flush();
//    }
//
//    public synchronized void error(String message) {
//        writer.println(getCurrentTimestamp() + " [ERROR] " + message);
//        writer.flush();
//    }
//
//    public synchronized void warn(String message) {
//        writer.println(getCurrentTimestamp() + " [WARN] " + message);
//        writer.flush();
//    }
//}

//package server.utils;
//
//import java.io.FileWriter;
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
//
//public class Logger {
//    private static volatile Logger instance;
//    private final PrintWriter writer;
//    private static final String DEFAULT_TIMEZONE = "Europe/Bucharest";
//    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//
//    private Logger() throws IOException {
//        String logFilePath = ConfigManager.getInstance().getLogFilePath();
//        writer = new PrintWriter(new FileWriter(logFilePath, true), true);
//    }
//
//    public static Logger getInstance() {
//        if (instance == null) {
//            synchronized (Logger.class) {
//                if (instance == null) {
//                    try {
//                        instance = new Logger();
//                    } catch (IOException e) {
//                        throw new RuntimeException("Failed to initialize Logger: " + e.getMessage());
//                    }
//                }
//            }
//        }
//        return instance;
//    }
//
//    private String getCurrentTimestamp() {
//        return LocalDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).format(FORMATTER);
//    }
//
//    public synchronized void log(String message) {
//        writer.println(getCurrentTimestamp() + " [INFO] " + message);
//        writer.flush();
//    }
//
//    public synchronized void error(String message) {
//        writer.println(getCurrentTimestamp() + " [ERROR] " + message);
//        writer.flush();
//    }
//}

