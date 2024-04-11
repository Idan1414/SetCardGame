package bguspl.set.ex;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;



    /**
     * The Queue that holds the slots for the actions of pressing a keyboard letter.
     */
    public BlockingQueue<Integer> actionsQueue;



    /**
     * Dealer
     */
    private Dealer dealer;


     /**
     * player will sleep until a key is pressed.
     */
    private Object keyPressLock;


    /**
     * freezeTimeFlag
     */
    private boolean isInFreezeTime; //


    /**
     * inPointState or inPenaltyState
     */
    public boolean inPointState; 



    /**

     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;

        this.actionsQueue = new LinkedBlockingQueue<>(env.config.featureSize);
        this.keyPressLock = new Object();
        this.isInFreezeTime = false;
        this.inPointState = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        dealer.playerStackIdInCreationOrder.push(id);//for the bonus
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop

            if(human){//for bonus
                synchronized(keyPressLock){ //go to sleep until keyPress
                    try{
                        while(actionsQueue.isEmpty() && !terminate){
                            keyPressLock.wait();    
                            env.logger.info("Player: " +id+ "goes to sleep until a key is pressed");
                        }
                    }
                    catch(InterruptedException ignored){}
                }
            }
           
            synchronized(this){
                    while(!actionsQueue.isEmpty()){
                        env.logger.info(actionsQueue.toString());
                        Integer currSlot =  actionsQueue.poll();
                        env.logger.info("Player " +id+ " :I have my key and I Polled from actionsQueue the slot: " + currSlot); // Debugging line
                        synchronized(table.slotLocks[currSlot]){ //in order to avoid same slot in one clocktick
                            env.logger.info("Player: " + id + "has the slotLock: lock-" + currSlot);
                            if(table.cardsPlayerHasTokened[id].contains(table.slotToCard[currSlot])){
                                table.removeToken(id, currSlot);
                            }
                            else if(table.cardsPlayerHasTokened[id].size()<env.config.featureSize){//in order to avoind placing "4th" token
                                table.placeToken(id, currSlot);
                               
                            }
                        }
                        env.logger.info("Releasing the SlotKey: "+ currSlot);
                        if (table.cardsPlayerHasTokened[id].size() == env.config.featureSize){//check for a set
                            dealer.setCheckRequests.add(this);
                            env.logger.info("Player " +id+ " asked to check set (added to request queue)");
                            dealer.playerWakeUpDealer();//setCheck
                            try {
                                this.wait();
                            } catch (InterruptedException e) {
                                env.logger.warning(" Player " + id + " was interrupted during setCheck sleep.");
                            }
                            if(inPointState)
                                point();
                            else
                                penalty();
                            //wakes up after getting a point/penalty
                        }
                    }
                }
                if(!human && actionsQueue.size()<env.config.featureSize){
                    synchronized(this){
                        this.notifyAll();
                        //env.logger.info("AI " +id+ " : is awake");
                    }
                }          
         }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                    
                    
                    while( !terminate &&!dealer.dealerReshufflesTable &&  actionsQueue.size()<env.config.featureSize){
                        int slotToClick = (int)(Math.floor(Math.random()*(env.config.tableSize)));
                        keyPressed(slotToClick);
                    }
                    
                    while(!terminate && actionsQueue.size()==env.config.featureSize){
                        try {
                            synchronized (this) { wait(); }
                        } catch (InterruptedException ignore) {}
                    }
                    

            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        // Signal all loops to terminate by setting the flag to true
        terminate = true;
        // Interrupt the player's thread to exit any blocking operation
        if (playerThread != null) {
            if(!human){
                aiThread.interrupt();
            }
            playerThread.interrupt();
        }

    
    }
    





    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        env.logger.info("player "+id+ " is awake and Entered KeyPressed() func");
        if(!dealer.dealerReshufflesTable && !isInFreezeTime && actionsQueue.size()<env.config.featureSize && table.slotToCard[slot]!=null ){ //in order to avoid clicking a "grey" slot
            actionsQueue.add(slot);
            env.logger.info("Player " +id+" : clicked on slot " + slot + "and added it to the actionsQueue");
            if(human){
                synchronized(keyPressLock){ // waking the player
                keyPressLock.notifyAll();
                env.logger.info("Player "+id+ "woke up from keypressed" );
                }
            }
        }
    }



    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        score++;
        
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id,score);
        actionsQueue.clear();
        isInFreezeTime = true;

        env.logger.info("Player " + id + " scored a point. Total score: " + score);
        try {
            for(int i = (int)(env.config.pointFreezeMillis/1000); i>0; i--){
                
                env.ui.setFreeze(id, i * 1000);
                Thread.sleep(1000);
                env.logger.info("seconds left for sleeping point : " + i);
                
            }
            env.ui.setFreeze(id, -1); // making it black again
            // After the sleep (point time)is finished without interaptions
            this.isInFreezeTime = false;


        } catch (InterruptedException e) {
            env.logger.warning("Point freeze for Player " + id + " was interrupted.");
        }
         // After the sleep (point time) is finished without interaptions
         env.logger.info("Player " + id + " point time ended.");

    }



    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
            actionsQueue.clear();
            isInFreezeTime = true;
            env.logger.info("Player id: " +id+" cleared actions queue from penalty()");
            env.logger.info("Player " + id + " penalized.");

            try {
                for(int i = (int)(env.config.penaltyFreezeMillis/1000); i>0; i--){
                     
                    env.ui.setFreeze(id, i * 1000);
                    Thread.sleep(1000);
                    env.logger.info("seconds left for sleeping penalty : " + i);

                }
                env.ui.setFreeze(id, -1); // making it black again
                // After the sleep (penalty time)is finished without interaptions
                this.isInFreezeTime = false;
                env.logger.info("Player " + id + " penalty time ended.");
                env.logger.info("Player " + id + " penalty time ended.");

            } catch (InterruptedException e) {
                env.logger.warning("Penalty sleep for Player " + id + " was interrupted.");
            }      
    }


    
    public int score() {
        return this.score;
    }
}