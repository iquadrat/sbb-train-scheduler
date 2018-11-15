package org.povworld.sbb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.povworld.collection.Map;
import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.sbb.RouteGraph.Edge;
import org.povworld.sbb.RouteGraph.Node;

import com.google.common.truth.Truth;

public class GraphConstraintsTest extends GraphTestBase {
	
	private static final double EPS = GraphConstraints.EPS;
	
	@Test
	public void testSimplePathTimeConstraints() {
		buildSimplePath();

		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		Map<Integer, Edge> edges = buildEdgeIndex(graph);

		assertEquals(time("12:20:00"), gtc.getEntryEarliest(edges.get(1)));
		assertEquals(time("12:20:10"), gtc.getEntryEarliest(edges.get(2)));
		assertEquals(time("12:30:00"), gtc.getEntryEarliest(edges.get(3)));
		assertEquals(time("12:30:30"), gtc.getEntryEarliest(edges.get(4)));
		assertEquals(time("12:30:50"), gtc.getEntryEarliest(edges.get(5)));
		
		assertEquals(time("13:00:00"), gtc.getExitLatest(edges.get(5)));
		assertEquals(time("12:59:10"), gtc.getExitLatest(edges.get(4)));
		assertEquals(time("12:58:50"), gtc.getExitLatest(edges.get(3)));
		assertEquals(time("12:50:00"), gtc.getExitLatest(edges.get(2)));
		assertEquals(time("12:49:40"), gtc.getExitLatest(edges.get(1)));
	}
	
	@Test
	public void testSimplePathFlowOccupationTimes() {
		buildSimplePath();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		Map<Integer, Edge> edges = buildEdgeIndex(graph); 
		
		 OccupationTimes t = 
				 gtc.getOccupationTimes(edges.get(3), edges.get(3), 
						 ImmutableCollections.listOf(edges.get(3)));
		assertEquals(time("12:30:00"), t.entryEarliest);
		assertEquals(time("12:30:30"), t.exitEarliest);
		assertEquals(time("12:50:00"), t.entryLatest);
		assertEquals(time("12:58:50"), t.exitLatest);
		assertEquals(1.0, t.weight, EPS);

		t = gtc.getOccupationTimes(edges.get(1), edges.get(5), 
				ImmutableCollections.listOf(
						edges.get(1),
						edges.get(2),
						edges.get(3),
						edges.get(4),
						edges.get(5)));
		assertEquals(time("12:20:00"), t.entryEarliest);
		assertEquals(time("12:31:40"), t.exitEarliest);
		assertEquals(time("12:49:30"), t.entryLatest);
		assertEquals(time("13:00:00"), t.exitLatest);
		assertEquals(1.0, t.weight, EPS);
	}
	
	@Test
	public void testForkGraphTimeConstraints() {
		buildForkGraph();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		Map<Integer, Edge> edges = buildEdgeIndex(graph);
		
		assertEquals(time("12:20:00"), gtc.getEntryEarliest(edges.get(1)));
		assertEquals(time("12:20:00"), gtc.getEntryEarliest(edges.get(5)));
		assertEquals(time("12:20:10"), gtc.getEntryEarliest(edges.get(2)));
		assertEquals(time("12:30:00"), gtc.getEntryEarliest(edges.get(3)));
		assertEquals(time("12:30:20"), gtc.getEntryEarliest(edges.get(4)));
		assertEquals(time("12:30:20"), gtc.getEntryEarliest(edges.get(6)));
		
		assertEquals(time("12:40:00"), gtc.getExitLatest(edges.get(4)));
		assertEquals(time("12:40:00"), gtc.getExitLatest(edges.get(6)));
		assertEquals(time("12:39:00"), gtc.getExitLatest(edges.get(3)));
		assertEquals(time("12:38:40"), gtc.getExitLatest(edges.get(2)));
		assertEquals(time("12:38:20"), gtc.getExitLatest(edges.get(1)));
		assertEquals(time("12:38:20"), gtc.getExitLatest(edges.get(5)));
	}
	
