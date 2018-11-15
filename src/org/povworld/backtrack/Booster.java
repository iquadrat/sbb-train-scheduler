package org.povworld.backtrack;

import java.util.HashMap;

public interface Booster<Conflict> {

	public void boost(Conflict conflict);
	
	public double getBoost(Conflict i);
	
	public class SimpleBooster<Conflict> implements Booster<Conflict> {
		
		private final HashMap<Conflict, Double> boosts = new HashMap<>();
		private final double factor;
		
		public SimpleBooster(double factor) {
			this.factor = factor;
		}
		
		@Override
		public void boost(Conflict conflict) {
			boosts.put(conflict, boosts.getOrDefault(conflict, 1.0) * factor);
			System.out.println("boosts: " + boosts);
		}
		public double getBoost(Conflict conflict) {
			return boosts.getOrDefault(conflict, 1.0);
		}
	}

}
