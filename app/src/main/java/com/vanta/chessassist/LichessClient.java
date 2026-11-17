package com.vanta.chessassist;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class LichessClient {

    public interface Callback {
        void onResult(String bestMoveUci, String evalStr, String line);
        void onError(String reason);
    }

    /**
     * Calls Lichess Cloud Eval API synchronously.
     * Run on a background thread — blocks until response.
     */
    public static void evalSync(String fen, Callback cb) {
        try {
            String enc      = URLEncoder.encode(fen, "UTF-8");
            String endpoint = "https://lichess.org/api/cloud-eval?fen=" + enc + "&multiPv=3";

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);

            int rc = conn.getResponseCode();
            if (rc == 404) { cb.onError("pos not in cloud DB"); return; }
            if (rc != 200) { cb.onError("http " + rc); return; }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String s;
            while ((s = br.readLine()) != null) sb.append(s);
            br.close();

            parse(sb.toString(), cb);

        } catch (Exception e) {
            cb.onError("net: " + e.getMessage());
        }
    }

    private static void parse(String json, Callback cb) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray pvs   = root.getJSONArray("pvs");
            if (pvs.length() == 0) { cb.onError("empty pvs"); return; }

            JSONObject top = pvs.getJSONObject(0);
            String moves   = top.optString("moves", "");
            String uci     = moves.isEmpty() ? null : moves.split(" ")[0];
            String bestStr = formatUci(uci);

            int cp   = top.optInt("cp",   Integer.MIN_VALUE);
            int mate = top.optInt("mate", Integer.MIN_VALUE);
            String evalStr;
            if (mate != Integer.MIN_VALUE) {
                evalStr = "M" + (mate > 0 ? "+" : "") + mate;
            } else if (cp != Integer.MIN_VALUE) {
                evalStr = String.format("%+.2f", cp / 100.0);
            } else {
                evalStr = "?";
            }

            String cont = moves.length() > 60 ? moves.substring(0, 60) + "…" : moves;

            cb.onResult(uci, evalStr, cont);

        } catch (Exception e) {
            cb.onError("parse: " + e.getMessage());
        }
    }

    /** e2e4 → e2→e4  |  e7e8q → e7→e8=Q */
    public static String formatUci(String uci) {
        if (uci == null || uci.length() < 4) return "?";
        String s = uci.substring(0, 2) + "→" + uci.substring(2, 4);
        if (uci.length() == 5) s += "=" + Character.toUpperCase(uci.charAt(4));
        return s;
    }
}
