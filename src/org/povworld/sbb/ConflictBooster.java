package org.povworld.sbb;

import java.util.HashMap;

import org.povworld.backtrack.Booster;

public class ConflictBooster implements Booster<Conflict> {

	private final HashMap<String, Double> boost = new HashMap<>();
	private final double factor = 1.05;
	
	@Override
	public void boost(Conflict conflict) {
		if (conflict.resource == null) {
			// TODO connection boosting
			return;
		}
		boost.put(conflict.resource, factor * getBoost(conflict.resource));
		System.out.println("boosts: " + boost);
	}

	public double getBoost(String resource) {
		return boost.getOrDefault(resource, 1.0);
	}

	@Override
	public double getBoost(Conflict i) {
		throw new UnsupportedOperationException();
	}

}
