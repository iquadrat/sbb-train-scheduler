package org.povworld.backtrack;

import java.util.logging.Level;

import org.povworld.collection.List;
import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.collection.mutable.ArrayList;

public class HardCollectingDirector<State, Conflict, Option> extends Director<State, Conflict, Option> {
	
	public HardCollectingDirector(Executor<State, Conflict, Option> executor, Level loggingLevel) {
		super(executor, loggingLevel);
	}
	
	@Override
	protected Conflict chooseNextConflict(State state) {
		return super.chooseNextConflict(state);
	}

	@Override
	protected void backtrack() {
		ArrayList<Conflict> hardConflicts = new ArrayList<>();
		hardConflicts.push(decisions.pop().conflict);
		
		State nextState;
		do {
			Decision<State, Conflict, Option> last = decisions.pop();
			nextState = tryApply(last.state, hardConflicts, 0); 
			if (nextState != null) {
				nextState = tryApply(nextState, ImmutableCollections.listOf(last.conflict), 0); 
				if (nextState != null) {
					break;
				} else {
					for(int i=0; i<hardConflicts.size(); ++i) {
						decisions.pop();
					}
					// TODO insert at front?!
					hardConflicts.push(last.conflict);
				}
			}
			if (decisions.isEmpty()) {
				// Everything is hard....
				State finalState = tryApply(last.state, hardConflicts, 0);
				if (finalState != null) {
					decisions.push(new Decision<>(finalState, null, null));
				}
				return;
			}
		} while(true);
		
		Conflict conflict = chooseNextConflict(nextState);
		List<Option> options = executor.getOptions(nextState, conflict);
		decisions.push(new Decision<>(nextState, conflict, options));
	}

	private State tryApply(State state, List<Conflict> hardConflicts, int start) {
		if (start == hardConflicts.size()) {
			return state;
		}
		Conflict conflict = hardConflicts.get(start);
		List<Option> options = executor.getOptions(state, conflict);
		Decision<State, Conflict, Option> decision = new Decision<>(state, conflict, options);
		while(decision.hasNextOption()) {
			State newState = executor.apply(state, decision.nextOption());
			if (newState != null) {
				decisions.push(decision);
				newState = tryApply(newState, hardConflicts, start + 1);
				if (newState != null) {
					return newState;
				}
				decisions.pop();
			}
		}
		return null;
	}
	
}
