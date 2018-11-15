package org.povworld.sbb;

import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.povworld.collection.CollectionUtil;
import org.povworld.collection.List;
import org.povworld.collection.Map;
import org.povworld.collection.Set;
import org.povworld.collection.common.Assert;
import org.povworld.collection.common.PreConditions;
import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.collection.immutable.ImmutableList;
import org.povworld.collection.immutable.ImmutableMap;
import org.povworld.collection.mutable.ArrayList;
import org.povworld.collection.mutable.HashMap;
import org.povworld.collection.mutable.HashSet;
import org.povworld.collection.persistent.PersistentHashMap;
import org.povworld.collection.persistent.PersistentHashSet;
import org.povworld.collection.persistent.PersistentMap;
import org.povworld.collection.persistent.PersistentSet;
import org.povworld.sbb.Input.RouteSection;
import org.povworld.sbb.Input.SectionRequirement;
import org.povworld.sbb.Input.ServiceIntention;
import org.povworld.sbb.RouteGraph.Edge;
import org.povworld.sbb.RouteGraph.Node;

public class GraphConstraints {

	public static final int TMAX = TimeUtil.parseTime("30:00:00");
	public static final double EPS = Util.WEIGHT_EPS;
	
	private static final Logger logger = Logger.getLogger(GraphConstraints.class.getSimpleName());
	
	private static final class TimeConstraint {
		private final int entryEarliest;
		private final int exitLatest;

		public TimeConstraint() {
			this(0, TMAX);
		}
		
		private TimeConstraint(int entryEarliest, int exitLatest) {
			this.entryEarliest = entryEarliest;
			this.exitLatest = exitLatest;
		}
		
		public TimeConstraint withEntryEarliest(int time) {
			if (time <= this.entryEarliest) {
				return this;
			}
			return new TimeConstraint(time, exitLatest);
		}

