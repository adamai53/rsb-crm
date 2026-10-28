package dj.redseabunkering.crm.auth;

import jakarta.servlet.http.HttpServletRequest;

/** Adresse IP et navigateur de l'appelant, tronqués pour tenir dans les colonnes. */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(truncate(request.getRemoteAddr(), 64), truncate(request.getHeader("User-Agent"), 255));
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
