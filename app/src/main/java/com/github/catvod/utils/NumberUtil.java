package com.github.catvod.utils;
public class NumberUtil {
    public static int parseIntWithDefault(String str) {
        return parseIntWithDefault(str, -1);
    }

    public static int parseIntWithDefault(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // 如果需要其他类型的解析方法，可以类似地编写
    public static double parseDoubleWithDefault(String str, double defaultValue) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
