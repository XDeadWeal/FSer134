package rtx.kimiko.api.events.funtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import rtx.kimiko.api.events.funtime.FunTimeApiException;
import rtx.kimiko.api.events.funtime.FunTimeEvent;
import rtx.kimiko.api.events.funtime.FunTimeMine;

public final class FunTimeApi {
    public static final FunTimeApi INSTANCE = new FunTimeApi();

    private static final String FUNTIME_BASE = "https://api.funtime.su/method/";
    private static final String FUNTIME_TOKEN = "10BBA230#D18D#4E33#B78E#51BF18E3E4B7";
    private static final String FUNTIME_COOKIE =
        "__ddg1_=tfw46HOJpRrCl9PXA7UJ; " +
        "__ddg2_=4IaX2bSrLj4mjj23; " +
        "__ddgid_=d49Qw5ydB7vnqVsn; " +
        "FunVersion=1.0.68; " +
        "FunToken=" + FUNTIME_TOKEN;

    private static final String HOLYWORLD_EVENTS = "https://api.holyworld.me/v1/events";
    private static final String HOLYWORLD_SERVERS = "https://api.holyworld.me/v1/servers";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8L))
        .build();

    private volatile boolean useHolyWorld = false;

    private FunTimeApi() {
    }

    private static JsonObject parse(String string) {
        try {
            JsonElement jsonElement = JsonParser.parseString(string == null ? "" : string);
            return jsonElement.isJsonObject() ? jsonElement.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static String str(JsonObject obj, String key, String def) {
        if (obj == null) return def;
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return def;
        String val = el.getAsString().trim();
        return val.isEmpty() ? def : val;
    }

    private JsonObject request(String url, boolean withAuth) throws FunTimeApiException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10L))
            .header("Accept", "application/json");
        if (withAuth) {
            builder.header("Authorization", FUNTIME_TOKEN)
                   .header("Cookie", FUNTIME_COOKIE);
        }
        HttpResponse<String> resp;
        try {
            resp = this.http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FunTimeApiException(-1, "interrupted", "\u0417\u0430\u043f\u0440\u043e\u0441 \u043f\u0440\u0435\u0440\u0432\u0430\u043d");
        } catch (IOException e) {
            throw new FunTimeApiException(-1, "network", "\u0421\u0435\u0442\u044c \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430: " + e.getClass().getSimpleName());
        }
        if (resp.statusCode() == 200) {
            return parse(resp.body());
        }
        throw new FunTimeApiException(resp.statusCode(), "http", "HTTP " + resp.statusCode());
    }

    private static int anarchyNumber(String serverId) {
        if (serverId == null) return 0;
        StringBuilder sb = new StringBuilder();
        for (char c : serverId.toCharArray()) {
            if (c >= '0' && c <= '9') sb.append(c);
        }
        try {
            return sb.isEmpty() ? 0 : Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public List<FunTimeEvent> events() throws FunTimeApiException {
        if (!useHolyWorld) {
            try {
                return eventsFunTime();
            } catch (FunTimeApiException e) {
                useHolyWorld = true;
                return eventsHolyWorld();
            }
        }
        return eventsHolyWorld();
    }

    private List<FunTimeEvent> eventsFunTime() throws FunTimeApiException {
        JsonObject json = this.request(FUNTIME_BASE + "events-info", true);
        if (json.has("success") && !json.get("success").getAsBoolean()) {
            throw new FunTimeApiException(401, "auth", str(json, "error-message", "Unauthorized"));
        }
        return parseEventServers(json);
    }

    private List<FunTimeEvent> eventsHolyWorld() throws FunTimeApiException {
        JsonObject json = this.request(HOLYWORLD_EVENTS, false);
        return parseEventServers(json);
    }

    private List<FunTimeEvent> parseEventServers(JsonObject json) {
        ArrayList<FunTimeEvent> list = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String serverId = entry.getKey();
            if (!entry.getValue().isJsonArray()) continue;
            int anarchy = anarchyNumber(serverId);
            for (JsonElement el : entry.getValue().getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject event = el.getAsJsonObject();
                JsonObject meta = event.has("metadata") && event.get("metadata").isJsonObject()
                    ? event.getAsJsonObject("metadata") : new JsonObject();
                String name = str(meta, "displayName", str(event, "name", "\u0421\u043e\u0431\u044b\u0442\u0438\u0435"));
                String id = str(event, "id", "");
                String rarity = str(meta, "rare", str(event, "rarity", ""));
                list.add(new FunTimeEvent(name, anarchy, 0, id, rarity));
            }
        }
        list.sort((a, b) -> {
            int ra = rarityWeight(a.rarity());
            int rb = rarityWeight(b.rarity());
            if (ra != rb) return rb - ra;
            return Integer.compare(a.anarchy(), b.anarchy());
        });
        return list;
    }

    public List<FunTimeMine> mines() throws FunTimeApiException {
        return List.of();
    }

    private static String serverLabel(String serverId) {
        if (serverId == null || serverId.isBlank()) return "";
        int n = anarchyNumber(serverId);
        if (n <= 0) return serverId;
        String upper = serverId.toUpperCase(Locale.ROOT);
        String type = upper.contains("LITE") ? "\u041b\u0430\u0439\u0442-\u0410\u043d\u0430\u0440\u0445\u0438\u044f" : "\u0410\u043d\u0430\u0440\u0445\u0438\u044f";
        String ver = upper.contains("NEW") ? " (1.21)" : "";
        return type + " " + n + ver;
    }

    private static int rarityWeight(String r) {
        if (r == null) return 0;
        return switch (r.toUpperCase(Locale.ROOT)) {
            case "MYTHICAL" -> 5;
            case "LEGENDARY" -> 4;
            case "EPIC" -> 3;
            case "RARE" -> 2;
            case "NORMAL" -> 1;
            default -> {
                String lower = r.toLowerCase(Locale.ROOT);
                if (lower.contains("\u043c\u0438\u0444\u0438\u0447\u0435\u0441\u043a")) yield 5;
                if (lower.contains("\u043b\u0435\u0433\u0435\u043d\u0434\u0430\u0440\u043d")) yield 4;
                if (lower.contains("\u044d\u043f\u0438\u0447\u0435\u0441\u043a")) yield 3;
                if (lower.contains("\u0440\u0435\u0434\u043a")) yield 2;
                yield 0;
            }
        };
    }

    public void setBaseUrl(String string) {
    }
}