	@Test
	public void testForkGraphFlowOccupationTimes() {
		buildForkGraph();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		Map<Integer, Edge> edges = buildEdgeIndex(graph);

		OccupationTimes t = gtc.getOccupationTimes(edges.get(1), edges.get(3),
				ImmutableCollections.listOf(
						edges.get(1),
						edges.get(2),
						edges.get(3)));
		assertEquals(time("12:20:00"), t.entryEarliest);
		assertEquals(time("12:30:20"), t.exitEarliest);
		assertEquals(time("12:39:00"), t.exitLatest);
		assertEquals(time("12:38:10"), t.entryLatest);
		assertEquals(0.5, t.weight, EPS);

		t = gtc.getOccupationTimes(edges.get(2), edges.get(3), 
				ImmutableCollections.listOf(
						edges.get(5),
						edges.get(2),
						edges.get(3),
						edges.get(6)));
		assertEquals(time("12:20:20"), t.entryEarliest);
		assertEquals(time("12:30:20"), t.exitEarliest);
		assertEquals(time("12:38:00"), t.exitLatest);
		assertEquals(time("12:37:20"), t.entryLatest);
		assertEquals(0.25, t.weight, EPS);
	}
	
	@Test
	public void testComplexGraphTimeConstraints() {
		buildComplexGraph();
		
		RouteGraph graph = RouteGraph.build(route);
		
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		Map<Integer, Edge> edges = buildEdgeIndex(graph);
		
		assertEquals(time("12:00:00"), gtc.getEntryEarliest(edges.get(1)));
		assertEquals(time("12:00:10"), gtc.getEntryEarliest(edges.get(2)));
		assertEquals(time("12:00:10"), gtc.getEntryEarliest(edges.get(17)));
		
		assertEquals(time("12:00:00"), gtc.getEntryEarliest(edges.get(9)));
		assertEquals(time("12:00:20"), gtc.getEntryEarliest(edges.get(10)));
		assertEquals(time("12:00:20"), gtc.getEntryEarliest(edges.get(18)));

		assertEquals(time("12:00:00"), gtc.getEntryEarliest(edges.get(13)));
		assertEquals(time("12:00:30"), gtc.getEntryEarliest(edges.get(14)));

		assertEquals(time("12:00:30"), gtc.getEntryEarliest(edges.get(3)));
		assertEquals(time("12:30:00"), gtc.getEntryEarliest(edges.get(4)));
		
		assertEquals(time("12:00:30"), gtc.getEntryEarliest(edges.get(11)));
		assertEquals(time("12:30:00"), gtc.getEntryEarliest(edges.get(12)));
		
		assertEquals(time("12:00:40"), gtc.getEntryEarliest(edges.get(15)));
		assertEquals(time("12:30:00"), gtc.getEntryEarliest(edges.get(16)));
		
		assertEquals(time("12:30:10"), gtc.getEntryEarliest(edges.get(5)));
		assertEquals(time("12:32:10"), gtc.getEntryEarliest(edges.get(6)));
		assertEquals(time("12:32:10"), gtc.getEntryEarliest(edges.get(19)));
		
		assertEquals(time("12:32:40"), gtc.getEntryEarliest(edges.get(7)));
		assertEquals(time("12:32:50"), gtc.getEntryEarliest(edges.get(8)));
		
		
		assertEquals(time("13:00:00"), gtc.getExitLatest(edges.get(8)));
		assertEquals(time("12:59:00"), gtc.getExitLatest(edges.get(7)));
		assertEquals(time("12:58:30"), gtc.getExitLatest(edges.get(6)));
		assertEquals(time("12:58:50"), gtc.getExitLatest(edges.get(19)));
		assertEquals(time("12:58:20"), gtc.getExitLatest(edges.get(5)));
		assertEquals(time("12:56:20"), gtc.getExitLatest(edges.get(4)));
		assertEquals(time("12:56:20"), gtc.getExitLatest(edges.get(12)));
		assertEquals(time("12:56:20"), gtc.getExitLatest(edges.get(16)));
		
		assertEquals(time("12:55:50"), gtc.getExitLatest(edges.get(3)));
		assertEquals(time("12:55:10"), gtc.getExitLatest(edges.get(2)));
		assertEquals(time("12:55:50"), gtc.getExitLatest(edges.get(11)));
		assertEquals(time("12:54:50"), gtc.getExitLatest(edges.get(17)));
		assertEquals(time("12:54:50"), gtc.getExitLatest(edges.get(10)));
		assertEquals(time("12:55:50"), gtc.getExitLatest(edges.get(15)));
		assertEquals(time("12:54:40"), gtc.getExitLatest(edges.get(14)));
		assertEquals(time("12:54:40"), gtc.getExitLatest(edges.get(18)));
		
		assertEquals(time("12:54:50"), gtc.getExitLatest(edges.get(1)));
		
		assertEquals(time("12:54:40"), gtc.getExitLatest(edges.get(9)));

		assertEquals(time("12:54:20"), gtc.getExitLatest(edges.get(13)));
	}
	
