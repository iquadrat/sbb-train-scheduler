package org.povworld.backtrack;

import javax.annotation.CheckForNull;

import org.povworld.collection.List;

public interface Executor<State, Conflict, Option> {
	@CheckForNull
	public Conflict getWorstConflict(State state);

	public List<Option> getOptions(State state, Conflict conflict);

	@CheckForNull
	public State apply(State state, Option option);
}
