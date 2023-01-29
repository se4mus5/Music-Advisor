package advisor;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Scanner;

public class Main {

    //TODO migrate credentials into property file
    static final String CLIENT_ID = "14d184f26d2a4d73b8492fa09e9787a0";
    static final String CLIENT_SECRET = "47502db99ad94512a9606f708bad77da";
    static final String UNAUTHORIZED_MSG = "Please, provide access for application.";
    static final String REDIRECT_URI = "http://localhost:8080";
    static String SPOTIFY_OAUTH2_API_URI;
    static String SPOTIFY_AUTHORIZE_APP_URI;

    public static void main(String[] args) {
        SPOTIFY_OAUTH2_API_URI = args.length == 0 ? "https://accounts.spotify.com" : args[1];
        SPOTIFY_AUTHORIZE_APP_URI = String.format("%s/authorize?client_id=%s&redirect_uri=%s&response_type=code",
                SPOTIFY_OAUTH2_API_URI, CLIENT_ID, REDIRECT_URI);

        Scanner scan = new Scanner(System.in);
        String command;
        boolean authorized = false;

        do {
            command = scan.nextLine();

            switch (command) {
                case "auth" -> {
                    try {
                        String accessToken = getAccessToken();
                        System.out.println(accessToken);
                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                    }
                    authorized = true;
                    System.out.println("---SUCCESS---");
                }
                case "new" -> System.out.println(authorized ? """
                        ---NEW RELEASES---
                        Mountains [Sia, Diplo, Labrinth]
                        Runaway [Lil Peep]
                        The Greatest Show [Panic! At The Disco]
                        All Out Life [Slipknot]""" : UNAUTHORIZED_MSG);
                case "featured" -> System.out.println(authorized ? """
                        ---FEATURED---
                        Mellow Morning
                        Wake Up and Smell the Coffee
                        Monday Motivation
                        Songs to Sing in the Shower""" : UNAUTHORIZED_MSG);
                case "categories" -> System.out.println(authorized ? """
                        ---CATEGORIES---
                        Top Lists
                        Pop
                        Mood
                        Latin""" : UNAUTHORIZED_MSG);
                case "playlists Mood" -> System.out.println(authorized ? """
                        ---MOOD PLAYLISTS---
                        Walk Like A Badass
                        Rage Beats
                        Arab Mood Booster
                        Sunday Stroll""" : UNAUTHORIZED_MSG);
            }
        } while (!command.equals("exit"));
        System.out.println("---GOODBYE!---");
    }

    private static String getAuthCode() {
        final String[] authCode = new String[1]; // workaround for final/effectively final mutate restriction of lambda
        try {
            HttpServer server = HttpServer.create();
            server.bind(new InetSocketAddress(8080), 0);
            server.start();

            // ask user to authorize app
            System.out.println("use this link to request the access code:");
            System.out.println(SPOTIFY_AUTHORIZE_APP_URI);
            System.out.println("waiting for code...");

            server.createContext("/",
                    exchange -> {
                        String authCodeResponse = exchange.getRequestURI().getQuery();
                        String messageToBrowser;

                        if (authCodeResponse != null && authCodeResponse.startsWith("code=")) {
                            authCode[0] = authCodeResponse.substring("code=".length());
                            messageToBrowser = "Got the code. Return back to your program.";
                        } else {
                            messageToBrowser = "Authorization code not found. Try again.";
                        }
                        exchange.sendResponseHeaders(200, messageToBrowser.length());
                        exchange.getResponseBody().write(messageToBrowser.getBytes());
                        exchange.getResponseBody().close();
                    }
            );
            while (authCode[0] == null) {
                Thread.sleep(10);
            }
            server.stop(1);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return authCode[0];
    }

    private static String getAccessToken() throws InterruptedException, IOException {
        String authCode = getAuthCode();
        System.out.println("code received");

        HttpClient client = HttpClient.newBuilder()
                .build();

        // get Access Token from Spotify OAuth2 API, 'Request Access Token' section
        // https://developer.spotify.com/documentation/general/guides/authorization/code-flow/
        System.out.println("making http request for access_token...");
        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic "+ Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes()))
                .uri(URI.create(SPOTIFY_OAUTH2_API_URI + "/api/token"))
                .POST(HttpRequest.BodyPublishers.ofString(String.format("grant_type=authorization_code&code=%s&redirect_uri=%s", authCode, REDIRECT_URI)))
                .timeout(Duration.ofMillis(10000L))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
