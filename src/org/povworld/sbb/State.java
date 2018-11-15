package org.povworld.sbb;

import org.povworld.collection.persistent.PersistentMap;

public class State {
	// Warning: ResourecPlan and GraphConstraints are not immutable!
	// They must be copied before modification!
	public final PersistentMap<String, ResourcePlan> resourcePlans;
	public final PersistentMap<String, GraphConstraints> graphConstraints;
	public final double minPenalty;
	
	public State(PersistentMap<String, ResourcePlan> resourcePlans, 
			PersistentMap<String, GraphConstraints> graphConstraints,
			double minPenalty) {
		this.resourcePlans = resourcePlans;
		this.graphConstraints = graphConstraints;
		this.minPenalty = minPenalty;
	}
}
