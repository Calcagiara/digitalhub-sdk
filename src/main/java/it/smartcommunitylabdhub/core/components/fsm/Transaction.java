/**
 * Transaction.java
 *
 * This class represents a transition in the State Machine. It defines the event, next state, guard,
 * and auto-flag for the transition.
 *
 * @param <S> The type of the states.
 * @param <E> The type of the events.
 * @param <C> The type of the context.
 */

package it.smartcommunitylabdhub.core.components.fsm;

import java.util.Optional;
import java.util.function.BiPredicate;

public class Transaction<S, E, C> {
    private E event;
    private S nextState;
    private BiPredicate<Optional<?>, C> guard;

    public Transaction(E event, S nextState, BiPredicate<Optional<?>, C> guard) {
        this.event = event;
        this.nextState = nextState;
        this.guard = guard;
    }

    /**
     * Get the event associated with this transaction.
     *
     * @return The event.
     */
    public E getEvent() {
        return event;
    }

    /**
     * Get the next state to transition to.
     *
     * @return The next state.
     */
    public S getNextState() {
        return nextState;
    }

    /**
     * Get the guard function associated with this transaction.
     *
     * @return The guard function.
     */
    public BiPredicate<Optional<?>, C> getGuard() {
        return guard;
    }
}
