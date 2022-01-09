package io.san.server;

public class SystemUtil {

    private static final String os = System.getProperty("os.name").toLowerCase();


    public static boolean isLinux(){
        return !os.contains("windows");
    }
}
