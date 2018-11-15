package org.povworld.backtrack;

import java.util.logging.Level;

import org.povworld.collection.List;
import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.collection.mutable.ArrayList;

public class BoostingDirector<State, Conflict, Option> extends Director<State, Conflict, Option> {
	
	private final Booster<Conflict> booster;
	private int boostHardConflictSize;

	public BoostingDirector(Executor<State, Conflict, Option> executor, Booster<Conflict> booster, int boostHardConflictSize, Level loggingLevel) {
		super(executor, loggingLevel);
		this.booster = booster;
		this.boostHardConflictSize = boostHardConflictSize;
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
					boost(hardConflicts);
					break;
				} else {
					for(int i=0; i<hardConflicts.size(); ++i) {
						decisions.pop();
					}
					// TODO insert at front?!
					hardConflicts.push(last.conflict);
					if (hardConflicts.size() == boostHardConflictSize) {
						boost(hardConflicts);
						jump(hardConflicts);
						return;
					}
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
	
	private int lastBoost = 0;
	
	private void boost(List<Conflict> conflicts) {
		if (decisions.size() > lastBoost) {
			lastBoost = decisions.size();
		}
		for(Conflict conflict: conflicts) {
			booster.boost(conflict);
		}
	}
	
	private int lastjump = 0;
	private int jumplength = 10;

	private void jump(List<Conflict> hardConflicts) {
		if (lastjump < decisions.size()) {
			jumplength = 10;
		} else {
			jumplength *= 2;
		}
		
		lastjump = decisions.size();
		jumplength = decisions.size() / 1;
		System.out.println("jumplength = " + jumplength);
		
		Decision<State, Conflict, Option> last = null;
		do {
			//int jumplength = Math.max(, lastBoost - decisions.size());
			for(int i=0; i<jumplength && !decisions.isEmpty(); ++i) {
				last = decisions.pop();
			}
			break;
			
//			State newState = tryApply(last.state, hardConflicts, 0);
//			if (newState != null) {
//				return;
//			}
		} while(!decisions.isEmpty());
		//lastBoost = 0;
		// Restart
		Conflict conflict = executor.getWorstConflict(last.state);
		decisions.push(new Decision<>(last.state, conflict,
				executor.getOptions(last.state, conflict)));
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
