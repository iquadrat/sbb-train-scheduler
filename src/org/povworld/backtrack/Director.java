package org.povworld.backtrack;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.povworld.collection.List;
import org.povworld.collection.mutable.ArrayList;

public class Director<State, Conflict, Option> {
	
	private static final Logger logger = Logger.getLogger(Director.class.getSimpleName());

	protected static final class Decision<State, Conflict, Option> {
		public final State state;
		public final Conflict conflict;
		public final List<Option> options;
		private int next = 0;

		Decision(State state, Conflict conflict, List<Option> options) {
			this.state = state;
			this.conflict = conflict;
			this.options = options;
		}

		Option nextOption() {
			return options.get(next++);
		}

		public boolean hasNextOption() {
			return next < options.size();
		}
	}

	protected final Executor<State, Conflict, Option> executor;
	protected final Level loggingLevel;
	protected final ArrayList<Decision<State, Conflict, Option>> decisions = new ArrayList<>();
	protected int steps = 0;

	public Director(Executor<State, Conflict, Option> executor, Level loggingLevel) {
		this.executor = executor;
		this.loggingLevel = loggingLevel;
	}
	
	private void step() {
		steps++;
		if (steps % 100 == 0) {
			StringBuilder sb = new StringBuilder();
			for (Decision<?, ?, ?> d : decisions) {
				sb.append(d.next + ";");
			}
			logger.log(Level.INFO, steps + ": " + decisions.size() + " / " + sb.toString());
		}
	}

	@CheckForNull
	public State work(State initialState) {
		Conflict initialConflict = executor.getWorstConflict(initialState);
		if (initialConflict == null) {
			// There are no conflicts! Why did you call me?
			return initialState;
		}
		List<Option> initialOptions = executor.getOptions(initialState, initialConflict);
		decisions.push(new Decision<>(initialState, initialConflict, initialOptions));

		for(;;) {
			step();
			Decision<State, Conflict, Option> decision = decisions.peek();
			State newState = null;
			while (decision.next < decision.options.size()) {
				Option option = decision.nextOption();
				logger.log(loggingLevel, decisions.size() + "/Applying ConflictSchedule " + option);
				newState = executor.apply(decision.state, option);
				if (newState != null) {
					break;
				}
				logger.log(loggingLevel, decisions.size() + "/Failed!");
			}

			if (newState != null) {
				Conflict conflict = chooseNextConflict(newState);
				if (conflict == null) {
					// Success!
					logger.log(loggingLevel, "Solved in {0} steps.", steps);
					return newState;
				}
				processConflict(newState, conflict);
			} else {
				// Dead end! Need to backtrack.
				backtrack();
				if (decisions.isEmpty()) {
					// Failed to find a success state!
					return null;
				}
				if (decisions.peek().conflict == null) {
					// TODO this is a bit hacky...
					return decisions.peek().state;
				}
			}
		}
	}
	
	@CheckForNull
	protected Conflict chooseNextConflict(State state) {
		return executor.getWorstConflict(state);
	}

	protected void processConflict(State newState, Conflict conflict) {
		List<Option> options = executor.getOptions(newState, conflict);
		decisions.push(new Decision<>(newState, conflict, options));
	}

	protected void backtrack() {
		decisions.pop();
	}

}
