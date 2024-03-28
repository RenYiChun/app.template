package com.lrenyi.template.core.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SystemUtils {
    private static final Logger logger = LoggerFactory.getLogger(SystemUtils.class);
    
    /**
     * 获取访问者IP
     * 在一般情况下使用Request.getRemoteAddr()即可，但是经过nginx等反向代理软件后，这个方法会失效。
     * <p>
     * 本方法先从Header中获取X-Real-IP，如果不存在再从X-Forwarded-For获得第一个IP(用,分割)，
     * 如果还不存在则调用Request .getRemoteAddr()。
     */
    public static String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ip = request.getHeader("client-ip-addr");
        if (EmptyUtil.isNotEmpty(ip)) {
            return ip;
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // 多次反向代理后会有多个IP值，第一个为真实IP。
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index);
            } else {
                return ip;
            }
        } else {
            return request.getRemoteAddr();
        }
    }
    
    /**
     * 获取来访者的浏览器版本
     */
    public static String getRequestBrowserInfo(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String header = request.getHeader("user-agent");
        return getBrowserInfoByUserAgent(header);
    }
    
    private static String getBrowserInfoByUserAgent(String userAgent) {
        String browserVersion = null;
        if (EmptyUtil.isEmpty(userAgent)) {
            return "";
        }
        if (userAgent.indexOf("MSIE") > 0) {
            browserVersion = "IE";
        } else if (userAgent.indexOf("Firefox") > 0) {
            browserVersion = "Firefox";
        } else if (userAgent.indexOf("Chrome") > 0) {
            browserVersion = "Chrome";
        } else if (userAgent.indexOf("Safari") > 0) {
            browserVersion = "Safari";
        } else if (userAgent.indexOf("Camino") > 0) {
            browserVersion = "Camino";
        } else if (userAgent.indexOf("Konqueror") > 0) {
            browserVersion = "Konqueror";
        }
        return browserVersion;
    }
    
    /**
     * 获取系统版本信息
     */
    public static String getRequestSystemInfo(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String header = request.getHeader("user-agent");
        return getSystemInfoByUserAgent(header);
    }
    
    private static String getSystemInfoByUserAgent(String userAgent) {
        String systenInfo = null;
        if (EmptyUtil.isEmpty(userAgent)) {
            return "";
        }
        //得到用户的操作系统
        int windows = userAgent.indexOf("Windows");
        if (windows > 0) {
            systenInfo = userAgent.substring(windows, userAgent.indexOf(")"));
        } else if (userAgent.indexOf("Mac") > 0) {
            systenInfo = "Mac";
        } else if (userAgent.indexOf("Unix") > 0) {
            systenInfo = "UNIX";
        } else if (userAgent.indexOf("Linux") > 0) {
            systenInfo = "Linux";
        } else if (userAgent.indexOf("SunOS") > 0) {
            systenInfo = "SunOS";
        }
        return systenInfo;
        
    }
    
    /**
     * 获取来访者的主机名称
     */
    public static String getHostName(String ip) {
        InetAddress inet;
        try {
            inet = InetAddress.getByName(ip);
            return inet.getHostName();
        } catch (UnknownHostException e) {
            logger.error("", e);
        }
        return "";
    }
}