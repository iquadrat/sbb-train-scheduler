package org.povworld.backtrack;

import java.util.logging.Level;

public class BoostingDirectorTest /*extends AbstractDirectorTest */ {

	//@Override
	protected <S, C, O> Director<S, C, O> createDirector(Executor<S, C, O> executor, Booster<C> booster) {
		return new BoostingDirector<>(executor, booster, 3, Level.FINE);
	}

}
