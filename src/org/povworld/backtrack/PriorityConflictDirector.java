package org.povworld.backtrack;

import java.util.logging.Level;

import javax.annotation.CheckForNull;

import org.povworld.collection.mutable.TreeList;

public class PriorityConflictDirector<State, Conflict, Option> extends Director<State, Conflict, Option> {

	private final int maxPriorityListSize;

	private final TreeList<Conflict> priorityConflicts = new TreeList<>();
	private int backtracklength = 0;

	public PriorityConflictDirector(Executor<State, Conflict, Option> executor, Level loggingLevel,
			int maxPriorityListSize) {
		super(executor, loggingLevel);
		this.maxPriorityListSize = maxPriorityListSize;
	}

	@Override
	@CheckForNull
	protected Conflict chooseNextConflict(State state) {
		backtracklength = 0;
		if (priorityConflicts.isEmpty()) {
			return super.chooseNextConflict(state);
		} else {
			return priorityConflicts.removeElementAt(0);
		}
	}

	@Override
	protected void backtrack() {
		Conflict conflict = decisions.peek().conflict;
		priorityConflicts.add(conflict, Math.min(backtracklength, priorityConflicts.size()));
		if (priorityConflicts.size() > maxPriorityListSize) {
			priorityConflicts.removeElementAt(priorityConflicts.size() - 1);
		}
		backtracklength++;
		super.backtrack();
	}

}