		public TimeConstraint withExitLatest(int time) {
			if (time >= this.exitLatest) {
				return this;
			}
			return new TimeConstraint(entryEarliest, time);
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof TimeConstraint))
				return false;
			TimeConstraint other = (TimeConstraint) obj;
			if (exitLatest != other.exitLatest)
				return false;
			if (entryEarliest != other.entryEarliest)
				return false;
			return true;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + exitLatest;
			result = prime * result + entryEarliest;
			return result;
		}
	}
	
	private final RouteGraph graph;
	private final ImmutableMap<Edge, Integer> minDurations;
	private final Map<Node, Integer> latestAllowedTimes; 

	// Needs to be saved/restored:
	private PersistentMap<Node, Double> weights;
	private PersistentMap<Edge, TimeConstraint> edgeTimeConstraints;
	private PersistentSet<Edge> infeasibleEdges; // TODO replace by boolean on TimeConstraint?
	private PersistentMap<Node, Double> penaltyForward;
	private PersistentMap<Node, Double> penaltyBackward;
	private PersistentMap<Node, PenaltyTimeSet> penaltyTimeSets;
	private double maxPenalty;
	private double minPenalty;
	
	private boolean markedEdgeInfeasibleSinceLastUpdate = false;

	private GraphConstraints(RouteGraph graph, ImmutableMap<Edge, Integer> minDurations,
			PersistentMap<Node, Double> weights, PersistentMap<Edge, TimeConstraint> edgeTimeConstraints,
			PersistentSet<Edge> infeasibleEdges, PersistentMap<Node, Double> penaltyForward,
			PersistentMap<Node, Double> penaltyBackward, Map<Node, Integer> latestAllowedTimes,
			PersistentMap<Node, PenaltyTimeSet> penaltyTimeSets, 
			double maxPenalty, double minPenalty) {
		this.graph = graph;
		this.weights = weights;
		this.edgeTimeConstraints = edgeTimeConstraints;
		this.minDurations = minDurations;
		this.infeasibleEdges = infeasibleEdges;
		this.penaltyForward = penaltyForward;
		this.penaltyBackward = penaltyBackward;
		this.latestAllowedTimes = latestAllowedTimes;
		this.penaltyTimeSets = penaltyTimeSets;
		this.maxPenalty = maxPenalty;
		this.minPenalty = minPenalty;
	}
	
	public RouteGraph getGraph() {
		return graph;
	}
	
	public double getMinPenalty() {
		return minPenalty;
	}

	public GraphConstraints copy() {
		assertNotDirty();
		return new GraphConstraints(graph, minDurations, weights, edgeTimeConstraints, infeasibleEdges,
				penaltyForward, penaltyBackward, latestAllowedTimes, penaltyTimeSets,  
				maxPenalty, minPenalty);
	}

	private void assertNotDirty() {
		if (markedEdgeInfeasibleSinceLastUpdate && areFeasible()) {
			throw new IllegalStateException("Needs update!");
		}
	}
	
	public int getEntryEarliest(Edge edge) {
		return edgeTimeConstraints.get(edge).entryEarliest;
	}
	
	public int getExitLatest(Edge edge) {
		return edgeTimeConstraints.get(edge).exitLatest;
	}
	
	public int getEntryLatest(Edge edge) {
		return getLatestIncoming(edge.start);
	}
	
	public int getExitEarliest(Edge edge) {
		return getEarliestOutgoing(edge.end);
	}
	
	@CheckForNull
	public OccupationTimes getOccupationTimes(Edge start, Edge end, List<Edge> flow) {
		assertNotDirty();
		// TODO check if max penalty is violated 
		
		// Forward pass for earliest times:
		int entryEarliest = -1;
		int exitEarliest = -1;
		Edge first = flow.getFirst();
		double forwardWeight = weights.get(first.start);
		int current = 0;
		for(Edge edge: flow) {
			if (infeasibleEdges.contains(edge)) {
				return null;
			}
			
			TimeConstraint constraint = edgeTimeConstraints.get(edge);
			current = Math.max(current, constraint.entryEarliest);
			
			if (edge == start) {
				entryEarliest = current;
			}  
			
			int outCount = countFeasibleEdges(edge.start.getNext());
			if (outCount == 0) {
				throw new IllegalStateException("oops");
			}
			forwardWeight /= outCount;
			
			current = Math.max(current + minDurations.get(edge), getEarliestOutgoing(edge.end));
			
			if (edge == end) {
				exitEarliest = current;
			}
		}
		if (forwardWeight <= 0 || Double.isNaN(forwardWeight) || Double.isInfinite(forwardWeight)) {
			throw new IllegalStateException("Invalid weight: "+ forwardWeight);
		}
		if (entryEarliest == -1 || exitEarliest == -1) {
			throw new IllegalStateException("entry / exit nodes not found");
		}

		if (Debug.ENABLE_TIME_CONSTRAINS_CONSISTENCY_CHECKS) {
			int endCont = getEarliestOutgoing(end.end);
			Assert.assertTrue(exitEarliest >= endCont, "No possible continuation at end!");
		}
		
		// Backward pass for latest times:
		int entryLatest = -1;
		int exitLatest = -1;
		double backwardWeight = weights.get(flow.getFirst().start);
		current = TMAX;
		for(Edge edge: CollectionUtil.reverse(flow)) {
			if (infeasibleEdges.contains(edge)) {
				return null;
			}
			
			TimeConstraint constraint = edgeTimeConstraints.get(edge);
			current = Math.min(current, constraint.exitLatest);
			
			if (edge == end) {
				exitLatest = current;
			}
			
			int outCount = countFeasibleEdges(edge.start.getNext());
			if (outCount == 0) {
				throw new IllegalStateException("oops");
			}
			backwardWeight /= outCount;
			current = Math.min(current - minDurations.get(edge), getLatestIncoming(edge.start));
			
			if (edge == start) {
				entryLatest = current;
			}
		}
		if (entryLatest == -1 || exitLatest == -1) {
			throw new IllegalStateException("entry / exit nodes not found");
		}
		
		if (Debug.ENABLE_TIME_CONSTRAINS_CONSISTENCY_CHECKS) {
			int startCont = getLatestIncoming(start.start);
			Assert.assertTrue(entryLatest <= startCont, "No possible continuation at start!");
		}
		
		if (Debug.ENABLE_TIME_CONSTRAINS_CONSISTENCY_CHECKS) {
			if (backwardWeight != forwardWeight) {
				throw new IllegalStateException("Inconsistent weights!");
			}
		}
		
		if (entryLatest < entryEarliest || exitLatest < exitEarliest) {
			return null;
		}
		
		return new OccupationTimes(entryEarliest, exitEarliest, entryLatest, exitLatest, forwardWeight);
	}
	
	public Set<Edge> getInfeasibleEdges() {
		return infeasibleEdges;
	}
	
	public boolean isFeasible(Edge edge) {
		return !infeasibleEdges.contains(edge);
	}

	public boolean areFeasible() {
		return infeasibleEdges.size() < graph.getEdges().size() && minPenalty <= maxPenalty;
	}
	
	public double getWeight(Node node) {
		return weights.get(node);
	}

	// TODO should this return edges instead?
	public Set<Node> increaseEarliestEntry(Edge edge, int entryEarliest) {
		assertFeasability();
		TimeConstraint constraint = edgeTimeConstraints.get(edge);
		if (!isFeasible(edge) || constraint.entryEarliest >= entryEarliest) {
			return ImmutableCollections.setOf();
		}
		edgeTimeConstraints = edgeTimeConstraints.with(edge, constraint.withEntryEarliest(entryEarliest));
		
		final HashSet<Node> changeSet = new HashSet<>();
		
		final HashMap<Node, Integer> minTime = new HashMap<>();
		minTime.put(edge.start, entryEarliest);
		new ForwardUpdater(edge) {
			@Override
			protected void update(Edge edge) {
				if (!isFeasible(edge)) {
					return;
				}
				changeSet.add(edge.end);
				
				// Need to update change set as long as 'now' is lower than earliest entry.
				int minDuration = minDurations.get(edge);
				int earliestOutgoingEnd = getEarliestOutgoing(edge.end);
				int now = minTime.getOrDefault(edge.start, 0) + minDuration;
				if (now > earliestOutgoingEnd) {
					minTime.put(edge.end, now);
					for(Edge e: edge.end.getNext()) {
						addPending(e);
					}
				}
			}
		}.run();

		changeSet.addAll(updateForIncreasedEarliestEntry(edge));
		changeSet.add(edge.start);
		changeSet.add(edge.end);
		changeSet.addAll(update());
		
		assertFeasability();
		return changeSet;
	}

	public Set<Node> decreaseLatestExit(Edge edge, int exitLatest) {
		TimeConstraint constraint = edgeTimeConstraints.get(edge);
		if (!isFeasible(edge) || constraint.exitLatest <= exitLatest) {
			return ImmutableCollections.setOf();
		}
		edgeTimeConstraints = edgeTimeConstraints.with(edge, constraint.withExitLatest(exitLatest));
		
		final HashSet<Node> changeSet = new HashSet<>();
		
		final HashMap<Node, Integer> maxTime = new HashMap<>();
		maxTime.put(edge.end, exitLatest);
		new BackwardUpdater(edge) {
			@Override
			protected void update(Edge edge) {
				if (!isFeasible(edge)) {
					return;
				}
				changeSet.add(edge.start);
				
				// Need to update change set as long as 'now' is larger than latest exit.
				int minDuration = minDurations.get(edge);
				int latestIncomingStart = getLatestIncoming(edge.start);
				int now = maxTime.getOrDefault(edge.end, 0) - minDuration;
				if (now < latestIncomingStart) {
					maxTime.put(edge.start, now);
					for(Edge e: edge.start.getPrevious()) {
						addPending(e);
					}
				}
				
			}
		}.run();
		
		changeSet.addAll(updateForDecreasedLatestExit(edge));
		changeSet.add(edge.start);
		changeSet.add(edge.end);
		changeSet.addAll(update());
		
		assertFeasability();
		return changeSet;
	}
	
	
	private HashSet<Node> updateForIncreasedEarliestEntry(Edge edge) {
		EdgeUpdater updater = new EdgeUpdater();
		if (latestAllowedTimes.containsKey(edge.start)) {
			updater.penaltyNodeAffected = true;
		}
		updater.pending.add(edge);
		updater.pending.addAll(edge.start.getPrevious());
		updater.pending.addAll(edge.end.getNext());
		return updater.run();
	}
	
	private HashSet<Node> updateForDecreasedLatestExit(Edge edge) {
		EdgeUpdater updater = new EdgeUpdater();
		updater.pending.add(edge);
		updater.pending.addAll(edge.start.getPrevious());
		updater.pending.addAll(edge.end.getNext());
		return updater.run();
	}
	
	private HashSet<Node> updateForNewInfeasible(Set<Edge> edges) {
		EdgeUpdater updater = new EdgeUpdater();
		for(Edge edge: edges) {
			updater.pending.addAll(edge.start.getPrevious());
			updater.pending.addAll(edge.end.getNext());
		}
		return updater.run();
	}
	
	private class EdgeUpdater {
		private final HashSet<Edge> pending = new HashSet<>();
		private final HashSet<Node> changed = new HashSet<>();
		
		// TODO we only actually need to trigger this if we increase the node's earliest time!
		private boolean penaltyNodeAffected = false;

		HashSet<Node> run() {
			while(!pending.isEmpty()) {
				Edge edge = pending.getFirst();
				pending.remove(edge);
				process(edge);
			}
			if (penaltyNodeAffected) {
				updatePathPenalties();
				//changed.addAll(GraphConstraints.this.update());
			}
			return changed;
		}
		
		private void process(Edge edge) {
			if (!isFeasible(edge)) {
				return;
			}
			TimeConstraint constraint = edgeTimeConstraints.get(edge);
			int minDuration = minDurations.get(edge);
			
			// Update entryEarliest:
			if (!edge.start.isSource()) {
				int earliestIncoming = getEarliestIncoming(edge.start);
				if (earliestIncoming > constraint.entryEarliest) {
					constraint = constraint.withEntryEarliest(earliestIncoming);
					pending.addAll(edge.end.getNext());
					changed.add(edge.start);
					changed.add(edge.end);
					if (latestAllowedTimes.containsKey(edge.start)) {
						penaltyNodeAffected = true;
					}
				}
			}
			
			// Update exitLatest:
			if (!edge.end.isSink()) {
				int latestOutgoing = getLatestOutgoing(edge.end);
				if (latestOutgoing < constraint.exitLatest) {
					constraint = constraint.withExitLatest(latestOutgoing);
					pending.addAll(edge.start.getPrevious());
					changed.add(edge.start);
					changed.add(edge.end);
				}
			}
			
			edgeTimeConstraints = edgeTimeConstraints.with(edge, constraint);
			
			// Check if edge has become infeasible:
			if ((constraint.exitLatest - constraint.entryEarliest < minDuration) || 
				(getLatestIncoming(edge.start) < constraint.entryEarliest) || 
				(getEarliestOutgoing(edge.end) > constraint.exitLatest)) {
				markInfeasibleNoUpdate(edge);
				pending.addAll(edge.start.getPrevious());
				pending.addAll(edge.end.getNext());
				if (latestAllowedTimes.containsKey(edge.start)) {
					penaltyNodeAffected = true;
				}
			}
		}
	}
	
	private int getEarliestIncoming(Node node) {
		PreConditions.paramCheck(node, "is source", !node.isSource());
		int earliestEntry = TMAX;
		for(Edge prev: node.getPrevious()) {
			if (!isFeasible(prev)) {
				continue;
			}
			int earliestFromPrev = edgeTimeConstraints.get(prev).entryEarliest + minDurations.get(prev); 
			earliestEntry = Math.min(earliestEntry, earliestFromPrev);
		}
		return earliestEntry;
	}
	
	private int getLatestIncoming(Node node) {
		PreConditions.paramCheck(node, "is sink", !node.isSink());
		int latestArrival = 0;
		for(Edge prev: node.getPrevious()) {
			if (!isFeasible(prev)) {
				continue;
			}
			int latestFromPrev = edgeTimeConstraints.get(prev).exitLatest; 
			latestArrival = Math.max(latestArrival, latestFromPrev);
		}
		return latestArrival;
	}
	
	private int getLatestOutgoing(Node node) {
		PreConditions.paramCheck(node, "is sink", !node.isSink());
		int latestExit = 0;
		for(Edge next: node.getNext()) {
			if (!isFeasible(next)) {
				continue;
			}
			int latestToNext = edgeTimeConstraints.get(next).exitLatest - minDurations.get(next);
			latestExit = Math.max(latestExit, latestToNext);
		}
		return latestExit;
	}

	private int getEarliestOutgoing(Node node) {
		PreConditions.paramCheck(node, "is source", !node.isSource());
		int earliestDeparture = TMAX;
		for(Edge next: node.getNext()) {
			if (!isFeasible(next)) {
				continue;
			}
			int earliestToNext = edgeTimeConstraints.get(next).entryEarliest;
			earliestDeparture = Math.min(earliestDeparture, earliestToNext);
		}
		return earliestDeparture;
	}
	
	private void assertFeasability() {
		if (!Debug.ENABLE_TIME_CONSTRAINS_CONSISTENCY_CHECKS) {
			return;
		}
		Assert.assertEquals(edgeTimeConstraints.keyCount(), graph.getEdges().size());
		
		for(Node node: graph.getNodes()) {
			if (node.isSource() || node.isSink()) {
				continue;
			}
			if (!hasFeasibleEdge(node.getPrevious())) {
				Assert.assertFalse(hasFeasibleEdge(node.getNext()), "Node with feasible in but no out edges!");
			} else if (!hasFeasibleEdge(node.getNext())) {
				Assert.assertFalse(hasFeasibleEdge(node.getPrevious()), "Node with feasible out but no in edges!");
			} else {
				int min = getEarliestIncoming(node);
				int max = getLatestOutgoing(node);
				Assert.assertTrue(min <= max, "Infeasible node!");
			}
		}
		
		for (Edge edge : graph.getEdges()) {
			if (!isFeasible(edge)) {
				continue;
			}
			TimeConstraint constraint = edgeTimeConstraints.get(edge);
			int minDuration = minDurations.get(edge);
			Assert.assertTrue(constraint.exitLatest - constraint.entryEarliest >= minDuration, "min duration violation");

			if (!edge.start.isSource()) {
				int maxExit = 0;
				for (Edge prev : edge.start.getPrevious()) {
					if (!isFeasible(prev)) {
						continue;
					}
					maxExit = Math.max(maxExit, edgeTimeConstraints.get(prev).exitLatest);
				}
				Assert.assertTrue(constraint.entryEarliest <= maxExit, "in connection violation %d <= %d", constraint.entryEarliest, maxExit);
			}

			if (!edge.end.isSink()) {
				int minEntry = TMAX;
				for (Edge next : edge.end.getNext()) {
					if (!isFeasible(next)) {
						continue;
					}
					minEntry = Math.min(minEntry, edgeTimeConstraints.get(next).entryEarliest);
				}
				Assert.assertTrue(constraint.exitLatest >= minEntry, "out connection violation %d >= %d", constraint.exitLatest, minEntry);
			}
		}
		
	}
	
	public PathSchedule scheduleMinimumPenaltyPath() {
		// TODO this could be done much more efficiently..
		RouteGroup routes = graph.generatePaths();
		HashMap<Path, Double> penalties = new HashMap<>();
		for(Path path: routes.getPaths()) {
			int[] nodeTimes = getNodeTimes(path);
			double penalty = 0;
			if (nodeTimes == null) {
				penalty = Double.POSITIVE_INFINITY;
			} else {
				for (int i = 0; i < nodeTimes.length; ++i) {
					penalty += 1.0 / 60 * Math.max(0, nodeTimes[i] - latestAllowedTimes.getOrDefault(path.getEdges().get(i).end, TMAX));
				}
				penalty += path.getPenalty();
			}
			penalties.put(path, penalty);
		}
		
		ArrayList<Path> paths = CollectionUtil.sort(routes.getPaths(), new Comparator<Path>() {
			@Override
			public int compare(Path p1, Path p2) {
				return Double.compare(penalties.get(p1), penalties.get(p2));
			}
		});
		for(Path path: paths) {
			int[] nodeTimes = getNodeTimes(path);
			if (nodeTimes != null) {
				double penalty = penalties.get(path);
				if (penalty > 0) {
					logger.log(Level.INFO, "Path for " + getGraph().getId() + " has penalty "+penalty);
				}
				return new PathSchedule(path, nodeTimes, penalty);
			}
		}
		throw new IllegalStateException("No feasible path");
	}

	private int[] getNodeTimes(Path path) {
		ImmutableList<Edge> flow = path.getEdges();
		int[] nodeTimes = new int[flow.size() - 1];
		int now = 0;
		int i = -1;
		for(Edge edge: flow) {
			if (infeasibleEdges.contains(edge)) {
				return null;
			}
			TimeConstraint constraint = edgeTimeConstraints.get(edge);
			now = Math.max(now, constraint.entryEarliest);
			
			if (!edge.start.isSource()) {
				nodeTimes[i] = now;
			}
			
			now = now + minDurations.get(edge);
			if (now > constraint.exitLatest) {
				return null;
			}
			i++;
		}
		return nodeTimes;
	}

	public Set<Node> markInfeasible(Iterable<Edge> edges) {
		HashSet<Edge> newInfeasible = new HashSet<>();
		for(Edge e: edges) {
			if (markInfeasibleNoUpdate(e)) {
				newInfeasible.add(e);
			}
		}
		updateForNewInfeasible(newInfeasible);
		return update();
	}
	
	
	public Set<Node> setMaxPenalty(double penalty) {
		PreConditions.conditionCheck("Tried to increase max penalty!", Util.penaltyLess(penalty, maxPenalty));
		if (penalty >= maxPenalty) {
			return ImmutableCollections.setOf();
		}
		this.maxPenalty = penalty;

		HashSet<Node> changes = updateMaxTimes();
		changes.addAll(update());
		assertNotDirty();
		return changes;
	}
	
	private HashSet<Node> updateMaxTimes() {
		HashSet<Node> changed = new HashSet<>();
		for (Node node : CollectionUtil.reverse(graph.getTopologicallySortedNodes())) {
			if (node.isSource() || node.isSink()) {
				continue;
			}
			PenaltyTimeSet pts = penaltyTimeSets.get(node);
			Integer latestAllowedTime = latestAllowedTimes.get(node);
			if (latestAllowedTime != null) {
				if (pts == null) {
					pts = new PenaltyTimeSet(latestAllowedTime);
				} else {
					pts = pts.with(latestAllowedTime);
				}
			}
			
			int latest = pts.maximumTime(maxPenalty);
			for (Edge prev : node.getPrevious()) {
				changed.addAll(decreaseLatestExit(prev, latest));
			}
			
			for (Edge prev : node.getPrevious()) {
				Integer edgeTime = minDurations.get(prev);
				
				// FIXME penaltyTimeSets should never change
				PenaltyTimeSet min = PenaltyTimeSet.min(pts.subtract(edgeTime), penaltyTimeSets.get(prev.start));
				penaltyTimeSets = penaltyTimeSets.with(prev.start, min);
			}
		}
		return changed;
	}
	
	private boolean markInfeasibleNoUpdate(Edge edge) {
		PersistentSet<Edge> updatedEdges = infeasibleEdges.with(edge);
		if (updatedEdges == infeasibleEdges) {
			return false;
		}
		infeasibleEdges = updatedEdges;
		markedEdgeInfeasibleSinceLastUpdate = true;
		return true;
	}
	
	private boolean hasFeasibleEdge(Iterable<Edge> edges) {
		for(Edge edge: edges) {
			if (!infeasibleEdges.contains(edge)) {
				return true;
			}
		}
		return false;
	}
	
	private int countFeasibleEdges(Iterable<Edge> edges) {
		int feasible = 0;
		for(Edge edge: edges) {
			if (!infeasibleEdges.contains(edge)) {
				feasible++;
			}
		}
		return feasible;
	}
	
	private Set<Node> update() {
		if (!markedEdgeInfeasibleSinceLastUpdate || !areFeasible()) {
			return ImmutableCollections.setOf();
		}
		updatePathPenalties();
		updateWeights();
		markedEdgeInfeasibleSinceLastUpdate = false;
		return graph.getNodes();
	}

	private Set<Node> updateWeights() {
		HashSet<Node> startNodes = new HashSet<>();
		for(Edge edge: graph.getSource().getNext()) {
			Node node = edge.end;
			if (hasFeasibleEdge(node.getNext())) {
				startNodes.add(node);
			} else {
				weights = weights.with(node, 0.0);
			}
		}

		HashSet<Node> changed = new HashSet<>();
		// Weights
		for (Node node : startNodes) {
			double weight = 1.0 / startNodes.size();
			if (weights.get(node) != weight) {
				weights = weights.with(node, weight);
				changed.add(node);
			}
		}

		for (Node node : graph.getTopologicallySortedNodes()) {
			if (node.isSource()) {
				continue;
			}
			double weight = 0;
			for (Edge edge : node.getPrevious()) {
				if (infeasibleEdges.contains(edge)) {
					continue;
				}
				weight += weights.get(edge.start) / countFeasibleEdges(edge.start.getNext());
			}
			if (weights.get(node) != weight) {
				weights = weights.with(node, weight);
				changed.add(node);
			}
		}
		
		// FIXME can we calculate the actually changed nodes correctly?
		return graph.getNodes();
	}
	
	private void updatePathPenalties() {
		// Forward pass for path penalty
		for (Node node : graph.getTopologicallySortedNodes()) {
			if (node.isSource()) {
				continue;
			}
			
			double incomingPenalty = Double.POSITIVE_INFINITY;
			for (Edge edge : node.getPrevious()) {
				if (infeasibleEdges.contains(edge)) {
					continue;
				}
				double penalty = penaltyForward.getOrDefault(edge.start, Util.ZERO) + edge.getPenalty();
				incomingPenalty = Math.min(incomingPenalty, penalty);
			}
			// TODO could cache delay penalty for node instead of calculating twice
			double delayPenalty = 1.0 / 60
					* Math.max(0, getEarliestIncoming(node) - latestAllowedTimes.getOrDefault(node, TMAX));
			double totalPenalty = incomingPenalty + delayPenalty;
			if (totalPenalty != penaltyForward.getOrDefault(node, Util.ZERO)) {
				penaltyForward = penaltyForward.with(node, totalPenalty);
			}
		}			
		
		// Backward pass for penalty
		for (Node node : CollectionUtil.reverse(graph.getTopologicallySortedNodes())) {
			if (node.isSource() || node.isSink()) {
				continue;
			}
			double outgoingPenalty = Double.POSITIVE_INFINITY;
			for (Edge edge : node.getNext()) {
				if (infeasibleEdges.contains(edge)) {
					continue;
				}
				double penalty = penaltyBackward.getOrDefault(edge.end, Util.ZERO) + edge.getPenalty();
				outgoingPenalty = Math.min(penalty, outgoingPenalty);
			}
			double delayPenalty = 1.0 / 60
					* Math.max(0, getEarliestIncoming(node) - latestAllowedTimes.getOrDefault(node, TMAX));
			double totalPenalty = outgoingPenalty + delayPenalty;
			if (totalPenalty != penaltyBackward.getOrDefault(node, Util.ZERO)) {
				penaltyBackward = penaltyBackward.with(node, totalPenalty);
			}
		}	
		
		double minStartPenalty = Double.POSITIVE_INFINITY;
		for(Edge edge: graph.getSource().getNext()) {
			minStartPenalty = Math.min(minStartPenalty, penaltyBackward.getOrDefault(edge.end, Util.ZERO));
		}
		
		if (Debug.ENABLE_TIME_CONSTRAINS_CONSISTENCY_CHECKS) {
			double minEndPenalty = Double.POSITIVE_INFINITY;
			for(Edge edge: graph.getSink().getPrevious()) {
				minEndPenalty = Math.min(minEndPenalty, penaltyForward.getOrDefault(edge.start, Util.ZERO));
			}
			Assert.assertTrue(Util.penaltyEquals(minStartPenalty, minEndPenalty),
					"Start and end penalty do not match! %s vs %s", minStartPenalty, minEndPenalty);
		}
		
		HashSet<Edge> newInfeasible = new HashSet<Edge>();
		for (Node node : graph.getNodes()) {
			// TODO check for the edges instead of nodes.
			double totalPenalty = penaltyBackward.getOrDefault(node, Util.ZERO) 
				+ penaltyForward.getOrDefault(node, Util.ZERO); 
			if (totalPenalty > maxPenalty) {
				for (Edge e: node.getNext()) {
					if (markInfeasibleNoUpdate(e)) {
						newInfeasible.add(e);
					}
				}
				for (Edge e: node.getPrevious()) {
					if (markInfeasibleNoUpdate(e)) {
						newInfeasible.add(e);
					}
				}
			}
		}
		if (minStartPenalty > 0) {
			minPenalty = minStartPenalty;
			//logger.log(Level.INFO, "Min penalty for " + graph.getId() + " is " + minPenalty);
		}
		if (!newInfeasible.isEmpty()) {
			// TODO do we care about changed nodes?
			updateForNewInfeasible(newInfeasible);
		}
	}

	private abstract class Updater {
		private final HashSet<Edge> pending = new HashSet<>();

		Updater(Edge edge) {
			pending.add(edge);
		}

		void run(List<Edge> sortedEdges) {
			for (Edge edge: sortedEdges) {
				if (pending.remove(edge) && isFeasible(edge)) {
					update(edge);
				}

				if (pending.isEmpty()) {
					break;
				}
			}
			done();
		}

		protected abstract void run();

		protected abstract void update(Edge edge);
		
		protected void done() {}

		protected void addPending(Edge edge) {
			pending.add(edge);
		}

	}

	private abstract class ForwardUpdater extends Updater {
		ForwardUpdater(Edge start) {
			super(start);
		}

		public final void run() {
			run(graph.getTopologicallySortedEdges());
		}
	}

	private abstract class BackwardUpdater extends Updater {
		BackwardUpdater(Edge end) {
			super(end);
		}

		public final void run() {
			run(CollectionUtil.reverse(graph.getTopologicallySortedEdges()));
		}
	}
	
	public static GraphConstraints create(RouteGraph graph, ServiceIntention intention) {
		return create(graph, intention, 0);
	}

	public static GraphConstraints create(RouteGraph graph, ServiceIntention intention, double maxPenalty) {
		Map<Edge, Integer> minDurations = GraphResourceOccupations.findEdgeMinDurations(graph, intention);
		GraphConstraints constraints = new Builder(intention, graph, minDurations).build();
		// Initially fill correct values.
		constraints.updateWeights();
		constraints.updatePathPenalties();
		// Update max penalty.
		constraints.setMaxPenalty(
				Math.min(Debug.MAX_PENALTY_PER_INTENTION, maxPenalty));
		return constraints;
	}

	private static class Builder {
		private static final SectionRequirement NO_REQUIREMENTS = SectionRequirement.newBuilder().build();
		final ServiceIntention intention;
		final RouteGraph graph;
		final Map<Edge, Integer> minDurations;

		final HashMap<String, SectionRequirement> requirements = new HashMap<>();
		final HashMap<Edge, TimeConstraint> timeConstraints = new HashMap<>();
		final HashMap<Node, Double> weights = new HashMap<>();

		Builder(ServiceIntention intention, RouteGraph graph, Map<Edge, Integer> minDurations) {
			this.intention = intention;
			this.graph = graph;
			this.minDurations = minDurations;
		}

		GraphConstraints build() {
			List<Node> sortedNodes = graph.getTopologicallySortedNodes();

			// Weights
			weights.put(graph.getSource(), 1.0);
			weights.put(graph.getSink(), 1.0);

			for (Node node : sortedNodes) {
				if (!node.isSource()) {
					double weight = 0;
					for (Node prev : node.getPreviousNodes()) {
						weight += weights.get(prev) / prev.getNext().size();
					}
					weights.put(node, weight);
				}
			}

			// Requirements
			for (SectionRequirement r : intention.getSectionRequirementsList()) {
				requirements.put(r.getSectionMarker(), r);
				if (r.getEntryDelayWeight() != 0 && r.getEntryDelayWeight() != 1) {
					throw new RuntimeException("Unexpected delay weight: " + r.getEntryDelayWeight());
				}
				if (r.getExitDelayWeight() != 0 && r.getExitDelayWeight() != 1) {
					throw new RuntimeException("Unexpected delay weight: " + r.getExitDelayWeight());
				}
			}
			
			HashMap<Node, Integer> latestAllowedTimes = new HashMap<>();
			PersistentMap<Node, PenaltyTimeSet> penaltyTimeSets = PersistentHashMap.empty();
			for (Edge edge : graph.getEdges()) {
				SectionRequirement r = getSectionRequirement(edge);
				if (!r.getEntryEarliest().isEmpty()) {
					int t = TimeUtil.parseTime(r.getEntryEarliest());
					timeConstraints.put(edge, getTimeConstraint(edge).withEntryEarliest(t));
				}
				if (!r.getExitEarliest().isEmpty()) {
					int t = TimeUtil.parseTime(r.getExitEarliest());
					// FIXME check if exit earliest is always also set as entry earliest on next
					for(Edge next: edge.end.getNext()) {
						timeConstraints.put(next, getTimeConstraint(next).withEntryEarliest(t));
					}
				}
				if (!r.getEntryLatest().isEmpty()) {
					// FIXME there could also be difference between entry and exit times here
					int t = TimeUtil.parseTime(r.getEntryLatest());
					latestAllowedTimes.put(edge.start, t);
				}
				if (!r.getExitLatest().isEmpty()) {
					int t = TimeUtil.parseTime(r.getExitLatest());
					latestAllowedTimes.put(edge.end, t);
				}
			}

			// Earliest times (min)
			for (Node node : sortedNodes) {
				if (node.isSource()) {
					continue;
				}
				
				int earlistArrival = TMAX;
				for(Edge prev: node.getPrevious()) {
					earlistArrival = Math.min(earlistArrival,
							getTimeConstraint(prev).entryEarliest + minDurations.get(prev));
				}
				
				for (Edge next : node.getNext()) {
					timeConstraints.put(next, getTimeConstraint(next).withEntryEarliest(earlistArrival));
				}
			}
			
			return new GraphConstraints(graph, 
					PersistentHashMap.copyOf(minDurations),
					PersistentHashMap.copyOf(weights),
					PersistentHashMap.copyOf(timeConstraints),
					PersistentHashSet.empty(),
					PersistentHashMap.empty(),
					PersistentHashMap.empty(),
					latestAllowedTimes,
					penaltyTimeSets,
					Double.POSITIVE_INFINITY,
					0.0);
		}

		private TimeConstraint getTimeConstraint(Edge edge) {
			TimeConstraint c = timeConstraints.get(edge);
			if (c == null) {
				c = new TimeConstraint();
				timeConstraints.put(edge, c);
			}
			return c;
		}

		private SectionRequirement getSectionRequirement(Edge edge) {
			RouteSection section = edge.section;
			SectionRequirement r = NO_REQUIREMENTS;
			if (section.getSectionMarkerCount() > 0) {
				r = requirements.get(section.getSectionMarker(0));
				if (r == null) {
					r = NO_REQUIREMENTS;
				}
			}
			return r;
		}
	}

}