	@Test
	public void testSimplePathIncreaseMinTime() {
		buildSimplePath();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		Map<Integer, Node> nodes = buildNodeIndex(graph);
		Map<Integer, Edge> edges = buildEdgeIndex(graph);
		
		Truth.assertThat(gtc.increaseEarliestEntry(edges.get(3), TimeUtil.parseTime("12:29:00"))).isEmpty();
		
		Truth.assertThat(gtc.increaseEarliestEntry(edges.get(1), TimeUtil.parseTime("12:25:00")))
			.containsAllOf(nodes.get(1), nodes.get(2));
		
		assertEquals(time("12:25:00"), gtc.getEntryEarliest(edges.get(1)));
		assertEquals(time("12:25:10"), gtc.getEntryEarliest(edges.get(2)));
		assertEquals(time("12:30:00"), gtc.getEntryEarliest(edges.get(3)));
		
		Truth.assertThat(gtc.increaseEarliestEntry(edges.get(2), TimeUtil.parseTime("12:35:00")))
			.containsAllOf(nodes.get(1), nodes.get(2), nodes.get(3), nodes.get(4), nodes.get(5));
		
		assertEquals(time("12:25:00"), gtc.getEntryEarliest(edges.get(1)));
		assertEquals(time("12:35:00"), gtc.getEntryEarliest(edges.get(2)));
		assertEquals(time("12:36:10"), gtc.getEntryEarliest(edges.get(5)));
	}
	
	@Test
	public void testSimplePathIncreaseMinTimeMakesInfeasible() {
		buildSimplePath();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		Map<Integer, Edge> edges = buildEdgeIndex(graph);
		
		gtc.increaseEarliestEntry(edges.get(4), TimeUtil.parseTime("12:59:59"));
		assertFalse(gtc.areFeasible());
	}
	
	@Test
	public void testSimplePathIncreaseMinTimeMakesInfeasibleDirect() {
		buildSimplePath();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		Map<Integer, Edge> edges = buildEdgeIndex(graph);
		
		gtc.increaseEarliestEntry(edges.get(3), TimeUtil.parseTime("12:50:05"));
		assertFalse(gtc.areFeasible());
	}
	
	@Test
	public void testSimplePathDecreaseMaxTime() {
		buildSimplePath();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		Truth.assertThat(gtc.decreaseLatestExit(edges.get(5), TimeUtil.parseTime("13:29:00"))).isEmpty();
		
		Truth.assertThat(gtc.decreaseLatestExit(edges.get(2), TimeUtil.parseTime("12:49:00")))
			.containsExactly(nodes.get(2), nodes.get(1), nodes.get(0), nodes.get(-1));
		assertEquals(time("12:49:00"), gtc.getExitLatest(edges.get(2)));
		assertEquals(time("12:48:40"), gtc.getExitLatest(edges.get(1)));
		
		Truth.assertThat(gtc.decreaseLatestExit(edges.get(4), TimeUtil.parseTime("12:55:00")))
			.containsAllOf(nodes.get(4), nodes.get(3));
		assertEquals(time("12:55:00"), gtc.getExitLatest(edges.get(4)));
		assertEquals(time("12:54:40"), gtc.getExitLatest(edges.get(3)));
		assertEquals(time("12:49:00"), gtc.getExitLatest(edges.get(2)));
	}
	
