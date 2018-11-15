package org.povworld.sbb;

import static org.povworld.sbb.Debug.SOLVER;

import java.util.Comparator;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.CheckReturnValue;

import org.povworld.backtrack.Executor;
import org.povworld.collection.CollectionUtil;
import org.povworld.collection.List;
import org.povworld.collection.Set;
import org.povworld.collection.common.Assert;
import org.povworld.collection.immutable.ImmutableSet;
import org.povworld.collection.mutable.ArrayList;
import org.povworld.collection.mutable.HashMap;
import org.povworld.collection.mutable.HashSet;
import org.povworld.collection.mutable.HashMultiMap;
import org.povworld.collection.persistent.PersistentHashMap;
import org.povworld.collection.persistent.PersistentMap;
import org.povworld.sbb.ConnectionRepository.Connection;
import org.povworld.sbb.GraphResourceOccupations.ResourceOccupation;
import org.povworld.sbb.Input.Resource;
import org.povworld.sbb.Input.ServiceIntention;
import org.povworld.sbb.ResourcePlan.Max;
import org.povworld.sbb.RouteGraph.Edge;
import org.povworld.sbb.RouteGraph.Node;

public class SbbExecutor implements Executor<State, Conflict, ConflictSchedule> {
	
	private static final Logger logger = Logger.getLogger(SbbExecutor.class.getSimpleName());
	
	private final Problem problem;
	private final double maxPenalty;
	private final ConnectionRepository connections;
	private final ConflictBooster conflictBooster;

	public SbbExecutor(Problem problem, double maxPenalty, ConnectionRepository connections, ConflictBooster conflictBooster) {
		this.problem = problem;
		this.maxPenalty = maxPenalty;
		this.connections = connections;
		this.conflictBooster = conflictBooster;
	}
	
	public State createInitialState() {
		State state = new State(
				createResourcePlans(),
				createGraphConstraints(),
				0);
		state = new StateChanger(problem, maxPenalty, connections, state).syncAllResourcePlans();
		return state;
	}
		
    private PersistentMap<String, GraphConstraints> createGraphConstraints() {
    	PersistentMap<String, GraphConstraints> graphConstraints = PersistentHashMap.empty();
		for (ServiceIntention si : problem.getScenario().getServiceIntentionsList()) {
			RouteGraph graph = problem.getRouteGraph(si.getRoute());
			GraphConstraints timeConstraints = GraphConstraints.create(graph, si, maxPenalty);
			graphConstraints = graphConstraints.with(si.getId(), timeConstraints);
		}
		return graphConstraints;
	}

	private PersistentMap<String, ResourcePlan> createResourcePlans() { 
    	PersistentMap<String, ResourcePlan> resourcePlans = PersistentHashMap.empty();		
		HashMap<String, HashMultiMap<String, ResourceOccupation>> resourceOccupationsByResourceAndIntention = new HashMap<>();
		for(Resource resource : problem.getResources()) { 
			resourceOccupationsByResourceAndIntention.put(resource.getId(), new HashMultiMap<>());
		}
		for(ServiceIntention si: problem.getScenario().getServiceIntentionsList()) {
			GraphResourceOccupations gro = problem.getResourceOccupations(si.getId());
			for(String resource: gro.getOccupiedResources()) {
				resourceOccupationsByResourceAndIntention.get(resource).putAll(si.getId(), gro.getOccupations(resource));
			}
		}
		
		for (Resource resource : problem.getResources()) {
			double boost = 1.0;
			resourcePlans = resourcePlans.with(resource.getId(),
					new ResourcePlan(resource.getId(), resource.getReleaseTimeSeconds(), boost,
							resourceOccupationsByResourceAndIntention.get(resource.getId())));
		}
		return resourcePlans;
	}

	@Override
	public Conflict getWorstConflict(State state) {
		Conflict result = null;
		for (ResourcePlan resourcePlan : state.resourcePlans.values()) {
			Conflict conflict = getConflict(resourcePlan); 		
			if (conflict != null && (result == null || conflict.badness > result.badness)) {
				result = conflict;
			}
		}
		for(Connection connection: connections.getAll()) {
			// TODO store ConnectionOccupations in ConnectionRepository
			//System.out.println(connection + " -> " + occupation + " " + occupation.getBadness());
			Conflict conflict = getConflict(state, connection);
			if (conflict != null && (result == null || conflict.badness > result.badness)) {
				result = conflict;
			}
		}
		return result;
	}
	
