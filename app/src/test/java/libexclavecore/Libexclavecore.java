package libexclavecore;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Libexclavecore {

    public static URL parseURL(String link) throws Exception {
        return new MockURL(link);
    }

    public static URL newURL(String scheme) {
        return new MockURL(scheme + "://");
    }

    public static boolean isIP(String str) {
        return str != null && str.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}$");
    }

    public static boolean isIPv4(String str) {
        return isIP(str);
    }

    public static boolean isIPv6(String str) {
        return str != null && str.contains(":");
    }

    public static HostPort splitHostPort(String str) {
        if (str == null) return new HostPort();
        HostPort hp = new HostPort();
        int idx = str.lastIndexOf(':');
        if (idx > 0) {
            hp.setHost(str.substring(0, idx));
            try {
                hp.setPort(Integer.parseInt(str.substring(idx + 1)));
            } catch (Exception ignored) {}
        } else {
            hp.setHost(str);
        }
        return hp;
    }

    public static class MockURL implements URL {
        private String scheme = "";
        private String host = "";
        private int port = 0;
        private String username = "";
        private String password = "";
        private String fragment = "";
        private String path = "";
        private final List<Map.Entry<String, String>> queryParams = new ArrayList<>();

        public MockURL(String raw) {
            try {
                if (raw == null) return;
                int hashIdx = raw.indexOf('#');
                if (hashIdx >= 0) {
                    this.fragment = URLDecoder.decode(raw.substring(hashIdx + 1), StandardCharsets.UTF_8);
                    raw = raw.substring(0, hashIdx);
                }

                int colonSlash = raw.indexOf("://");
                if (colonSlash >= 0) {
                    this.scheme = raw.substring(0, colonSlash);
                    raw = raw.substring(colonSlash + 3);
                }

                int questionIdx = raw.indexOf('?');
                if (questionIdx >= 0) {
                    String query = raw.substring(questionIdx + 1);
                    raw = raw.substring(0, questionIdx);
                    if (!query.isEmpty()) {
                        String[] parts = query.split("&");
                        for (String part : parts) {
                            if (part.isEmpty()) continue;
                            int eq = part.indexOf('=');
                            if (eq >= 0) {
                                String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
                                String value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                                queryParams.add(new AbstractMap.SimpleEntry<>(key, value));
                            } else {
                                queryParams.add(new AbstractMap.SimpleEntry<>(URLDecoder.decode(part, StandardCharsets.UTF_8), ""));
                            }
                        }
                    }
                }

                int slashIdx = raw.indexOf('/');
                if (slashIdx >= 0) {
                    this.path = raw.substring(slashIdx);
                    raw = raw.substring(0, slashIdx);
                }

                int atIdx = raw.indexOf('@');
                if (atIdx >= 0) {
                    String userinfo = raw.substring(0, atIdx);
                    raw = raw.substring(atIdx + 1);
                    int userColon = userinfo.indexOf(':');
                    if (userColon >= 0) {
                        this.username = URLDecoder.decode(userinfo.substring(0, userColon), StandardCharsets.UTF_8);
                        this.password = URLDecoder.decode(userinfo.substring(userColon + 1), StandardCharsets.UTF_8);
                    } else {
                        this.username = URLDecoder.decode(userinfo, StandardCharsets.UTF_8);
                    }
                }

                int hostColon = raw.lastIndexOf(':');
                if (hostColon >= 0) {
                    this.host = raw.substring(0, hostColon);
                    try {
                        this.port = Integer.parseInt(raw.substring(hostColon + 1));
                    } catch (Exception ignored) {}
                } else {
                    this.host = raw;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        @Override public void addQueryParameter(String k, String v) {
            queryParams.add(new AbstractMap.SimpleEntry<>(k, v));
        }

        @Override public long countQueryParameter(String k) {
            return queryParams.stream().filter(e -> e.getKey().equals(k)).count();
        }

        @Override public long countQueryParameters() {
            return queryParams.size();
        }

        @Override public void deleteQueryParameter(String k) {
            queryParams.removeIf(e -> e.getKey().equals(k));
        }

        @Override public String getFragment() { return fragment; }
        @Override public String getHost() { return host; }
        @Override public String getOpaque() { return ""; }
        @Override public String getPassword() { return password; }
        @Override public String getPath() { return path; }
        @Override public int getPort() { return port; }

        @Override public String getQueryParameter(String k) {
            for (Map.Entry<String, String> e : queryParams) {
                if (e.getKey().equals(k)) return e.getValue();
            }
            return "";
        }

        @Override public String getQueryParameterAt(String k, long idx) {
            long count = 0;
            for (Map.Entry<String, String> e : queryParams) {
                if (e.getKey().equals(k)) {
                    if (count == idx) return e.getValue();
                    count++;
                }
            }
            return null;
        }

        @Override public String getRawFragment() { return fragment; }
        @Override public String getRawHost() { return host; }
        @Override public String getRawPath() { return path; }
        @Override public String getRawQuery() { return ""; }
        @Override public String getScheme() { return scheme; }

        @Override public String getString() {
            StringBuilder sb = new StringBuilder();
            if (scheme != null && !scheme.isEmpty()) {
                sb.append(scheme).append("://");
            }
            if (username != null && !username.isEmpty()) {
                sb.append(username);
                if (password != null && !password.isEmpty()) {
                    sb.append(":").append(password);
                }
                sb.append("@");
            }
            if (host != null) {
                sb.append(host);
            }
            if (port > 0) {
                sb.append(":").append(port);
            }
            if (path != null && !path.isEmpty()) {
                if (!path.startsWith("/")) sb.append("/");
                sb.append(path);
            }
            if (!queryParams.isEmpty()) {
                sb.append("?");
                boolean first = true;
                for (Map.Entry<String, String> e : queryParams) {
                    if (!first) sb.append("&");
                    first = false;
                    sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
                    sb.append("=");
                    sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
                }
            }
            if (fragment != null && !fragment.isEmpty()) {
                sb.append("#").append(fragment);
            }
            return sb.toString();
        }

        @Override public String getUserInfo() {
            if (username.isEmpty()) return "";
            if (password.isEmpty()) return username;
            return username + ":" + password;
        }

        @Override public String getUsername() { return username; }
        @Override public boolean hasPassword() { return password != null && !password.isEmpty(); }
        @Override public boolean hasPort() { return port > 0; }
        @Override public boolean hasQueryParameter(String k) { return getQueryParameter(k) != null; }
        @Override public boolean hasUsername() { return username != null && !username.isEmpty(); }
        @Override public QueryParameters queryParameters() { return null; }
        @Override public void setFragment(String f) { this.fragment = f; }
        @Override public void setHost(String h) { this.host = h; }
        @Override public void setHostPort(String h, int p) { this.host = h; this.port = p; }
        @Override public void setOpaque(String o) {}
        @Override public void setPassword(String p) { this.password = p; }
        @Override public void setPath(String p) { this.path = p; }
        @Override public void setQueryParameter(String k, String v) {
            deleteQueryParameter(k);
            addQueryParameter(k, v);
        }
        @Override public void setRawFragment(String f) { this.fragment = f; }
        @Override public void setRawHost(String h) { this.host = h; }
        @Override public void setRawPath(String p) { this.path = p; }
        @Override public void setRawQuery(String q) {}
        @Override public void setScheme(String s) { this.scheme = s; }
        @Override public void setUsername(String u) { this.username = u; }
        @Override public void setUsernamePassword(String u, String p) { this.username = u; this.password = p; }
    }
}