	@Test
	public void testSimplePathDecreaseMaxTimeMakesInfeasible() {
		buildSimplePath();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		gtc.decreaseLatestExit(edges.get(4), TimeUtil.parseTime("12:30:40"));
		
		assertFalse(gtc.areFeasible());
	}
	
	@Test
	public void testSimplePathDecreaseMaxTimeMakesInfeasibleDirect() {
		buildSimplePath();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		gtc.decreaseLatestExit(edges.get(2), TimeUtil.parseTime("12:29:00"));
		
		assertFalse(gtc.areFeasible());
	}
	
	@Test
	public void testForkGraphIncreaseMinTime() {
		buildForkGraph();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		Truth.assertThat(gtc.increaseEarliestEntry(edges.get(1), TimeUtil.parseTime("12:25:00")))
			.containsAllOf(nodes.get(0), nodes.get(1));
		assertEquals(time("12:25:00"), gtc.getEntryEarliest(edges.get(1)));
		assertEquals(time("12:20:20"), gtc.getEntryEarliest(edges.get(2)));
		assertEquals(time("12:30:00"), gtc.getEntryEarliest(edges.get(3)));
		
		Truth.assertThat(gtc.increaseEarliestEntry(edges.get(5), TimeUtil.parseTime("12:31:00"))).containsExactly(
				nodes.get(5), nodes.get(1), nodes.get(2), nodes.get(3), nodes.get(6), nodes.get(4), nodes.get(-2));
		
		assertEquals(time("12:31:00"), gtc.getEntryEarliest(edges.get(5)));
		assertEquals(time("12:25:00"), gtc.getEntryEarliest(edges.get(1)));
		assertEquals(time("12:25:10"),  gtc.getEntryEarliest(edges.get(2)));
		assertEquals(time("12:30:00"), gtc.getEntryEarliest(edges.get(3)));
		assertEquals(time("12:30:20"), gtc.getEntryEarliest(edges.get(4)));
	}
	
	@Test
	public void testForkGraphDecreaseMaxTime() {
		buildForkGraph();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		Truth.assertThat(gtc.decreaseLatestExit(edges.get(6), TimeUtil.parseTime("12:39:00")))
				.containsExactly(nodes.get(1), nodes.get(2), nodes.get(3), nodes.get(6), nodes.get(0), nodes.get(5),
						nodes.get(-1));
	
		assertEquals(time("12:40:00"), gtc.getExitLatest(edges.get(4)));
		assertEquals(time("12:39:00"), gtc.getExitLatest(edges.get(6)));
		assertEquals(time("12:39:00"), gtc.getExitLatest(edges.get(3)));
		assertEquals(time("12:38:40"), gtc.getExitLatest(edges.get(2)));
		assertEquals(time("12:38:20"), gtc.getExitLatest(edges.get(1)));
		assertEquals(time("12:38:20"), gtc.getExitLatest(edges.get(5)));
	}
	
	@Test
	public void testForkGrapDecreaseMaxTimeMakesIncomingEdgeInfeasible() {
		buildForkGraph();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		Truth.assertThat(gtc.decreaseLatestExit(edges.get(1), TimeUtil.parseTime("12:24:00")))
				.containsExactly(nodes.get(1), nodes.get(0), nodes.get(-1));
		
		Truth.assertThat(gtc.increaseEarliestEntry(edges.get(2), TimeUtil.parseTime("12:25:00")))
				.containsExactlyElementsIn(nodes.values());
		Truth.assertThat(gtc.getInfeasibleEdges()).contains(edges.get(1));
	}
	