	@CheckForNull
	private Conflict getConflict(ResourcePlan resourcePlan) {
		Max maxDensityRange = resourcePlan.getMaxDensityRange(Debug.CONFLICT_SCHEDULE);
		if (maxDensityRange == null) {
			return null;
		}
		double maxDensity = maxDensityRange.density * conflictBooster.getBoost(resourcePlan.getResource());
		Assert.assertTrue(maxDensity > 0, "zero density");
		return new Conflict(maxDensity, resourcePlan.getResource(), maxDensityRange.interval);
	}

	@CheckForNull
	private Conflict getConflict(State state, Connection connection) {
		ConnectionOccupation occupation = ConnectionOccupation.create(connection, state.graphConstraints);
		if (occupation.getBadness() <= 0) {
			return null;
		}
		return new Conflict(occupation.getBadness(), occupation);
	}

	@Override
	public List<ConflictSchedule> getOptions(State state, Conflict conflict) {
		assertResourcePlanAndTimeConstrainsAreConsistent(problem, state.resourcePlans, state.graphConstraints);
		
		List<ConflictSchedule> conflictSchedules;
		if (conflict.resource != null) {
			ResourcePlan plan = state.resourcePlans.get(conflict.getResource());
			conflictSchedules = 
					plan.createConflictSchedules(conflict.range);
		} else {
			conflictSchedules = conflict.connectionOccupation.createConflictSchedules();
		}
		
		if (maxPenalty > 0) {
			conflictSchedules = rescore(state, conflictSchedules);
		}
		
		conflictSchedules = CollectionUtil.sort(conflictSchedules, new Comparator<ConflictSchedule>() {
			@Override
			public int compare(ConflictSchedule s1, ConflictSchedule s2) {
				return s1.badness.compareTo(s2.badness);
			}
		});
		
		printConflictSchedule(conflict, conflictSchedules);
		
		return conflictSchedules;
	}

	private List<ConflictSchedule> rescore(State state, List<ConflictSchedule> conflictSchedules) {
		HashSet<String> intentions = new HashSet<>();
		for (ConflictSchedule schedule : conflictSchedules) {
			for (IntentionRestriction r : schedule.restrictions) {
				intentions.add(r.intention);
			}
		}
		
		ArrayList<ConflictSchedule> result = new ArrayList<>(conflictSchedules.size());
		
		for(ConflictSchedule schedule: conflictSchedules) {
			HashMap<String, GraphConstraints> constraints = new HashMap<>();
			HashMap<String, ResourcePlan> modifiedPlans = new HashMap<>();
			
			for(IntentionRestriction restriction: schedule.restrictions) {
				String intention = restriction.intention;
				GraphConstraints constraint = state.graphConstraints.get(intention).copy();
				constraints.put(intention, constraint);
			}
			
			IntentionRestrictionQueue queue = new IntentionRestrictionQueue();
			queue.addAll(schedule.restrictions);
			
			while(!queue.isEmpty()) {
				final IntentionRestriction restriction = queue.pop();
				final GraphConstraints constraint = constraints.get(restriction.intention);
				if (constraint == null) {
					continue;
				}
				
				Set<Node> changedNodes = restriction.applyTo(constraint);

				HashSet<ResourceOccupation> needsUpdate = new HashSet<>();
				GraphResourceOccupations graphResourceOccupations = problem.getResourceOccupations(restriction.intention);
				for (Node node : changedNodes) {
					needsUpdate.addAll(graphResourceOccupations.getOccupationsStartingAt(node));
					needsUpdate.addAll(graphResourceOccupations.getOccupationsEndingAt(node));
				}
			
				for(ResourceOccupation ro: needsUpdate) {
					final GraphConstraints roConstraint = constraints.get(ro.intention);
					if (roConstraint == null) {
						continue;
					}
					ResourcePlan plan = modifiedPlans.get(ro.resource);
					if (plan == null) {
						plan = state.resourcePlans.get(ro.resource).copy();
						modifiedPlans.put(ro.resource, plan);
					}
					OccupationTimes times = roConstraint.getOccupationTimes(ro.getStart(), ro.getEnd(), ro.flow);
					if (times == null) {
						plan.remove(ro, queue);
					} else {
						plan.set(ro, times, queue);
					}
				}
			}
			
			double minPenalty = 0;
			for(String intention: intentions) {
				GraphConstraints gc = constraints.get(intention);
				if (gc == null) {
					gc = state.graphConstraints.get(intention);
				}
				minPenalty += gc.getMinPenalty();
			}

			// TODO filter Infinity badness
			result.push(schedule.withBadness(minPenalty));
		}
		
		return result;
	}

