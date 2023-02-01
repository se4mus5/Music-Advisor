package advisor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TextUserInterface {
    private final String UNAUTHORIZED_MSG = "Please, provide access for application.";
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private boolean authorized;
    private final AppLogic appLogic;
    private static int pageSize;

    public TextUserInterface(String[] args) {

        this.authorized = false;
        // this.authorized = true;
        this.appLogic = new AppLogic(args);
        pageSize = AppLogic.getPageSize();
    }

    public void start() {
        Scanner scan = new Scanner(System.in);
        String commandLine;

        do {
            commandLine = scan.nextLine();
            String[] commandParts = commandLine.strip().split(" ", 2);

            switch (commandParts[0]) {
                case "auth" -> authorize();
                case "new" -> newReleases();
                case "featured" -> featuredPlaylists();
                case "categories" -> categories();
                case "playlists" -> playlists(commandParts[1]);
            }
        } while (!commandLine.equals("exit"));
        System.out.println("---GOODBYE!---");
    }

    private void newReleases() {
        if (authorized) {
            String apiResponseJsonString = appLogic.getNewReleases();
            List<String> newReleases = extractPropertiesFromJsonStringIntoPages(apiResponseJsonString, "albums", true, true);
            paginateDisplay(newReleases);
        } else {
            System.out.println(UNAUTHORIZED_MSG);
        }
    }

    private void featuredPlaylists() {
        if (authorized) {
            String apiResponseJsonString = appLogic.getFeaturedPlaylists();
            List<String> featuredPlaylists = extractPropertiesFromJsonStringIntoPages(apiResponseJsonString, "playlists", false, true);
            paginateDisplay(featuredPlaylists);
        } else {
            System.out.println(UNAUTHORIZED_MSG);
        }
    }

    private void categories() {
        if (authorized) {
            String apiResponseJsonString = appLogic.getCategories();
            List<String> categories = extractPropertiesFromJsonStringIntoPages(apiResponseJsonString, "categories", false, false);
            paginateDisplay(categories);
        } else {
            System.out.println(UNAUTHORIZED_MSG);
        }
    }

    private void playlists(String category) {
        if (authorized) {
            String apiResponseJsonString = appLogic.getPlaylists(category);
            if (apiResponseJsonString.startsWith("Unknown category")) { // user entered unknown category
                System.out.println(apiResponseJsonString);
                return;
            }

            List<String> playlists = extractPropertiesFromJsonStringIntoPages(apiResponseJsonString, "playlists", false, true);
            paginateDisplay(playlists);
        } else {
            System.out.println(UNAUTHORIZED_MSG);
        }
    }

    private void paginateDisplay(List<String> dataInPages) {
        Scanner scan = new Scanner(System.in);
        int page = 0;
        System.out.print(dataInPages.get(page));
        System.out.printf("---PAGE %d OF %d---\n", page + 1, dataInPages.size());
        while (true) {
            String commandLine = scan.nextLine();
            switch (commandLine) {
                case "next" -> page++;
                case "prev" -> page--;
                case "exit" -> { return; }
            }

            if (page >= 0 && page < dataInPages.size()) {
                System.out.print(dataInPages.get(page));
                System.out.printf("---PAGE %d OF %d---\n", page + 1, dataInPages.size());
            } else {
                System.out.println("No more pages.");
                page = page >= dataInPages.size() ? dataInPages.size() - 1 : 0;
            }
        }
    }

    private void authorize() {
        appLogic.setAccessToken();
        String accessToken = appLogic.getAccessTokenFullJson();
        System.out.println(accessToken);
        authorized = true;
        System.out.println("---SUCCESS---");
    }

    // TODO extractPropertiesFromJsonStringIntoPages does too many things, break up
    private static List<String> extractPropertiesFromJsonStringIntoPages(String jsonString, String itemsParentProperty, boolean extractArtists, boolean extractUrls) {
        List<String> extractedProperties = new ArrayList<>();
        String itemName;
        String itemUrl = ""; // NPE prevention
        List<String> artistNames = new ArrayList<>();
        int itemCnt = 0;
        StringBuilder itemStringRepresentationAggregator = new StringBuilder();

        JsonObject apiResponseJsonObj = JsonParser.parseString(jsonString).getAsJsonObject();
        if (jsonString.startsWith("{\"error\":")) {
            extractedProperties.add(apiResponseJsonObj.get("error").getAsJsonObject().get("message").getAsString() + LINE_SEPARATOR);
            return extractedProperties;
        }
        JsonArray items = apiResponseJsonObj.get(itemsParentProperty).getAsJsonObject().get("items").getAsJsonArray();

        for (JsonElement itemJsonEmt : items) {
            JsonObject itemJsonObj = itemJsonEmt.getAsJsonObject();

            // extract name
            itemName = itemJsonObj.getAsJsonObject().get("name").getAsString();

            // extract featured url
            if (extractUrls) {
                JsonObject externalUrls = itemJsonObj.get("external_urls").getAsJsonObject();
                itemUrl = externalUrls.get("spotify").getAsString();
            }

            // extract artist names
            if (extractArtists) {
                JsonArray artists = itemJsonObj.get("artists").getAsJsonArray();
                artistNames.clear();
                for (JsonElement artistJsonEmt : artists) {
                    String artistName = artistJsonEmt.getAsJsonObject().get("name").getAsString();
                    artistNames.add(artistName);
                }
            }

            // format line item as String
            itemStringRepresentationAggregator.append(itemName);
            itemStringRepresentationAggregator.append(LINE_SEPARATOR);
            if (extractArtists) {
                itemStringRepresentationAggregator.append("[");
                artistNames.forEach(a -> itemStringRepresentationAggregator.append(a).append(", "));
                itemStringRepresentationAggregator.replace(itemStringRepresentationAggregator.length() - 2,
                        itemStringRepresentationAggregator.length(), ""); // remove training comma
                itemStringRepresentationAggregator.append("]").append(LINE_SEPARATOR);
            }
            if (extractUrls) {
                itemStringRepresentationAggregator.append(itemUrl);
                itemStringRepresentationAggregator.append(LINE_SEPARATOR);
                itemStringRepresentationAggregator.append(LINE_SEPARATOR);
            }
            itemCnt++;
            if (itemCnt % pageSize == 0) { // add items in page-sized batches, facilitates trivial UI pagination
                extractedProperties.add(itemStringRepresentationAggregator.toString());
                itemStringRepresentationAggregator.setLength(0);
            }
        }
        if (!itemStringRepresentationAggregator.isEmpty()) { // add remaining items that do not make up a full page
            extractedProperties.add(itemStringRepresentationAggregator.toString());
        }

        return extractedProperties;
    }
}
