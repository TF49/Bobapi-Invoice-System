package com.invoice.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * IP 字面量解析与 CIDR 匹配工具。
 *
 * <p>只接受 IP 字面量，绝不触发 DNS 解析：转发头里的值来自外部，不能交给
 * {@link InetAddress#getByName(String)} 去解析主机名。
 */
public final class IpUtils {

    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    /**
     * 首字符必须是十六进制数字或冒号：JDK 只在这种情况下按 IP 字面量解析，
     * 否则（如 ".1:2"）会回落到系统 DNS 查询。
     */
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:][0-9A-Fa-f:.]*$");
    private static final int MAX_LITERAL_LENGTH = 64;

    private IpUtils() {
    }

    /**
     * 解析 IP 字面量，失败返回 {@code null}。
     * 兼容 {@code [v6]}、{@code [v6]:port}、{@code v4:port} 与带 zone id 的 IPv6。
     */
    public static InetAddress parseLiteral(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (value.isEmpty() || value.length() > MAX_LITERAL_LENGTH) {
            return null;
        }
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 0) {
                return null;
            }
            value = value.substring(1, end);
        } else if (value.indexOf(':') >= 0 && value.indexOf(':') == value.lastIndexOf(':') && value.indexOf('.') > 0) {
            // IPv4:port
            value = value.substring(0, value.indexOf(':'));
        }
        int zone = value.indexOf('%');
        if (zone >= 0) {
            value = value.substring(0, zone);
        }

        try {
            if (IPV4.matcher(value).matches()) {
                String[] parts = value.split("\\.");
                byte[] bytes = new byte[4];
                for (int i = 0; i < 4; i++) {
                    bytes[i] = (byte) Integer.parseInt(parts[i]);
                }
                return InetAddress.getByAddress(bytes);
            }
            // 含冒号时 JDK 按 IPv6 字面量解析，非法则直接抛异常，不会发起 DNS 查询
            if (value.indexOf(':') >= 0 && IPV6_CHARS.matcher(value).matches()) {
                return InetAddress.getByName(value);
            }
        } catch (UnknownHostException | NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    /**
     * 规范化 IP 字符串（IPv6 统一为 JDK 标准写法，IPv4 映射地址转为 IPv4），无法解析返回 {@code null}。
     */
    public static String normalize(String raw) {
        InetAddress address = parseLiteral(raw);
        return address == null ? null : address.getHostAddress();
    }

    /**
     * 返回用于“同网段”限流的网段键：IPv4 取 /24，IPv6 取 /64。无法解析时原样返回。
     */
    public static String subnetKey(String ip) {
        InetAddress address = parseLiteral(ip);
        if (address == null) {
            return ip == null ? "unknown" : ip;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "." + (bytes[2] & 0xff) + ".0/24";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 8; i += 2) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff)));
        }
        return builder.append("::/64").toString();
    }

    /**
     * 解析逗号分隔的 CIDR 列表（单个 IP 视为 /32 或 /128），非法条目抛出 {@link IllegalArgumentException}。
     */
    public static List<Cidr> parseCidrList(String csv) {
        List<Cidr> result = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String item : csv.split(",")) {
            String entry = item.strip();
            if (!entry.isEmpty()) {
                result.add(Cidr.parse(entry));
            }
        }
        return List.copyOf(result);
    }

    public static boolean matchesAny(InetAddress address, List<Cidr> ranges) {
        if (address == null) {
            return false;
        }
        for (Cidr range : ranges) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    public record Cidr(byte[] network, int prefixLength) {

        public static Cidr parse(String text) {
            String addressPart = text;
            int prefix = -1;
            int slash = text.indexOf('/');
            if (slash >= 0) {
                addressPart = text.substring(0, slash);
                try {
                    prefix = Integer.parseInt(text.substring(slash + 1).strip());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("非法的 CIDR: " + text);
                }
            }
            InetAddress address = parseLiteral(addressPart);
            if (address == null) {
                throw new IllegalArgumentException("非法的 CIDR: " + text);
            }
            byte[] bytes = address.getAddress();
            int maxPrefix = bytes.length * 8;
            if (prefix < 0) {
                prefix = maxPrefix;
            }
            if (prefix > maxPrefix) {
                throw new IllegalArgumentException("非法的 CIDR 前缀长度: " + text);
            }
            return new Cidr(mask(bytes, prefix), prefix);
        }

        public boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            return Arrays.equals(mask(candidate, prefixLength), network);
        }

        private static byte[] mask(byte[] bytes, int prefix) {
            byte[] masked = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                int bits = Math.max(0, Math.min(8, prefix - i * 8));
                int m = bits == 0 ? 0 : (0xff << (8 - bits)) & 0xff;
                masked[i] = (byte) (bytes[i] & m);
            }
            return masked;
        }
    }
}
