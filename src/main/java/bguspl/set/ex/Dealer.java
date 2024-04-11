package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;


    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;



    /**
     * this lock incharge of the sleeping state of the Dealer
     */
    private final Object lockSleepDealer;


    /**
     * orgenized the requests of the payers to test their sets by order
     */
    public Queue<Player> setCheckRequests;

    /**
     * Threads list to start and terminate easily
     */
    public Thread[] playerThreads;


    /**
     * Stack to keep track on creation order for the bonus.
     */ 
    public Stack<Integer> playerStackIdInCreationOrder;

    /**
     * false for player woke him up
     */
    private boolean playerWokeUpDealer;

    /**
     * if removeAllCards , all players will not be able to keyPressed() and wait until he places all cards.
     */
    public boolean dealerReshufflesTable;



    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        
        reshuffleTime = System.currentTimeMillis()+ env.config.turnTimeoutMillis;
        playerThreads = new Thread[players.length];
        setCheckRequests = new LinkedList<Player>();
        this.lockSleepDealer = new Object();
        this.playerWokeUpDealer =false;
        this.dealerReshufflesTable = true;//will start with true to let dealer put all cards.
        this.playerStackIdInCreationOrder = new Stack<Integer>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        
        
        //creating the player Threads
        for(int i=0; i<playerThreads.length; i++){
            playerThreads[i] = new Thread(players[i]);
            playerThreads[i].start();
        }


        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);//to start from 60
            timerLoop();
            //updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        if(env.util.findSets(deck, 1).size() == 0){//if no sets left in the deck
            announceWinners();
        }
            
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        env.logger.info("dealer entred timerloop");
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            env.logger.info("dealer entred timerloop WHILE LOOP");
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        while(!playerStackIdInCreationOrder.empty()){ //closing Threads backwards
            try {
                int currPlayerId = playerStackIdInCreationOrder.pop();
                players[currPlayerId].terminate(); 
                playerThreads[currPlayerId].join();
                
           } catch (InterruptedException ignored) {} 
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        env.logger.info("DEALER :Entered RemoveCardsFromTable()");
        if(playerWokeUpDealer|| !setCheckRequests.isEmpty()){
            env.logger.info("PlayerWokeUpDealer = true (so entered the IF)");
            //woke up because of a SetCheck call from player.
            Player currPlayerSetToTest = setCheckRequests.poll();
            env.logger.info("Dealer polled the set from the request queue Set is:" + table.cardsPlayerHasTokened[currPlayerSetToTest.id]);
            synchronized(currPlayerSetToTest){//player is asleep until check is done
                env.logger.info("DEALER : took the key of player id: "+currPlayerSetToTest.id);
                if(currPlayerSetToTest !=null && 
                table.cardsPlayerHasTokened[currPlayerSetToTest.id].size()
                ==
                env.config.featureSize) //in order to avoid checking a set that includes a card we have just removed from the table 
                 {
                    LinkedList<Integer> cardList = table.cardsPlayerHasTokened[currPlayerSetToTest.id];
                    // Convert LinkedList to array:
                    int[] setToCheck = new int[cardList.size()];
                    for (int i = 0; i < cardList.size(); i++) {
                        setToCheck[i] = cardList.get(i); // Auto-unboxing converts Integer to int
                    }
                    boolean isSet = env.util.testSet(setToCheck);
                    env.logger.info("Dealer Testing the SET");
                    if(isSet){
                        
                        for(int card : setToCheck){
                            int mySlot = table.cardToSlot[card];
                            table.removeCard(mySlot);
                        }
                        playerWokeUpDealer = false; //resets the flag
                        env.logger.info("playerWokeUpDealer flag is false(reseted) by Player " + currPlayerSetToTest.id);
                        currPlayerSetToTest.inPointState = true;
                        currPlayerSetToTest.notifyAll();
                        updateTimerDisplay(true);
                    } 
                    
                    else {
                        playerWokeUpDealer = false; //resets the flag
                        env.logger.info("playerWokeUpDealer flag is false(reseted) by Player " + currPlayerSetToTest.id);
                        currPlayerSetToTest.inPointState = false;
                        currPlayerSetToTest.notifyAll();
                    }
                }
                else {// if a card was removed and belonged to a player's set that was waiting in the requests queue
                    playerWokeUpDealer = false; //resets the flag
                    env.logger.info("playerWokeUpDealer flag is false(reseted) by Player " + currPlayerSetToTest.id);
                    currPlayerSetToTest.inPointState = false;
                    currPlayerSetToTest.notifyAll();
                }
            }
            env.logger.info("DEALER : REALESED the key of player id: "+currPlayerSetToTest.id);
            if (deck.isEmpty()) {
                List<Integer> cardsOnTable = new LinkedList<Integer>();
                for (int i=0; i<table.slotToCard.length; i++){
                    if (table.slotToCard[i] != null)
                        cardsOnTable.add(table.slotToCard[i]);
                }
                if (env.util.findSets(cardsOnTable, 1).size() == 0)
                    this.terminate = true;
            }
        }   
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
         // Iterate over each slot
        for (int slotToCheck = 0; slotToCheck < env.config.tableSize; slotToCheck++) {
            // Check if the slot is empty and get a card
            if (table.slotToCard[slotToCheck] == null) {
                // Acquire lock for the current slot
                synchronized (table.slotLocks[slotToCheck]) {
                    env.logger.info("Dealer.placeCardsOnTable has the slotLock: slot- " +slotToCheck);
                    int card = getNextCardFromDeck(slotToCheck);
                    // If there is a card to place
                    if (card != -1) {
                        table.placeCard(card, slotToCheck); 
                    }
                }
                env.logger.info("Dealer.placeCardsOnTable has REALESED THE slotLock" +slotToCheck);
                
            }
        }
        dealerReshufflesTable = false; //letting the players keep playing after the remove all
        //table.hints();
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        //TODO implement
        synchronized (lockSleepDealer) {
            try {
                // Wait with a timeout (the turn timeout)
                lockSleepDealer.wait(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Preserve interrupt status
                env.logger.warning("Dealer thread interrupted.");
            }

        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset){
            env.logger.info("reset TIME");
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
            try {
                Thread.sleep(500);//for better looking when removing a set
            } catch (InterruptedException ignore) {}
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        }
        else if((reshuffleTime - System.currentTimeMillis()) <= env.config.turnTimeoutWarningMillis){
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
            if(reshuffleTime - System.currentTimeMillis() <= 0){
                env.ui.setCountdown(0, true);
            }
        }
        
        else
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);   
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        dealerReshufflesTable = true;
        env.logger.info("got in removeAllCards function");
        //dealerPlacingCardsOnTable = true;
        // TODO implement
        for(Player player : players){
            player.actionsQueue.clear();
        }
        for (int slot = 0; slot < env.config.tableSize; slot++) {
            if (table.slotToCard[slot] != null) {               
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }
        }


        updateTimerDisplay(true);
        env.logger.info("time has been reset after removing all cards");
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int topScore = 0;
        for (Player currPlayer : players) {
            if (topScore < currPlayer.score())
            topScore = currPlayer.score();
        }
        int arraySize = 0;
        for (Player player : players) {
            if (player.score() == topScore)
            arraySize++;
        }
        int j = 0;
        int[] winners = new int[arraySize];
        for (Player player : players) {
            if(player.score()==topScore && j<arraySize){
                winners[j] = player.id;
                j++;
            } 
        }
        env.ui.announceWinner(winners);
        terminate();
    }



    public void playerWakeUpDealer() { //id of the player that waked the dealer up
        //env.logger.info("PlayerWokeUpDealer() is called by player: " + playerId);
        synchronized (lockSleepDealer) {
            playerWokeUpDealer = true;//player woke the dealer up
            //env.logger.info("Player "+playerId+" changed the playerWokeUpDealer Flag to TRUE");
            lockSleepDealer.notify(); // Wake up the dealer
        }
    }


    private int getNextCardFromDeck(int slotToPutCard) {
        //if I dont have more cards in the deck return -1
        int randomIndexCard = (int)Math.floor(Math.random()*(deck.size()));;
        if(deck.size()==0){
            return -1;
        }
        int cardToPutOnTable = deck.get(randomIndexCard);
        table.placeCard(cardToPutOnTable, slotToPutCard);
        deck.remove(randomIndexCard);
        return -1;
    }
}