	@Override
	@CheckForNull
	public State apply(State state, ConflictSchedule conflictSchedule) {
		return new StateChanger(problem, maxPenalty, connections, state).apply(conflictSchedule);
	}
	
	// TODO move to separate file
	public static class StateChanger {
		private final HashSet<String> copiedResourcePlans = new HashSet<>();
		private final HashSet<String> copiedGraphConstraints = new HashSet<>();
		private final IntentionRestrictionQueue restrictionQueue = new IntentionRestrictionQueue();
		
		private final Problem problem;
		private final double maxPenalty;
		private final ConnectionRepository connections; 
		
		private PersistentMap<String, ResourcePlan> resourcePlans;
		private PersistentMap<String, GraphConstraints> graphConstraints;
		private double minPenalty;
		

		public StateChanger(Problem problem, double maxPenalty, ConnectionRepository connections, State state) {
			this.problem = problem;
			this.maxPenalty = maxPenalty;
			this.connections = connections;
			this.resourcePlans = state.resourcePlans;
			this.graphConstraints = state.graphConstraints;
			this.minPenalty = state.minPenalty;
		}

		@CheckForNull
		public State apply(ConflictSchedule conflictSchedule) {
			restrictionQueue.addAll(conflictSchedule.restrictions);
			return drainQueue();
		}
		
		@CheckForNull
		public State syncAllResourcePlans() {
			for(ServiceIntention intention: problem.getScenario().getServiceIntentionsList()) {
				ImmutableSet<Node> nodes = problem.getRouteGraph(intention.getRoute()).getNodes();
				syncResourcePlan(intention.getId(), nodes, restrictionQueue);
			}
			return drainQueue();
		}
		
		@CheckForNull
		private State drainQueue() {
			HashSet<ResourceOccupation> needsUpdate = new HashSet<>();
			double penaltyIncrease = 0;
			while(!restrictionQueue.isEmpty()) {
				IntentionRestriction restriction = restrictionQueue.pop();
				//logger.log(SOLVER, "Applying " + restriction + " on " + restriction.resource);
				
				// Apply restriction to all affected occupations.
				final String intention = restriction.intention;
				final GraphConstraints constraints = prepareGraphConstraintsForModification(intention);
				
				double penaltyBefore = constraints.getMinPenalty();
			
				Set<Node> changedNodes = restriction.applyTo(constraints);
				if (!constraints.areFeasible()) {
					return null;
				}
				
				GraphResourceOccupations graphResourceOccupations = problem.getResourceOccupations(intention);
				for (Node node : changedNodes) {
					needsUpdate.addAll(graphResourceOccupations.getOccupationsStartingAt(node));
					needsUpdate.addAll(graphResourceOccupations.getOccupationsEndingAt(node));
				}
				
				double penaltyAfter = constraints.getMinPenalty();
				if (penaltyAfter > penaltyBefore) {
					penaltyIncrease += penaltyAfter - penaltyBefore;
				}
				
				if (restrictionQueue.isEmpty()) {
					for (ResourceOccupation ro : needsUpdate) {
						updateResourcePlan(ro.resource, ro, restrictionQueue);
					}
					needsUpdate.clear();
					if (penaltyIncrease > 0 && !increaseMinPenalty(penaltyIncrease, restrictionQueue)) {
						return null;
					}
					penaltyIncrease = 0;
					applyConnectionConstraints(restrictionQueue);
				}
			}
			logger.log(SOLVER, "Applied {0} path restrictions.", restrictionQueue.getPoppedCount());
			return new State(resourcePlans, graphConstraints, minPenalty);
		}
		
