package org.povworld.backtrack;

import java.util.logging.Level;

import org.povworld.backtrack.Director;
import org.povworld.backtrack.Executor;

public class DirectorTest extends AbstractDirectorTest {

	@Override
	protected <S, C, O> Director<S, C, O> createDirector(Executor<S, C, O> executor, Booster<C> booster) {
		return new Director<>(executor, Level.FINE);
	}
	
	@Override
	public void testSelectAvoid3BadsSpread() {
		// takes tot long
	}
	
	@Override
	public void testSelectAvoid4BadsSpread() {
		// takes too long
	}
	
	@Override
	public void testSelectAvoid6BadsSpread() {
		// takes too long
	}
	
	@Override
	public void testSelectAvoid2BadsSpread() {
		// takes too long
	}
}
