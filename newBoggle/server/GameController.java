package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import javax.script.ScriptException;

import server.player.*;
import server.Modes.*;
import server.communication.Communication;

//  TODO: dont need to abstract the player class since socket closed reacts to the tcp channel being closed by 
//  throwing an exception.
// TODO: Read the dictionary at some point
// TODO: Restructure the logic of gameController
// TODO: Produce the randomized boggle board.

// BUGGAR:
//  1. Skickar man in "A" eller icke siffra som antar players i settings så castas NumberFormatException

public class GameController {
    
    private static ArrayList<String> dictionary = new ArrayList<String>(); // WHY not static size
    private static ArrayList<String> gameDict = new ArrayList<String>();
    private static ArrayList<Player> playerArray = new ArrayList<Player>();
    private static ArrayList<String> wordList = new ArrayList<String>();
    private static String[][] boggleBoard;
    private static ServerSocket serverSocket;
    public final static int PORT = 2048;
    private static String WORDFILE = "server/CollinsScrabbleWords2019.txt";
    private static Boolean menuRun = true;
    private static Boolean running = false;
    

    public static void main(String argv[]) throws Exception {
        startNewGame();
    }

    private static void startNewGame() throws FileNotFoundException, IOException , InterruptedException , ScriptException{
        GameModes gameMode = new GameModes();
        gameMode.loadJsonSettings("gameModes");
        dictionary = GameLogic.loadDictionary(WORDFILE);
        while(menuRun) {
            StartMenu.printMenu(gameMode);
            Scanner in = new Scanner(System.in);
            String userInput = in.nextLine();
            userChoice(userInput, gameMode, in);
        }
    }

