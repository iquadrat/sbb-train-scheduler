package org.povworld.backtrack;

import java.util.logging.Level;

import org.povworld.backtrack.Director;
import org.povworld.backtrack.Executor;
import org.povworld.backtrack.PriorityConflictDirector;

public class PriorityConflictDirectorTest extends AbstractDirectorTest {

	@Override
	protected <S, C, O> Director<S, C, O> createDirector(Executor<S, C, O> executor, Booster<C> booster) {
		return new PriorityConflictDirector<>(executor, Level.FINE, 50);
	}

}
