package test;

import mnkgame.*;

import java.util.*;

import player.Utils;
import test.AdvancedMNKPlayerTester.GameState;
import test.AdvancedMNKPlayerTester.GameSetting;

public class GameTester {
    public int ROUNDS;
    public boolean VERBOSE = true;
    AdvancedMNKPlayerTester tester;
    HashMap<GameSetting, HashMap<MNKPlayer, HashMap<MNKPlayer, GameResult>>> gameSettingResults;
    static EnumMap<GameState, Integer> gameScoresMap;
    HashMap<MNKPlayer, Integer> playerScoresMap;
    private boolean addSamePlayersRound;
    HashMap<MNKPlayer, PlayerSummaryTable> playerSummaryTable;
    /** Scoring system */
    private static int WINP1SCORE = 2;
    private static int WINP2SCORE = 3;
    private static int DRAWSCORE  = 1;
    private static int ERRSCORE   = 2;

    public static class PlayerSummaryTable {
        EnumMap<GameState, Integer> scores;

        public PlayerSummaryTable() {
            this.scores = new EnumMap<>(GameState.class);
            this.scores.put(GameState.WINP1, 0);
            this.scores.put(GameState.WINP2, 0);
            this.scores.put(GameState.DRAW, 0);
            this.scores.put(GameState.ERRP1, 0);
            this.scores.put(GameState.ERRP2, 0);
        }
    }

    public static class GameResult extends PlayerSummaryTable {
        MNKPlayer opponent;

        public GameResult(MNKPlayer opponent) {
            this.opponent = opponent;
        }
    };

    public GameTester(int rounds, boolean addSamePlayersRound, AdvancedMNKPlayerTester tester) {
        this.tester = tester;
        this.ROUNDS = rounds;
        this.gameSettingResults = new HashMap<>();
        this.addSamePlayersRound = addSamePlayersRound;

        this.gameScoresMap = new EnumMap<>(GameState.class);
        this.gameScoresMap.put(GameState.WINP1, 2 );
        this.gameScoresMap.put(GameState.WINP2, 3 );
        this.gameScoresMap.put(GameState.DRAW, 1 );
        this.gameScoresMap.put(GameState.ERRP1, 2 );
        this.gameScoresMap.put(GameState.ERRP2, 2 );

        this.playerScoresMap = new HashMap<>();
        this.playerSummaryTable = new HashMap<>();
    }

    public MNKPlayer[] instancePlayers() {
        Class<MNKPlayer>[] playerClasses = tester.getPlayerClasses();

        MNKPlayer[] players = new MNKPlayer[playerClasses.length];
        int pci = 0;
        try {
            for (int i = 0; i < players.length; i++) {
                pci = i;
                players[i] = playerClasses[i].getDeclaredConstructor().newInstance();
            }
        }
        catch(NoSuchMethodException e) {
            throw new IllegalArgumentException("Illegal argument: \'" + playerClasses[pci] + "\' class constructor needs to be empty");
        }
        catch(Exception e) {
            throw new IllegalArgumentException("Illegal argument: \'" +playerClasses[pci] + "\' class (unexpected exception) " + e);
        }

        return players;
    }

    public void main() {

        MNKPlayer[] players = instancePlayers();
        GameSetting[] settings = tester.getSettings();

        AdvancedMNKPlayerTester.GameState result = null;
        for (MNKPlayer first : players) {
            for (MNKPlayer second : players) {
                for (GameSetting setting : settings) {
                    MNKPlayer[] opponents;
                    if( first == second ){
                        if(!addSamePlayersRound)
                            continue;
                        try {
                            opponents = new MNKPlayer[]{first, second.getClass().getDeclaredConstructor().newInstance()};
                        }
                        catch(NoSuchMethodException e) {
                            throw new IllegalArgumentException("Illegal argument: \'" + second.getClass().getName() + "\' class constructor needs to be empty");
                        }
                        catch(Exception e) {
                            throw new IllegalArgumentException("Illegal argument: \'" + second.getClass().getName() + "\' class (unexpected exception) " + e);
                        }
                    }
                    else {
                        opponents = new MNKPlayer[]{first, second};
                    }

                    for(int i = 1; i <= ROUNDS; i++) {
                        if (VERBOSE) System.out.println("\n**** ROUND " + i + " ****");

                        // INIT
                        tester.initGame(setting, opponents);
                        // RUN
                        result = tester.runGame();
                        // END

                        PlayerSummaryTable firstSummary = playerSummaryTable.get(first);
                        if( firstSummary == null) {
                            firstSummary = new PlayerSummaryTable();
                            playerSummaryTable.put(first,  firstSummary);
                        }
                        PlayerSummaryTable secondSummary = playerSummaryTable.get(second);
                        if( secondSummary == null) {
                            secondSummary = new PlayerSummaryTable();
                            playerSummaryTable.put(second,  secondSummary);
                        }

                        // update global Scoring System
                        Integer scoreP1 = playerScoresMap.getOrDefault(first, 0);
                        Integer scoreP2 = playerScoresMap.getOrDefault(second, 0);
                        int scoreAmount = gameScoresMap.get(result);
                        switch(result) {
                            case WINP1:
                            case ERRP2:
                                playerScoresMap.put(first, scoreP1 + scoreAmount);
                                break;
                            case WINP2:
                            case ERRP1:
                                playerScoresMap.put(second, scoreP2 + scoreAmount);
                                break;
                            case DRAW :
                                if( first != second ) {
                                    playerScoresMap.put(first, scoreP1 + scoreAmount);
                                    playerScoresMap.put(second, scoreP2 + scoreAmount);
                                }
                                else {
                                    playerScoresMap.put(first, scoreP1 + 2*scoreAmount);
                                }
                                break;
                        }

                        playerScoresMap.put(second, scoreP2);

                        // if (result.equals(setting.expectedResult))

                        switch (result) {
                            case WINP1:
                            case ERRP1:
                                firstSummary.scores.computeIfPresent(result, (gameState, value) -> value + 1);
                                break;
                            case WINP2:
                            case ERRP2:
                                secondSummary.scores.computeIfPresent(result, (gameState, value) -> value + 1);
                                break;
                            case DRAW:
                                firstSummary.scores.computeIfPresent(result, (gameState, value) -> value + 1);
                                secondSummary.scores.computeIfPresent(result, (gameState, value) -> value + 1);
                                break;
                        }


                        // update results System
                        HashMap<MNKPlayer, HashMap<MNKPlayer, GameResult>> playersResults = gameSettingResults.get(setting);
                        if (playersResults == null) {
                            playersResults = new HashMap<>();
                            gameSettingResults.put(setting, playersResults);
                        }
                        HashMap<MNKPlayer, GameResult> firstPlayerResults = playersResults.get(first);
                        if (firstPlayerResults == null) {
                            firstPlayerResults = new HashMap<>();
                            playersResults.put(first, firstPlayerResults);
                        }

                        GameResult totalResults = firstPlayerResults.get(second);
                        if (totalResults == null) {
                            totalResults = new GameResult(second);
                            firstPlayerResults.put(second, totalResults);
                        }

                        totalResults.scores.computeIfPresent(result, (gameState, value) -> value + 1);
                    }
                }
            }
        }

        printResults(settings, players, gameSettingResults);
        printTotalResults(settings, players, playerSummaryTable);
    }

