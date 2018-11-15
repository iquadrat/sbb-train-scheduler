package org.povworld.sbb;

import javax.annotation.CheckForNull;

import org.povworld.collection.Set;
import org.povworld.collection.common.Interval;
import org.povworld.collection.common.PreConditions;
import org.povworld.collection.mutable.HashSet;
import org.povworld.collection.persistent.PersistentHashSet;
import org.povworld.collection.persistent.PersistentSet;
import org.povworld.sbb.RouteGraph.Edge;
import org.povworld.sbb.RouteGraph.Node;

public abstract class IntentionRestriction {
	public final String intention;

	public IntentionRestriction(String intention) {
		this.intention = intention;
	}

	public abstract Set<Node> applyTo(GraphConstraints timeConstraints);
	
	@CheckForNull
	public abstract Interval getRestrictionInterval();

	public static class IncreaseMinTimeRestriction extends IntentionRestriction {
		private final int minTime;
		private final Set<Edge> edges;

		public IncreaseMinTimeRestriction(String intention, int minTime, Set<Edge> edges) {
			super(intention);
			PreConditions.paramNotEmpty(edges);
			this.minTime = minTime;
			this.edges = edges;
		}

		@Override
		public Set<Node> applyTo(GraphConstraints timeConstraints) {
			HashSet<Node> affected = new HashSet<>();
			for (Edge edge: edges) {
				affected.addAll(timeConstraints.increaseEarliestEntry(edge, minTime));
			}
			return affected;
		}
		
		@Override
		public Interval getRestrictionInterval() {
			return new Interval(minTime, GraphConstraints.TMAX);
		}

		@Override
		public String toString() {
			return intention + ": start>=" + TimeUtil.printTime(minTime);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + minTime;
			result = prime * result + edges.hashCode();
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (!(obj instanceof IncreaseMinTimeRestriction))
				return false;
			IncreaseMinTimeRestriction other = (IncreaseMinTimeRestriction) obj;
			if (minTime != other.minTime)
				return false;
			if (!edges.equals(other.edges))
				return false;
			return true;
		}
	}

	public static class DecreaseMaxTimeRestriction extends IntentionRestriction {
		private final int maxTime;
		private final Set<Edge> edges;

		public DecreaseMaxTimeRestriction(String intention, int maxTime, Set<Edge> edges) {
			super(intention);
			PreConditions.paramNotEmpty(edges);
			this.maxTime = maxTime;
			this.edges = edges;
		}

		@Override
		public Set<Node> applyTo(GraphConstraints timeConstraints) {
			HashSet<Node> affected = new HashSet<>();
			for (Edge edge : edges) {
				affected.addAll(timeConstraints.decreaseLatestExit(edge, maxTime)); // TODO pass in edge set
			}
			return affected;
		}
		
		@Override
		public Interval getRestrictionInterval() {
			return new Interval(0, maxTime);
		}

		@Override
		public String toString() {
			return intention + ": end<" + TimeUtil.printTime(maxTime);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + maxTime;
			result = prime * result + edges.hashCode();
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (!(obj instanceof DecreaseMaxTimeRestriction))
				return false;
			DecreaseMaxTimeRestriction other = (DecreaseMaxTimeRestriction) obj;
			if (maxTime != other.maxTime)
				return false;
			if (!edges.equals(other.edges))
				return false;
			return true;
		}
	}

	public static class MarkPathInfeasibleRestriction extends IntentionRestriction {
		private final PersistentSet<Edge> edges;

		public MarkPathInfeasibleRestriction(String intention, Set<Edge> edges) {
			super(intention);
			PreConditions.paramCheck(edges, "is empty", !edges.isEmpty());
			this.edges = PersistentHashSet.copyOf(edges);
		}

		@Override
		public Set<Node> applyTo(GraphConstraints constraints) {
			return constraints.markInfeasible(edges);
		}
		
		@Override
		@CheckForNull
		public Interval getRestrictionInterval() {
			return null;
		}

		@Override
		public String toString() {
			return intention + ": infeasible " + edges;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + ((edges == null) ? 0 : edges.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (!(obj instanceof MarkPathInfeasibleRestriction))
				return false;
			MarkPathInfeasibleRestriction other = (MarkPathInfeasibleRestriction) obj;
			if (edges == null) {
				if (other.edges != null)
					return false;
			} else if (!edges.equals(other.edges))
				return false;
			return true;
		}

	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((intention == null) ? 0 : intention.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof IntentionRestriction))
			return false;
		IntentionRestriction other = (IntentionRestriction) obj;
		if (intention == null) {
			if (other.intention != null)
				return false;
		} else if (!intention.equals(other.intention))
			return false;
		return true;
	}
	
	public static MarkPathInfeasibleRestriction merge(@CheckForNull MarkPathInfeasibleRestriction existing, MarkPathInfeasibleRestriction restriction) {
		if (existing == null) {
			return restriction;
		}
		PreConditions.conditionCheck("Intention mismatch", existing.intention.equals(restriction.intention));
		return new MarkPathInfeasibleRestriction(existing.intention, existing.edges.withAll(restriction.edges));
	}
}