package server;

import server.Modes.GameModes;
import server.player.Player;

// Isn't it better to one-line this?
public class RunnableTask implements Runnable {
    private Player player;
    private GameModes runMode;

    public RunnableTask(Player aPlayer, GameModes mode) {
        player = aPlayer;
        runMode = mode;
    }

    @Override
    public void run() {
        //startGame(player, runMode);

    } 


} 