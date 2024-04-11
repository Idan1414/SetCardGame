package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * players tokens slots
     */
    protected boolean[][] playerTokensSlots;

    /**
     * an array that represent for each player the cards he had clicked on
     * each player has a linked list.
     */
    protected LinkedList<Integer>[] cardsPlayerHasTokened;


     /**
     * an array that holds the locks for each slot
     */
    protected final Object[] slotLocks;



    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */

    @SuppressWarnings("unchecked")
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.playerTokensSlots = new boolean[env.config.players][env.config.tableSize];
        this.cardsPlayerHasTokened = new LinkedList[env.config.players];
        
        
        for (int i = 0; i < env.config.players; i++) {
        cardsPlayerHasTokened[i] = new LinkedList<>();
        }

        this.slotLocks = new Object[env.config.tableSize];
        for (int i = 0; i < env.config.tableSize; i++) {
            slotLocks[i] = new Object(); // Initialize each slot object
        }

    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);//Dealer sleeps
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        // TODO implement
        env.ui.placeCard(card, slot);

    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        // TODO implement
        env.ui.removeCard(slot);
        for(int i=0; i< env.config.players;i++){
            removeToken(i,slot);//removes the tokens that were on the card.
            env.ui.removeToken(i, slot);
            //cardsPlayerHasTokened[i].remove(slotToCard[slot]); 
        } 
        cardToSlot[slotToCard[slot]] = null;
        slotToCard[slot] = null ;
       
    };

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        // TODO implement
        if(slotToCard[slot]!=null){
            env.ui.placeToken(player, slot);
            playerTokensSlots[player][slot]=true;
            cardsPlayerHasTokened[player].add(slotToCard[slot]); // placing the card
            env.logger.info("cardNumber:" + slotToCard[slot]);
        }
        
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot){
            // TODO implement
            if(playerTokensSlots[player][slot]=true && cardsPlayerHasTokened[player].contains(slotToCard[slot])){
            env.ui.removeToken(player, slot);
            playerTokensSlots[player][slot]=false;
            cardsPlayerHasTokened[player].remove(slotToCard[slot]);
            return true;
        }
        return false;
    }
}