    public static void printResults(GameSetting[] settings, MNKPlayer[] players, HashMap<GameSetting, HashMap<MNKPlayer, HashMap<MNKPlayer, GameResult>>> resultTable) {

        String[] header = new String[]{
                "M N K",
                "P1", "P2",
                GameState.WINP1.toString(), GameState.WINP2.toString(),
                GameState.DRAW.toString(),
                GameState.ERRP1.toString(),GameState.ERRP2.toString()
        };

        List<String[]> rows = new ArrayList<>();
        rows.add(header);
        int maxCharCount = 0;
        for(GameSetting setting : settings) {
            for( MNKPlayer first : players ) {
                maxCharCount = Math.max(maxCharCount, first.playerName().length());
                for( MNKPlayer second : players ) {
                    GameResult resultFirst = resultTable.get(setting).get(first).get(second);
                    if( resultFirst != null )
                        rows.add(new String[]{
                                setting.M + " " + setting.N + " " + setting.K,
                                first.playerName(),
                                second.playerName(),
                                resultFirst.scores.get(GameState.WINP1).toString(),
                                resultFirst.scores.get(GameState.WINP2).toString(),
                                resultFirst.scores.get(GameState.DRAW).toString(),
                                resultFirst.scores.get(GameState.ERRP1).toString(),
                                resultFirst.scores.get(GameState.ERRP2).toString()
                        });
                }
            }
        }


        System.out.println(Utils.tableToString(rows));
    }

    public static void printTotalResults(GameSetting[] settings, MNKPlayer[] players, HashMap<MNKPlayer, PlayerSummaryTable> playerSummaryTable) {
        int winsAsP1, winsAsP2;
        int draws;
        int errorsAsP1, errorsAsP2;

        String[] header = new String[]{
                "Player",
                "Score",
                GameState.WINP1.toString(), GameState.WINP2.toString(),
                GameState.DRAW.toString(),
                GameState.ERRP1.toString(),GameState.ERRP2.toString(),
        };

        List<String[]> rows = new ArrayList<>(players.length+1);
        rows.add(header);
        for( MNKPlayer first : players ) {
            winsAsP1 = 0; winsAsP2 = 0;
            draws = 0;
            errorsAsP1 = 0; errorsAsP2 = 0;

            PlayerSummaryTable resultFirst = playerSummaryTable.get(first);
            winsAsP1 += resultFirst.scores.get(GameState.WINP1);
            winsAsP2 += resultFirst.scores.get(GameState.WINP2);
            draws += resultFirst.scores.get(GameState.DRAW);
            errorsAsP1 += resultFirst.scores.get(GameState.ERRP1);
            errorsAsP2 += resultFirst.scores.get(GameState.ERRP2);
            int score = 0;
            for ( Map.Entry<GameState, Integer> scoreType : gameScoresMap.entrySet() ) {
                switch (scoreType.getKey()) {
                    case WINP1:
                    case WINP2:
                    case DRAW:
                        score += resultFirst.scores.get(scoreType.getKey()) * scoreType.getValue();
                        break;
                }
            }
            rows.add(new String[]{
                    first.playerName(),
                    String.valueOf(score),
                    String.valueOf(winsAsP1), String.valueOf(winsAsP2),
                    String.valueOf(draws),
                    String.valueOf(errorsAsP1), String.valueOf(errorsAsP2)
            });
        }

        System.out.println(Utils.tableToString(rows));
    }

}
