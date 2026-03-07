package com.lrenyi.template.core.util;

public class FileUtil {
    
    private FileUtil() {
        throw new IllegalStateException("Utility class");
    }
    
    public static boolean isResourceFileNotExists(String resourceFilename) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader.getResource(resourceFilename) == null;
    }
}
