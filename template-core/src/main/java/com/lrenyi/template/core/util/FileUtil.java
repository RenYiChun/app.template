package com.lrenyi.template.core.util;

public class FileUtil {
    public static boolean isResourceFileNotExists(String resourceFilename) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader.getResource(resourceFilename) == null;
    }
}
