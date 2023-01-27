package advisor;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);
        String command = "";
        boolean authenticated = false;
        String clientId = "14d184f26d2a4d73b8492fa09e9787a0";
        do {
            command = scan.nextLine();
            switch (command) {
                case "auth" -> { authenticated = true;
                    System.out.printf("https://accounts.spotify.com/authorize?client_id=%s&redirect_uri=http://localhost:8080&response_type=code\n",
                            clientId);
                    System.out.println("---SUCCESS---");
                }
                case "new" -> System.out.println(authenticated ? """
                        ---NEW RELEASES---
                        Mountains [Sia, Diplo, Labrinth]
                        Runaway [Lil Peep]
                        The Greatest Show [Panic! At The Disco]
                        All Out Life [Slipknot]""" : "Please, provide access for application.");
                case "featured" -> System.out.println(authenticated ? """
                        ---FEATURED---
                        Mellow Morning
                        Wake Up and Smell the Coffee
                        Monday Motivation
                        Songs to Sing in the Shower""" : "Please, provide access for application.");
                case "categories" -> System.out.println(authenticated ? """
                        ---CATEGORIES---
                        Top Lists
                        Pop
                        Mood
                        Latin""" : "Please, provide access for application.");
                case "playlists Mood" -> System.out.println(authenticated ? """
                        ---MOOD PLAYLISTS---
                        Walk Like A Badass
                        Rage Beats
                        Arab Mood Booster
                        Sunday Stroll""" : "Please, provide access for application.");
            }
        } while (!command.equals("exit"));
        System.out.println("---GOODBYE!---");
    }
}
