package org.povworld.sbb;

import java.util.NoSuchElementException;

import org.povworld.collection.Set;
import org.povworld.collection.common.ObjectUtil;
import org.povworld.collection.mutable.HashMap;
import org.povworld.collection.mutable.HashMultiMap;
import org.povworld.sbb.IntentionRestriction.MarkPathInfeasibleRestriction;

public class IntentionRestrictionQueue {
	
	// TODO collect all min/max updates per intention into a map: edge -> mintime and
	//      apply them at once in a single call to GraphConstraint.increaseEarliestEntry
	
	// intention -> {IntentionRestriction}
	private final HashMultiMap<String, IntentionRestriction> restrictions = new HashMultiMap<>();
	private final HashMap<String, MarkPathInfeasibleRestriction> infeasibleEdges = new HashMap<>();
	
	private int poppedElements = 0;
	
	public boolean isEmpty() {
		return infeasibleEdges.isEmpty() && restrictions.isEmpty();
	}
	
	public int getPoppedCount() {
		return poppedElements;
	}
	
	public void add(IntentionRestriction restriction) {
		MarkPathInfeasibleRestriction infeasible = ObjectUtil.castOrNull(restriction, MarkPathInfeasibleRestriction.class);
		if (infeasible != null) {
			addInfeasible(infeasible);
		} else {
			restrictions.put(restriction.intention, restriction);
		}
	}
	
	private void addInfeasible(MarkPathInfeasibleRestriction restriction) {
		infeasibleEdges.put(restriction.intention, 
				IntentionRestriction.merge(infeasibleEdges.get(restriction.intention), restriction));
	}

	public void addAll(Iterable<IntentionRestriction> restrictions) {
		for(IntentionRestriction restriction: restrictions) {
			add(restriction);
		}
	}
	
	public IntentionRestriction pop() throws NoSuchElementException {
		if (!infeasibleEdges.isEmpty()) {
			String intention = infeasibleEdges.keys().getFirst();
			poppedElements++;
			return infeasibleEdges.remove(intention);
		}
		
		String intention = restrictions.keys().getFirst();
		Set<IntentionRestriction> r = restrictions.get(intention);
		IntentionRestriction result = r.getFirst();
		restrictions.remove(intention, result);
		poppedElements++;
		return result;
	}

}
