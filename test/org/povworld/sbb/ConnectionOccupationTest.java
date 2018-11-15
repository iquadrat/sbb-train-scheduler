package org.povworld.sbb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.sbb.ConnectionRepository.Connection;
import org.povworld.sbb.RouteGraph.Edge;

import com.google.common.truth.Truth;

public class ConnectionOccupationTest extends GraphTestBase {

	private static final double ANY_NUMBER = 3924;

	private static void assertProb(double expected, double actual) {
		assertEquals(expected, actual, 1e-6);
	}

	private static Connection conn(int minTime) {
		return new ConnectionRepository.Connection("from", "to", "X", "X", minTime);
	}

	@Test
	public void testProbPoints() {
		assertProb(0, new ConnectionOccupation(conn(400), 100, 100, 500, 500).getLateProbability());
		assertProb(1.0, new ConnectionOccupation(conn(401), 100, 100, 500, 500).getLateProbability());
	}

	@Test
	public void testArrivalIntervalDeparturePoint() {
		assertProb(0, new ConnectionOccupation(conn(100), 100, 100, 200, 400).getLateProbability());
		assertProb(1.0, new ConnectionOccupation(conn(300), 100, 100, 200, 400).getLateProbability());
		assertProb(0.5, new ConnectionOccupation(conn(200), 100, 100, 200, 400).getLateProbability());
		assertProb(0.25, new ConnectionOccupation(conn(150), 100, 100, 200, 400).getLateProbability());
	}

	@Test
	public void testArrivalPointDepartureInterval() {
		assertProb(0, new ConnectionOccupation(conn(100), 100, 300, 400, 400).getLateProbability());
		assertProb(1.0, new ConnectionOccupation(conn(300), 100, 300, 400, 400).getLateProbability());
		assertProb(0.5, new ConnectionOccupation(conn(200), 100, 300, 400, 400).getLateProbability());
		assertProb(0.25, new ConnectionOccupation(conn(150), 100, 300, 400, 400).getLateProbability());
	}

	@Test
	public void testArrivalDepartureSameInterval() {
		assertProb(0.5, new ConnectionOccupation(conn(0), 100, 300, 100, 300).getLateProbability());
		assertProb(7.0 / 8, new ConnectionOccupation(conn(100), 100, 300, 100, 300).getLateProbability());
		assertProb(1.0 / 8, new ConnectionOccupation(conn(-100), 100, 300, 100, 300).getLateProbability());
		assertProb(23.0 / 32, new ConnectionOccupation(conn(50), 100, 300, 100, 300).getLateProbability());
	}

	@Test
	public void testArrivalDepartureOverlappingInterval() {
		assertProb(25.0 / 400, new ConnectionOccupation(conn(0), 100, 300, 200, 600).getLateProbability());
		assertProb(100.0 / 400, new ConnectionOccupation(conn(100), 100, 300, 200, 600).getLateProbability());
		assertProb(200.0 / 400, new ConnectionOccupation(conn(200), 100, 300, 200, 600).getLateProbability());
		assertProb(300.0 / 400, new ConnectionOccupation(conn(300), 100, 300, 200, 600).getLateProbability());
		assertProb(375.0 / 400, new ConnectionOccupation(conn(400), 100, 300, 200, 600).getLateProbability());
		assertProb(1.0, new ConnectionOccupation(conn(500), 100, 300, 200, 600).getLateProbability());
	}

	@Test
	public void testArrivalDepartureTouchingInterval() {
		assertProb(0.0, new ConnectionOccupation(conn(0), 100, 200, 200, 600).getLateProbability());
		assertProb(50.0 / 400, new ConnectionOccupation(conn(100), 100, 200, 200, 600).getLateProbability());
		assertProb(150.0 / 400, new ConnectionOccupation(conn(200), 100, 200, 200, 600).getLateProbability());
		assertProb(250.0 / 400, new ConnectionOccupation(conn(300), 100, 200, 200, 600).getLateProbability());
		assertProb(350.0 / 400, new ConnectionOccupation(conn(400), 100, 200, 200, 600).getLateProbability());
		assertProb(1.0, new ConnectionOccupation(conn(500), 100, 200, 200, 600).getLateProbability());
	}

	@Test
	public void testArrivalDepartureNonOverlappingInterval() {
		assertProb(0.0, new ConnectionOccupation(conn(0), 100, 200, 300, 600).getLateProbability());
		assertProb(0.0, new ConnectionOccupation(conn(100), 100, 200, 300, 600).getLateProbability());
		assertProb(50.0 / 300, new ConnectionOccupation(conn(200), 100, 200, 300, 600).getLateProbability());
		assertProb(150.0 / 300, new ConnectionOccupation(conn(300), 100, 200, 300, 600).getLateProbability());
		assertProb(250.0 / 300, new ConnectionOccupation(conn(400), 100, 200, 300, 600).getLateProbability());
		assertProb(1.0, new ConnectionOccupation(conn(500), 100, 200, 300, 600).getLateProbability());
	}
	
	@Test
	public void testConflictScheduleSingleSecondSlack() {
		buildSimplePath();
		final Edge arrivalEdge = edges.get(1);
		final Edge departureEdge = edges.get(3);
		
		Connection connection = new Connection("from", "to", "A", "B", 1);
		ConnectionOccupation o = new ConnectionOccupation(
				connection, 100, 200, 200, 300, 
				ImmutableCollections.setOf(arrivalEdge), 
				ImmutableCollections.setOf(departureEdge));
		
		Truth.assertThat(o.createConflictSchedules())
				.containsExactly(
						new ConflictSchedule(
								ImmutableCollections.listOf(new IntentionRestriction.IncreaseMinTimeRestriction("to",
										201, ImmutableCollections.asSet(departureEdge.end.getNext()))),
								ANY_NUMBER),
						new ConflictSchedule(
								ImmutableCollections.listOf(new IntentionRestriction.DecreaseMaxTimeRestriction("from",
										199, ImmutableCollections.asSet(arrivalEdge.start.getPrevious()))),
								ANY_NUMBER));
	}
	
	@Test
	public void testConflictSchedule() {
		buildSimplePath();
		final Edge arrivalEdge = edges.get(1);
		final Edge departureEdge = edges.get(3);
		
		Connection connection = new Connection("from", "to", "A", "B", 100);
		ConnectionOccupation o = new ConnectionOccupation(
				connection, 100, 400, 300, 700, 
				ImmutableCollections.setOf(arrivalEdge), 
				ImmutableCollections.setOf(departureEdge));
		
		IntentionRestriction.IncreaseMinTimeRestriction restrictDeparture = new IntentionRestriction.IncreaseMinTimeRestriction(
				"to", 400, ImmutableCollections.asSet(departureEdge.end.getNext()));
		IntentionRestriction.DecreaseMaxTimeRestriction restrictArrival = new IntentionRestriction.DecreaseMaxTimeRestriction(
				"from", 300, ImmutableCollections.asSet(arrivalEdge.start.getPrevious()));
		Truth.assertThat(o.createConflictSchedules()).containsExactly(
				new ConflictSchedule(ImmutableCollections.listOf(restrictDeparture), ANY_NUMBER),
				new ConflictSchedule(ImmutableCollections.listOf(restrictArrival), ANY_NUMBER));
	}

}
