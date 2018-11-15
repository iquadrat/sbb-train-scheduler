package org.povworld.sbb;

import org.povworld.collection.Map;
import org.povworld.collection.Set;
import org.povworld.collection.common.Assert;
import org.povworld.collection.common.Interval;
import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.collection.mutable.ArrayList;
import org.povworld.collection.mutable.HashSet;
import org.povworld.sbb.ConnectionRepository.Connection;
import org.povworld.sbb.RouteGraph.Edge;

public class ConnectionOccupation {
	public final Connection connection;
	public final int earliestArrival;
	public final int latestArrival;
	public final int earliestDeparture;
	public final int latestDeparture;
	
	private final Set<Edge> arrivalEdges;
	private final Set<Edge> departureEdges;
	
	// TODO this is used only for testing
	ConnectionOccupation(Connection connection, int earliestArrival, int latestArrival, int earliestDeparture,
			int latestDeparture) {
		this(connection, earliestArrival, latestArrival, earliestDeparture, latestDeparture,
				ImmutableCollections.setOf(), ImmutableCollections.setOf());
	}

	public ConnectionOccupation(
			Connection connection,
			int earliestArrival, int latestArrival, int earliestDeparture, int latestDeparture,
			Set<Edge> arrivalEdges, Set<Edge> departureEdges) {
		this.connection = connection;
		this.earliestArrival = earliestArrival;
		this.latestArrival = latestArrival;
		this.earliestDeparture = earliestDeparture;
		this.latestDeparture = latestDeparture;
		this.arrivalEdges = arrivalEdges;
		this.departureEdges = departureEdges;
	}
	
	public double getBadness() {
		return getLateProbability() * Debug.CONNECTION_LATE_PROBABILITY_TO_BADNESS_FACTOR;
	}

	public double getLateProbability() {
		int minConnectionTime = connection.minConnectionTime;
				
		int tSafe = earliestDeparture - latestArrival;
		if (tSafe >= minConnectionTime) {
			return 0;
		}
		
		int tOpt = latestDeparture- earliestArrival;
		if (tOpt <= minConnectionTime) {
			return 1.0;
		}
		
		int tMid1 = earliestDeparture - earliestArrival;
		int tMid2 = latestDeparture - latestArrival;
		if (tMid1 > tMid2) {
			int tmp = tMid1;
			tMid1 = tMid2;
			tMid2 = tmp;
		}
		Assert.assertTrue(tSafe <= tMid1, "tsafe > tmid1");
		Assert.assertTrue(tMid1 <= tMid2, "tmid1 > tmid2");
		Assert.assertTrue(tMid2 <= tOpt, "topt > tmid2");
		
		double areaA = 0.5 * (tMid1 - tSafe);
		double areaB = tMid2 - tMid1;
		double areaC = 0.5 * (tOpt - tMid2);
		
		double areaGood;
		if (minConnectionTime < tMid1) {
			double l = tMid1 - tSafe;
			double k = tMid1 - minConnectionTime;
			areaGood = areaB + areaC + k * (1 - (0.5*k/l));
		} else if (minConnectionTime < tMid2) {
			areaGood = areaC + (tMid2 - minConnectionTime);
		} else /* minConnectionTime < tOpt */ {
			double l = tOpt - tMid2;
			double k = tOpt - minConnectionTime;
			areaGood = k * (0.5*k/l);
		}
		
		return 1.0 - areaGood / (areaA + areaB + areaC);
	}

	public static ConnectionOccupation create(Connection c, Map<String, GraphConstraints> timeConstraints) {
		GraphConstraints timeConstraintsFrom = timeConstraints.get(c.intentionFrom);
		int earliestArrival = Integer.MAX_VALUE;
		int latestArrival = 0;
		Set<Edge> arrivalEdges = timeConstraintsFrom.getGraph().getEdgesByMarker(c.markerFrom);
		for (Edge edge : arrivalEdges) {
			if (!timeConstraintsFrom.isFeasible(edge)) {
				continue;
			}
			earliestArrival = Math.min(earliestArrival, timeConstraintsFrom.getEntryEarliest(edge));
			latestArrival = Math.max(latestArrival, timeConstraintsFrom.getEntryLatest(edge));
		}
		
		GraphConstraints timeConstraintsTo = timeConstraints.get(c.intentionTo);
		int latestDeparture = 0;
		int earliestDeparture = Integer.MAX_VALUE;
		Set<Edge> departureEdges = timeConstraintsTo.getGraph().getEdgesByMarker(c.markerTo);
		for (Edge edge: departureEdges) {
			if (!timeConstraintsTo.isFeasible(edge)) {
				continue;
			}
			latestDeparture = Math.max(latestDeparture, timeConstraintsTo.getExitLatest(edge));
			earliestDeparture = Math.min(earliestDeparture, timeConstraintsTo.getExitEarliest(edge));
		}
		
		Assert.assertTrue(earliestArrival > 0, "0 arrival");
		Assert.assertTrue(earliestDeparture > 0, "0 arrival");
		Assert.assertTrue(latestArrival < GraphConstraints.TMAX, "TMAX departure");
		Assert.assertTrue(latestDeparture < GraphConstraints.TMAX, "TMAX departure");
		
		return new ConnectionOccupation(c, earliestArrival, latestArrival, earliestDeparture, latestDeparture,
				arrivalEdges, departureEdges);
	}
	
	public ArrayList<ConflictSchedule> createConflictSchedules() {
		int cutoff = (latestArrival - earliestDeparture + connection.minConnectionTime + 1) / 2;
		if (cutoff <= 0) {
			throw new IllegalStateException();
		}
		int earliestDepartureMin = earliestDeparture + cutoff;
		int latestArrivalMax = latestArrival - cutoff;
		
		ArrayList<ConflictSchedule> schedules = new ArrayList<>(2);
		schedules.push(new ConflictSchedule(ImmutableCollections.listOf(
				new IntentionRestriction.IncreaseMinTimeRestriction(
						connection.intentionTo, earliestDepartureMin, getOutgoing(departureEdges))),
				1.0 / (latestDeparture - earliestDeparture)));
		schedules.push(new ConflictSchedule(ImmutableCollections.listOf(
				new IntentionRestriction.DecreaseMaxTimeRestriction(
						connection.intentionFrom, latestArrivalMax, getIncoming(arrivalEdges))) ,
				1.0 / (latestArrival - earliestArrival)));
		return schedules;
	}
	
	private Set<Edge> getIncoming(Set<Edge> arrivalEdges) {
		HashSet<Edge> result = new HashSet<>(arrivalEdges.size());
		for(Edge edge: arrivalEdges) {
			result.addAll(edge.start.getPrevious());
		}
		return result;
	}

	private Set<Edge> getOutgoing(Set<Edge> departureEdges) {
		HashSet<Edge> result = new HashSet<>(departureEdges.size());
		for(Edge edge: departureEdges) {
			result.addAll(edge.end.getNext());
		}
		return result;
	}

	@Override
	public String toString() {
		return TimeUtil.printTimeInterval(new Interval(earliestArrival, latestArrival)) + " - "
				+ TimeUtil.printTimeInterval(new Interval(earliestDeparture, latestDeparture)) + " / "
				+ connection.minConnectionTime + "s";
	}

}