    // need to check that number of players > 1 
    private static void startServer(int port, int players) throws IOException{
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started, waiting for players to connect..."); 
            for(int i=1; i < players; i++) {
                Socket connectionSocket = serverSocket.accept();
                ObjectInputStream inFromClient = new ObjectInputStream(connectionSocket.getInputStream());
                ObjectOutputStream outToClient = new ObjectOutputStream(connectionSocket.getOutputStream());
                playerArray.add(new ClientPlayer(i, connectionSocket, inFromClient, outToClient));
                System.out.println("Player " + i + " connected.");
                outToClient.writeObject("You connected to the server as player " + i);
            }
            
        } catch (IOException e) { 
            throw new IOException("Couldn't start server. Exception thrown: "+e); 
        } 
    }

     // Should i use this to create the localHost player object?
    private static void createLocalHost() {
            playerArray.add(new LocalPlayer(0, null, null, null));
    }
  
    /* private static void addClientPlayer(Socket socket, ObjectInputStream in, ObjectOutputStream out) {
        playerArray.add(new ClientPlayer(1,socket,in,out)); // TEST WITH ID 1
    } */
    
    private static void startGame(Player player, GameModes mode, String[][] boggleBoard) throws IOException, ScriptException{
        wordList.clear();
        GameLogic logic = new GameLogic(); // TODO: Should this only be referenced in static way??
        Communication messages = new Communication();
        String startInfo = "PlayerID: " + player.getId() + ", Game Mode: " + mode.getGameMode() + " Boggle, Time Limit: " + mode.getGameTime() + " seconds";
        
        messages.sendMessage(startInfo, player);
        messages.sendMessage(boggleBoard, player);

        while(running){
            String userWord = messages.readMessage(player);
            //How to check rules in a general way? Maybe by checking mode.gameMode?
            //TODO: refactor this into lower tree complexity
            if (logic.checkWordFree(userWord, player, mode, wordList)) {
                if (logic.checkWordValid(userWord, mode, gameDict)) { 
                    // check if numbers
                    if (mode.loadJsonGameMode(mode.getGameMode(), "modeType").contains("numeric")) {
                        messages.sendMessage(logic.isExpressionOnBoard(userWord, boggleBoard,mode), player);
                    } else {
                        messages.sendMessage("OK", player);
                        wordList.add(userWord);
                        player.addWord(userWord);
                        if (logic.getUseWordsOnce(mode)){
                            for (Player pl: playerArray) {
                                if (pl != player){
                                    String msg = "Player id "+ pl.getId() + ", played word:" + userWord;
                                    messages.sendMessage(msg, pl);
                                }
                            }
                        } 
                    }
                } else {messages.sendMessage("Not Valid", player);}
            } else {messages.sendMessage("Already submitted", player);}
            messages.sendMessage(boggleBoard, player);
        }
    }

    private static void userChoice(String choice, GameModes mode, Scanner in) throws FileNotFoundException, IOException, InterruptedException, ScriptException {
        if (choice.equals("Settings")) {
            StartMenu.printSettings(mode);
            String settingsChoice = in.nextLine();
            String [] setter = settingsChoice.split(" ", 2);
            if (setter[1].equals("size")) {
                if (checkSettings(mode.loadJsonSettings("boardSizes"), setter[0])) {
                    mode.setBoardSize(setter[0]);
                }
            } else if (setter[1].equals("dice")) {
                    if (setter[0].equals("generous")) {
                        mode.setGenerous(true);
                    } else {
                        mode.setGenerous(false);
                    }
            } else if (setter[1].equals("lang")) {
                if (checkSettings(mode.loadJsonSettings("languages"), setter[0])) {
                    System.out.println("teteteY");
                    mode.setLanguage(setter[0]);
                }
            } else if (setter[1].equals("solution")) {
                if (setter[0].equals("show")) {
                    mode.setShowSolution(true);
                } else {
                    mode.setShowSolution(false);
                }
            } else if (setter[1].equals("players")) {
                int i=Integer.parseInt(setter[0]);  
                if (i > 1) {
                    mode.setNumberOfPlayers(i);
                } else {
                    System.out.println("Must be 2 or more players.");
                }
            } else if (setter[1].equals("seconds")) {
                int i=Integer.parseInt(setter[0]); 
                if (i > 0){
                    mode.setGameTime(i);
                } else {
                    System.out.println("Must be 1 or more seconds.");
                }
            }
        } else if (choice.equals("Quit")) {
            System.out.println("BYE BYE");
            menuRun = false;
        } else if (checkSettings(mode.loadJsonSettings("gameModes"), choice)) {
            mode.setGameMode(choice);
            boggleBoard = GameLogic.randomizeBoard(BoggleBoards.getBoggleBoard(mode.getGameMode(), mode.getBoardSize()));
            if (!mode.loadJsonGameMode(mode.getGameMode(), "modeType").contains("numeric")) {
                SmartSearch newdict = new SmartSearch(dictionary, boggleBoard, boggleBoard.length);
                gameDict = newdict.getCurrentDict();
            }
            createLocalHost();
            startServer(PORT, mode.getNumberOfPlayers());
            running = true;
            startThreads(choice, mode);
            GameLogic.printEndScore(playerArray);
            endGame(); 
        }
    }
    //LOGIC
    private static Boolean checkSettings(ArrayList<String> list, String word) {
        for (String setting: list) {
            if (word.equals(setting)) {
                return true;
            }
        }
        return false;
    }

    private static void startThreads(String choice, GameModes mode) throws InterruptedException, IOException, ScriptException {
        final int players = mode.getNumberOfPlayers();
        ExecutorService threadpool = Executors.newFixedThreadPool(players);
        for(int i=0; i<players; i++) {
            Player aPlayer = playerArray.get(i);
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    try {
                        startGame(aPlayer, mode, boggleBoard);
                    } catch (IOException e) {
                        e.printStackTrace();
                        //System.out.println("IOException thrown, couldn't start game");
                    } catch (ScriptException e) {
                        new ScriptException("Error evaluating expression: "+e);
                    }
                }
            };
            threadpool.execute(task);
        }
        threadpool.awaitTermination(mode.getGameTime(), TimeUnit.SECONDS);
        running = false;
        threadpool.shutdownNow();
    }
    static void endGame() throws IOException {
        Communication msg = new Communication();
        for (Player player : playerArray){
            try {
                if (player.getId() != 0) {
                    msg.sendMessage("CLOSE SOCKET", player);
                    player.getSocket().close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        playerArray.clear();
        serverSocket.close();
    }
}