	@Test
	public void testForkGraphIncreaseMinTimeMakesPartInfeasible() {
		buildForkGraph();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		Truth.assertThat(gtc.increaseEarliestEntry(edges.get(3), TimeUtil.parseTime("12:38:30")))
			.containsAllOf(nodes.get(2), nodes.get(3), nodes.get(4), nodes.get(6));
		
		Truth.assertThat(gtc.getInfeasibleEdges()).contains(edges.get(6));
		assertWeight(1.0, gtc.getWeight(nodes.get(3)));
		assertWeight(1.0, gtc.getWeight(nodes.get(4)));
		assertWeight(0.0, gtc.getWeight(nodes.get(6)));
		
		OccupationTimes t = gtc.getOccupationTimes(edges.get(1), edges.get(4),
				ImmutableCollections.listOf(
						edges.get(1), edges.get(2), edges.get(3), edges.get(4)));
		assertEquals(time("12:20:00"), t.entryEarliest);
		assertEquals(time("12:39:50"), t.exitEarliest);
		assertEquals(0.5, t.weight, EPS);
		
		Truth.assertThat(gtc.getOccupationTimes(edges.get(1), edges.get(6),
				ImmutableCollections.listOf(
						edges.get(1), edges.get(2), edges.get(3), edges.get(6)))).isNull();
		
		Truth.assertThat(gtc.increaseEarliestEntry(edges.get(1), TimeUtil.parseTime("12:59:59")))
			.containsAllOf(nodes.get(0), nodes.get(1), nodes.get(2), nodes.get(3), nodes.get(4));
		Truth.assertThat(gtc.getInfeasibleEdges())
			.containsAllOf(edges.get(6), edges.get(1));
		assertWeight(1.0, gtc.getWeight(nodes.get(5)));
		assertWeight(0.0, gtc.getWeight(nodes.get(0)));
		
		t = gtc.getOccupationTimes(edges.get(2), edges.get(2),
				ImmutableCollections.listOf(edges.get(2)));
		assertEquals(time("12:20:20"), t.entryEarliest);
		assertEquals(time("12:38:30"), t.exitEarliest);
		assertEquals(1.0, t.weight, EPS);
	}
	
	@Test
	public void testForkGraphIncreaseMinTimeMakesAllInfeasible() {
		buildForkGraph();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		gtc.increaseEarliestEntry(edges.get(2), TimeUtil.parseTime("12:39:00"));
		
		assertFalse(gtc.areFeasible());
	}
	
	@Test
	public void testForkGraphDecreaseMaxTimeMakesOutgoingEdgeInfeasible() {
		buildForkGraph();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		gtc.increaseEarliestEntry(edges.get(6), time("12:35:00"));
		
		Truth.assertThat(gtc.decreaseLatestExit(edges.get(3), time("12:34:00")))
				.containsExactlyElementsIn(graph.getNodes());
		Truth.assertThat(gtc.getInfeasibleEdges()).contains(edges.get(6));
	}

	@Test
	public void testForkGraphDecreaseMaxTimeMakesPartInfeasible() {
		buildForkGraph();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		Map<Integer, Node> nodes = buildNodeIndex(graph);
		Map<Integer, Edge> edges = buildEdgeIndex(graph);
		
		gtc.increaseEarliestEntry(edges.get(5), time("12:30:00"));
		
		Truth.assertThat(gtc.decreaseLatestExit(edges.get(4), TimeUtil.parseTime("12:31:40")))
				.containsAllOf(nodes.get(1), nodes.get(2), nodes.get(3), nodes.get(4));
		Truth.assertThat(gtc.getInfeasibleEdges()).isEmpty();
		
		OccupationTimes t = gtc.getOccupationTimes(edges.get(5), edges.get(4),
				ImmutableCollections.listOf(
						edges.get(5), edges.get(2), edges.get(3), edges.get(4)));
		assertNull(t);
		
		t = gtc.getOccupationTimes(edges.get(1), edges.get(2),
				ImmutableCollections.listOf(edges.get(1), edges.get(2)));
		assertEquals(time("12:20:00"), t.entryEarliest);
		assertEquals(time("12:30:00"), t.exitEarliest);
		assertEquals(time("12:37:40"), t.exitLatest);
		assertEquals(time("12:37:10"), t.entryLatest);
		
		Truth.assertThat(gtc.decreaseLatestExit(edges.get(6), time("12:32:00")))
			.containsAllOf(nodes.get(0), nodes.get(1), nodes.get(2), nodes.get(3), nodes.get(6));
		
		assertTrue(gtc.areFeasible());
		Truth.assertThat(gtc.getInfeasibleEdges())
			.containsAllOf(edges.get(5), edges.get(6));
		
		t = gtc.getOccupationTimes(edges.get(1), edges.get(4), 
				ImmutableCollections.listOf(
						edges.get(1), edges.get(2), edges.get(3), edges.get(4)));
		
		assertWeight(1.0, t.weight);
		assertEquals(time("12:20:00"), t.entryEarliest);
		assertEquals(time("12:31:20"), t.exitEarliest);
		assertEquals(time("12:31:40"), t.exitLatest);
		assertEquals(time("12:29:50"), t.entryLatest);
	}
	