		private void applyConnectionConstraints(IntentionRestrictionQueue restrictionQueue) {
			for (Connection c : connections.getAll()) {
				GraphConstraints timeConstraintsFrom = graphConstraints.get(c.intentionFrom);
				int earliestArrival = Integer.MAX_VALUE;
				for (Edge edge : timeConstraintsFrom.getGraph().getEdgesByMarker(c.markerFrom)) {
					if (!timeConstraintsFrom.isFeasible(edge)) {
						continue;
					}
					int time = timeConstraintsFrom.getEntryEarliest(edge);
					earliestArrival = Math.min(earliestArrival, time);
				}
				
				GraphConstraints timeConstraintsTo = graphConstraints.get(c.intentionTo);
				int latestDeparture = 0;
				for (Edge edge: timeConstraintsTo.getGraph().getEdgesByMarker(c.markerTo)) {
					if (!timeConstraintsTo.isFeasible(edge)) {
						continue;
					}
					int time = timeConstraintsTo.getExitLatest(edge);
					latestDeparture = Math.max(latestDeparture, time);
				}

				int latestArrival = latestDeparture - c.minConnectionTime;
				int earliestDeparture = earliestArrival + c.minConnectionTime;
				
				HashSet<Edge> affectedEdgesFrom = new HashSet<>();
				for (Edge arrivalEdge : timeConstraintsFrom.getGraph().getEdgesByMarker(c.markerFrom)) {
					if (!timeConstraintsFrom.isFeasible(arrivalEdge)) {
						continue;
					}
					
					for(Edge prev: arrivalEdge.start.getPrevious()) {
						if (!timeConstraintsFrom.isFeasible(prev)) {
							continue;
						}
						if (timeConstraintsFrom.getExitLatest(prev) > latestArrival) {
							affectedEdgesFrom.add(prev);
						}
					}
				}
				if (!affectedEdgesFrom.isEmpty()) {
					restrictionQueue.add(
							new IntentionRestriction.DecreaseMaxTimeRestriction(
									c.intentionFrom, latestArrival, affectedEdgesFrom));
				}
				
				HashSet<Edge> affectedEdgesTo = new HashSet<>();
				for (Edge departureEdge: timeConstraintsTo.getGraph().getEdgesByMarker(c.markerTo)) {
					if (!timeConstraintsTo.isFeasible(departureEdge)) {
						continue;
					}
					
					for(Edge next: departureEdge.end.getNext()) {
						if (!timeConstraintsTo.isFeasible(next)) {
							continue;
						}
						if (timeConstraintsTo.getEntryEarliest(next) < earliestDeparture) {
							affectedEdgesTo.add(next);
						}
					}
				}
				if (!affectedEdgesTo.isEmpty()) {
					restrictionQueue.add(
							new IntentionRestriction.IncreaseMinTimeRestriction(
									c.intentionTo, earliestDeparture, affectedEdgesTo));
				}
			}
		}
		
		private boolean increaseMinPenalty(double delta, IntentionRestrictionQueue restrictionQueue) {
			minPenalty += delta;
			double remainingPenalty = maxPenalty - minPenalty;
			if (remainingPenalty < 0) {
				return false;
			}
			
			for(String intention: graphConstraints.keys()) {
				GraphConstraints constraints = prepareGraphConstraintsForModification(intention);
				double minPenaltyBefore = constraints.getMinPenalty();
				double maxPenalty =
						Math.min(Debug.MAX_PENALTY_PER_INTENTION, 
								 constraints.getMinPenalty() + remainingPenalty);
				Set<Node> changedNodes = constraints.setMaxPenalty(maxPenalty);
				if (!constraints.areFeasible()) {
					return false;
				}
				syncResourcePlan(intention, changedNodes, restrictionQueue);
				double minPenaltyAfter = constraints.getMinPenalty();
				if (minPenaltyAfter > minPenaltyBefore) {
					return increaseMinPenalty(minPenaltyAfter - minPenaltyBefore, restrictionQueue);
				}
			}
			assertResourcePlanAndTimeConstrainsAreConsistent(problem, resourcePlans, graphConstraints);
			return true;
		}

