package advisor;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class AppLogic {

    @Parameter(names={"-access"})
    private String SPOTIFY_OAUTH2_API_URI = "https://accounts.spotify.com";
    @Parameter(names={"-resource"})
    private String SPOTIFY_API_URI = "https://api.spotify.com";
    @Parameter(names={"-page"})
    private static int pageSize = 5; // pagination is implemented on the client side: tests are incompatible w/ Spotify API pagination
    public static int getPageSize() { return pageSize; }
    private final String CATEGORIES_PATH = "/v1/browse/categories";
    private final String PLAYLISTS_PATH = "/v1/browse/categories/%s/playlists";
    private final String NEW_RELEASES_PATH = "/v1/browse/new-releases";
    private final String FEATURED_PLAYLISTS_PATH = "/v1/browse/featured-playlists";
    private final String CLIENT_ID = "14d184f26d2a4d73b8492fa09e9787a0";
    private final String CLIENT_SECRET = "47502db99ad94512a9606f708bad77da";
    private final int PORT = 8080;
    private final String REDIRECT_URI = "http://localhost:" + PORT;
    private final String SPOTIFY_MANUAL_APP_AUTHORIZATION_URI;
    private String authCode = "INVALID_AUTH_CODE"; // NPE prevention
    private String accessToken = "INVALID_ACCESS_TOKEN"; // NPE prevention
    //private String accessToken = ""; // add valid access token here and set authorized = true in TextUserInterface for unattended testing

    HttpServer httpServer;
    HttpClient httpClient;

    public AppLogic(String[] args) {
        JCommander.newBuilder()
                .addObject(this)
                .build()
                .parse(args);

        SPOTIFY_MANUAL_APP_AUTHORIZATION_URI = String.format("%s/authorize?client_id=%s&redirect_uri=%s&response_type=code",
                SPOTIFY_OAUTH2_API_URI, CLIENT_ID, REDIRECT_URI);
        try {
            httpServer = HttpServer.create();
            httpServer.bind(new InetSocketAddress(PORT), 0);
            httpServer.start();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        httpClient = HttpClient.newBuilder()
                .build();
    }

    private void setAuthCode() {
        final String[] authCodeContainer = new String[1]; // workaround for final/effectively final mutate restriction of lambda
        try {
            // ask user to authorize app manually by clicking on link presented
            System.out.println("use this link to request the access code:");
            System.out.println(SPOTIFY_MANUAL_APP_AUTHORIZATION_URI);
            System.out.println("waiting for code...");

            httpServer.createContext("/",
                    exchange -> {
                        String authCodeResponse = exchange.getRequestURI().getQuery();
                        String messageToBrowser;

                        if (authCodeResponse != null && authCodeResponse.startsWith("code=")) {
                            authCodeContainer[0] = authCodeResponse.substring("code=".length());
                            messageToBrowser = "Got the code. Return back to your program.";
                        } else {
                            messageToBrowser = "Authorization code not found. Try again.";
                        }
                        exchange.sendResponseHeaders(200, messageToBrowser.length());
                        exchange.getResponseBody().write(messageToBrowser.getBytes());
                        exchange.getResponseBody().close();
                    }
            );
            while (authCodeContainer[0] == null) {
                Thread.sleep(10);
            }
            httpServer.removeContext("/");
            httpServer.stop(0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.authCode = authCodeContainer[0];
    }

    void setAccessToken() {
        setAuthCode();
        System.out.println("code received");

        // get Access Token from Spotify OAuth2 API, 'Request Access Token' section
        // https://developer.spotify.com/documentation/general/guides/authorization/code-flow/
        System.out.println("making http request for access_token...");
        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic "+ Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes())) // essentially basic HTTP auth
                .uri(URI.create(SPOTIFY_OAUTH2_API_URI + "/api/token"))
                .POST(HttpRequest.BodyPublishers.ofString(String.format("grant_type=authorization_code&code=%s&redirect_uri=%s", authCode, REDIRECT_URI)))
                .timeout(Duration.ofMillis(10000L))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
        accessToken = response.body();
    }

    public String getAccessTokenFullJson() {
        return accessToken;
    }

    public String getAccessToken() {
        JsonObject jo = JsonParser.parseString(accessToken).getAsJsonObject();
        return jo.get("access_token").getAsString();
    }

    public String makeHttpCallToSpotifyApi(String apiUri) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + getAccessToken())
                .uri(URI.create(apiUri))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
        return response.body();
    }

    public String getNewReleases() {
        return makeHttpCallToSpotifyApi(SPOTIFY_API_URI + NEW_RELEASES_PATH);
    }

    public String getFeaturedPlaylists() {
        return makeHttpCallToSpotifyApi(SPOTIFY_API_URI + FEATURED_PLAYLISTS_PATH);
    }

    public Map<String, String> getCategoryNameToIdMap() { // TODO merge into extractPropertiesFromJsonString intelligently
        Map<String, String> categoryNameToIdMap = new HashMap<>();
        String apiResponseJsonString = makeHttpCallToSpotifyApi(SPOTIFY_API_URI + CATEGORIES_PATH);
        String category;
        String categoryId;

        JsonObject apiResponseJsonObj = JsonParser.parseString(apiResponseJsonString).getAsJsonObject();
        JsonArray categoryItems = apiResponseJsonObj.get("categories").getAsJsonObject().get("items").getAsJsonArray();

        for (JsonElement newReleaseJsonEmt : categoryItems) {
            JsonObject newReleaseItemJsonObj = newReleaseJsonEmt.getAsJsonObject();
            category = newReleaseItemJsonObj.getAsJsonObject().get("name").getAsString();
            categoryId = newReleaseItemJsonObj.getAsJsonObject().get("id").getAsString();

            // populate map
            categoryNameToIdMap.put(category, categoryId);
        }

        return categoryNameToIdMap;
    }

    public String getCategories() {
        return makeHttpCallToSpotifyApi(SPOTIFY_API_URI + CATEGORIES_PATH);
    }

    public String getPlaylists(String category) {
        Map<String, String> categoryNameToIdMap = getCategoryNameToIdMap();

        if (!categoryNameToIdMap.containsKey(category)) {
            return "Unknown category name.\n";
        }

        String categoryId = categoryNameToIdMap.get(category);
        return makeHttpCallToSpotifyApi(SPOTIFY_API_URI + String.format(PLAYLISTS_PATH, categoryId));
    }
}
