package com.dayu.smallfile.utils;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DayuStringUtils {

    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)\\s*(B|KB|MB|GB|TB)?");
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)\\s*(s|m|h|d)?");
    /**
     * 解析文件大小字符串为字节数
     *
     * @param sizeStr 文件大小字符串，如 "128MB"
     * @return 字节数
     */
    public static long parseSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) {
            return 0;
        }

        Matcher matcher = SIZE_PATTERN.matcher(sizeStr.trim());
        if (matcher.matches()) {
            long size = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            if (unit == null || "B".equals(unit)) {
                return size;
            } else if ("KB".equals(unit)) {
                return size * 1024;
            } else if ("MB".equals(unit)) {
                return size * 1024 * 1024;
            } else if ("GB".equals(unit)) {
                return size * 1024 * 1024 * 1024;
            } else if ("TB".equals(unit)) {
                return size * 1024 * 1024 * 1024 * 1024;
            }
        }

        throw new IllegalArgumentException("无效的文件大小格式: " + sizeStr);
    }

    /**
     * 解析时间字符串为毫秒数
     *
     * @param timeStr 时间字符串，如 "1h"
     * @return 毫秒数
     */
    public static long parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return 0;
        }

        Matcher matcher = TIME_PATTERN.matcher(timeStr.trim());
        if (matcher.matches()) {
            long time = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            if (unit == null || "s".equals(unit)) {
                return TimeUnit.SECONDS.toMillis(time);
            } else if ("m".equals(unit)) {
                return TimeUnit.MINUTES.toMillis(time);
            } else if ("h".equals(unit)) {
                return TimeUnit.HOURS.toMillis(time);
            } else if ("d".equals(unit)) {
                return TimeUnit.DAYS.toMillis(time);
            }
        }

        throw new IllegalArgumentException("无效的时间格式: " + timeStr);
    }
}