	@Test
	public void testForkGraphDecreaseMaxTimeMakesAllInfeasible() {
		buildForkGraph();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		Map<Integer, Edge> edges = buildEdgeIndex(graph);
		
		gtc.decreaseLatestExit(edges.get(3), TimeUtil.parseTime("12:30:10"));
		
		assertFalse(gtc.areFeasible());
	}
	
	@Test
	public void testSlowFastPathGraphFlowOccupationTimes() {
		buildSlowFastPathGraph();
		Map<Integer, Node> nodes = buildNodeIndex(graph);
		Map<Integer, Edge> edges = buildEdgeIndex(graph);
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		OccupationTimes t = gtc.getOccupationTimes(edges.get(1), edges.get(1), ImmutableCollections.listOf(edges.get(1)));
		assertWeight(1.0, t.weight);
		assertEquals(time("12:00:00"), t.entryEarliest);
		assertEquals(time("12:01:00"), t.exitEarliest);
		assertEquals(time("12:56:00"), t.exitLatest);
		assertEquals(time("12:55:00"), t.entryLatest);
		
		t = gtc.getOccupationTimes(edges.get(3), edges.get(8), 
				ImmutableCollections.listOf(edges.get(3), edges.get(8)));
		assertWeight(0.25, t.weight);
		
		assertEquals(time("12:02:00"), t.entryEarliest);
		assertEquals(time("12:13:00"), t.exitEarliest);
		assertEquals(time("12:59:00"), t.exitLatest);
		assertEquals(time("12:48:00"), t.entryLatest);
	}
	
	@Test
	public void testSlowFastPathDecreaseMaxTimeMakesPartInfeasible() {
		buildSlowFastPathGraph();
		GraphConstraints gtc = GraphConstraints.create(graph, intention);
		
		Truth.assertThat(gtc.decreaseLatestExit(edges.get(5), time("12:15:00"))).containsExactly(nodes.get(1),
				nodes.get(2), nodes.get(3), nodes.get(4), nodes.get(5), nodes.get(0), nodes.get(-1)); // allows only one slow path
		Truth.assertThat(gtc.getInfeasibleEdges()).isEmpty();
		
		OccupationTimes t = gtc.getOccupationTimes(edges.get(1), edges.get(1), ImmutableCollections.listOf(edges.get(1)));
		assertWeight(1.0, t.weight);
		assertEquals(time("12:00:00"), t.entryEarliest);
		assertEquals(time("12:01:00"), t.exitEarliest);
		
		assertEquals(time("12:11:00"), t.exitLatest);
		assertEquals(time("12:10:00"), t.entryLatest);
		
		assertNull(gtc.getOccupationTimes(edges.get(1), edges.get(5), 
				ImmutableCollections.listOf(edges.get(1), edges.get(6), edges.get(3), edges.get(8), edges.get(5))));
		
		Truth.assertThat(gtc.decreaseLatestExit(edges.get(5), time("12:10:00")))
				.containsAllIn(graph.getNodes()); // allows no slow path
		Truth.assertThat(gtc.getInfeasibleEdges()).containsExactly(edges.get(6), edges.get(7), edges.get(8));
		
		t = gtc.getOccupationTimes(edges.get(3), edges.get(3), ImmutableCollections.listOf(edges.get(3)));
		assertEquals(time("12:02:00"), t.entryEarliest);
		assertEquals(time("12:03:00"), t.exitEarliest);
		assertEquals(time("12:08:00"), t.exitLatest);
		assertEquals(time("12:07:00"), t.entryLatest);
	}
	
}