		// TODO separate into min-time and max-time changes?
		private void syncResourcePlan(String intention, Set<Node> changedNodes,
				IntentionRestrictionQueue restrictionQueue) {
			HashSet<ResourceOccupation> needsUpdate = new HashSet<>();
			GraphResourceOccupations graphResourceOccupations = problem.getResourceOccupations(intention);
			for (Node node : changedNodes) {
				needsUpdate.addAll(graphResourceOccupations.getOccupationsStartingAt(node));
				needsUpdate.addAll(graphResourceOccupations.getOccupationsEndingAt(node));
			}
			for (ResourceOccupation ro : needsUpdate) {
				updateResourcePlan(ro.resource, ro, restrictionQueue);
			}
		}

		private void updateResourcePlan(String resource,
				ResourceOccupation occupation,
				IntentionRestrictionQueue restrictionQueue) {
			ResourcePlan resourcePlan = prepareResourcePlanForModification(resource);
			GraphConstraints timeConstraints = graphConstraints.get(occupation.intention);
			OccupationTimes times = timeConstraints.getOccupationTimes(
					occupation.getStart(), occupation.getEnd(), occupation.flow);
			if (times == null) {
				resourcePlan.remove(occupation, restrictionQueue);
				return;
			}
			resourcePlan.set(occupation, times, restrictionQueue);
		}
		
		@CheckReturnValue
		private ResourcePlan prepareResourcePlanForModification(String resource) {
			if (!copiedResourcePlans.add(resource)) {
				return resourcePlans.get(resource);
			}
			ResourcePlan copy = resourcePlans.get(resource).copy();
			resourcePlans = resourcePlans.with(resource, copy);
			return copy;
		}
		
		@CheckReturnValue
		private GraphConstraints prepareGraphConstraintsForModification(String intention) {
			if (!copiedGraphConstraints.add(intention)) {
				return graphConstraints.get(intention);
			}
			GraphConstraints copy = graphConstraints.get(intention).copy();
			graphConstraints = graphConstraints.with(intention, copy);
			return copy;
		}
		
	}
	
	// Debugging methods
	
	private static void assertResourcePlanAndTimeConstrainsAreConsistent(Problem problem,
			PersistentMap<String, ResourcePlan> resourcePlans,
			PersistentMap<String, GraphConstraints> graphConstraints) {
		if (!Debug.ENABLE_RESOURCE_PLAN_CONSITENCY_CHECKS) {
			return;
		}
		for (String intention : graphConstraints.keys()) {
			assertResourcePlanAndTimeConstraintsAreConsistent(problem, resourcePlans, graphConstraints, intention);
		}
	}

	private static void assertResourcePlanAndTimeConstraintsAreConsistent(Problem problem,
			PersistentMap<String, ResourcePlan> resourcePlans, PersistentMap<String, GraphConstraints> graphConstraints,
			String intention) {
		if (!Debug.ENABLE_RESOURCE_PLAN_CONSITENCY_CHECKS) {
			return;
		}
		GraphConstraints timeConstraints = graphConstraints.get(intention);
		GraphResourceOccupations occupations = problem.getResourceOccupations(intention);
		for (String resource : occupations.getOccupiedResources()) {
			ResourcePlan resourcePlan = resourcePlans.get(resource);
			for (ResourceOccupation o : occupations.getOccupations(resource)) {
				OccupationTimes times = timeConstraints.getOccupationTimes(o.getStart(), o.getEnd(), o.flow);
				resourcePlan.check(o, times);
			}
		}
	}

	private void printConflictSchedule(Conflict conflict, List<ConflictSchedule> conflictSchedules) {
		logger.log(SOLVER, "Schedules for worst conflict " + conflict + ":");
		for (ConflictSchedule cs : conflictSchedules) {
			logger.log(SOLVER, cs.toString());
		}
	}

}
