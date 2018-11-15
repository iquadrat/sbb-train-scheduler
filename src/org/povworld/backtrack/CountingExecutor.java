package org.povworld.backtrack;

import org.povworld.collection.List;

public class CountingExecutor<S,C,O> implements Executor<S,C,O> {
	
	public interface Callback {
		
		public void report(int worstConflictCalls, int optionCalls, int applyCalls);
	}
	
	private final Executor<S,C,O> delegate;
	private final Callback callback;
	private final int n;
	
	private int worstConflictCalls = 0;
	private int optionsCalls = 0;
	private int applyCalls = 0;
	
	public CountingExecutor(Executor<S, C, O> delegate, Callback callback, int n) {
		this.delegate = delegate;
		this.callback = callback;
		this.n = n;
	}

	@Override
	public C getWorstConflict(S state) {
		worstConflictCalls++;
		return delegate.getWorstConflict(state);
	}

	@Override
	public List<O> getOptions(S state, C conflict) {
		optionsCalls++;
		if (optionsCalls % n == 0) {
			callback.report(worstConflictCalls, optionsCalls, applyCalls);
		}
		return delegate.getOptions(state, conflict);
	}

	@Override
	public S apply(S state, O option) {
		applyCalls++;
		return delegate.apply(state, option);
	}
	
}
