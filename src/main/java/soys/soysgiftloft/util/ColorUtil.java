package soys.soysgiftloft.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

/**
 * 颜色与文本工具类。
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    /**
     * 将 & 颜色代码转换为 Minecraft 颜色字符。
     */
    public static String color(String s) {
        if (s == null) {
            return null;
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /**
     * 批量转换颜色代码。
     */
    public static List<String> color(List<String> list) {
        if (list == null) {
            return null;
        }
        List<String> out = new ArrayList<>(list.size());
        for (String s : list) {
            out.add(color(s));
        }
        return out;
    }

    /**
     * 将秒数格式化为易读文本，例如 "1天2小时3分4秒"。
     */
    public static String formatPlaytime(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append("天");
        }
        if (h > 0) {
            sb.append(h).append("小时");
        }
        if (m > 0) {
            sb.append(m).append("分");
        }
        sb.append(s).append("秒");
        return sb.toString();
    }
}
