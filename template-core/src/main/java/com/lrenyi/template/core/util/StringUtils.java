package com.lrenyi.template.core.util;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * 字符串工具类
 *
 * @author htcctech
 */
public class StringUtils extends org.apache.commons.lang3.StringUtils {
    private static final String NULLPTR = "";
    private static final String START = "*";
    
    private StringUtils() {
    
    }
    
    public static boolean hasLength(@Nullable String str) {
        return (str != null && !str.isEmpty());
    }
    
    /**
     * 截取字符串
     *
     * @param str   字符串
     * @param start 开始
     *
     * @return 结果
     */
    public static String substring(final String str, int start) {
        if (str == null) {
            return NULLPTR;
        }
        
        if (start < 0) {
            start = str.length() + start;
        }
        
        if (start < 0) {
            start = 0;
        }
        if (start > str.length()) {
            return NULLPTR;
        }
        
        return str.substring(start);
    }
    
    /**
     * 截取字符串
     *
     * @param str   字符串
     * @param start 开始
     * @param end   结束
     *
     * @return 结果
     */
    public static String substring(final String str, int start, int end) {
        if (str == null) {
            return NULLPTR;
        }
        if (end < 0) {
            end = str.length() + end;
        }
        if (start < 0) {
            start = str.length() + start;
        }
        if (end > str.length()) {
            end = str.length();
        }
        if (start > end) {
            return NULLPTR;
        }
        if (start < 0) {
            start = 0;
        }
        if (end < 0) {
            end = 0;
        }
        return str.substring(start, end);
    }
    
    /**
     * 是否包含字符串
     *
     * @param str  验证字符串
     * @param stars 字符串组
     *
     * @return 包含返回true
     */
    public static boolean inStringIgnoreCase(String str, String... stars) {
        if (str != null && stars != null) {
            for (String s : stars) {
                if (str.equalsIgnoreCase(trim(s))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 去空格
     */
    public static String trim(String str) {
        return (str == null ? "" : str.trim());
    }
    
    /**
     * 查找指定字符串是否匹配指定字符串列表中的任意一个字符串
     *
     * @param str  指定字符串
     * @param strs 需要检查的字符串数组
     *
     * @return 是否匹配
     */
    public static boolean matches(String str, List<String> strs) {
        if (isEmpty(str) || EmptyUtil.isEmpty(strs)) {
            return false;
        }
        for (String testStr : strs) {
            if (matches(str, testStr)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 查找指定字符串是否匹配指定字符串数组中的任意一个字符串
     *
     * @param str  指定字符串
     * @param strs 需要检查的字符串数组
     *
     * @return 是否匹配
     */
    public static boolean matches(String str, String... strs) {
        if (isEmpty(str) || EmptyUtil.isEmpty(strs)) {
            return false;
        }
        for (String testStr : strs) {
            if (matches(str, testStr)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 查找指定字符串是否匹配
     *
     * @param str     指定字符串
     * @param pattern 需要检查的字符串
     *
     * @return 是否匹配
     */
    public static boolean matches(String str, String pattern) {
        if (isEmpty(pattern) || isEmpty(str)) {
            return false;
        }
        
        pattern = pattern.replaceAll("\\s*", ""); // 替换空格
        int beginOffset = 0; // pattern截取开始位置
        int formerStarOffset = -1; // 前星号的偏移位置
        int latterStarOffset = -1; // 后星号的偏移位置
        
        String remainingUri = str;
        String prefixPattern = "";
        String suffixPattern = "";
        
        boolean result = false;
        do {
            formerStarOffset = indexOf(pattern, START, beginOffset);
            prefixPattern = substring(pattern,
                                      beginOffset,
                                      formerStarOffset > -1 ? formerStarOffset : pattern.length()
            );
            
            // 匹配前缀Pattern
            result = remainingUri.contains(prefixPattern);
            // 已经没有星号，直接返回
            if (formerStarOffset == -1) {
                return result;
            }
            
            // 匹配失败，直接返回
            if (!result) {
                return false;
            }
            
            if (!isEmpty(prefixPattern)) {
                remainingUri = substringAfter(str, prefixPattern);
            }
            
            // 匹配后缀Pattern
            latterStarOffset = indexOf(pattern, START, formerStarOffset + 1);
            suffixPattern = substring(pattern,
                                      formerStarOffset + 1,
                                      latterStarOffset > -1 ? latterStarOffset : pattern.length()
            );
            
            result = remainingUri.contains(suffixPattern);
            // 匹配失败，直接返回
            if (!result) {
                return false;
            }
            
            if (!isEmpty(suffixPattern)) {
                remainingUri = substringAfter(str, suffixPattern);
            }
            
            // 移动指针
            beginOffset = latterStarOffset + 1;
            
        } while (!isEmpty(suffixPattern) && !isEmpty(remainingUri));
        
        return true;
    }
}