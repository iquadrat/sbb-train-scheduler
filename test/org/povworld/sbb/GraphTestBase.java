package org.povworld.sbb;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.povworld.collection.Map;
import org.povworld.collection.mutable.HashMap;
import org.povworld.sbb.Input.ResourceOccupations;
import org.povworld.sbb.Input.Route;
import org.povworld.sbb.Input.RoutePath;
import org.povworld.sbb.Input.RouteSection;
import org.povworld.sbb.Input.RouteSection.Builder;
import org.povworld.sbb.RouteGraph.Edge;
import org.povworld.sbb.RouteGraph.Node;
import org.povworld.sbb.Input.SectionRequirement;
import org.povworld.sbb.Input.ServiceIntention;

public class GraphTestBase {

	private int nextSequenceNumber = 1;
	
	protected void resetSectionSequenceNumber() {
		nextSequenceNumber = 1;
	}

	protected RouteSection section(int minRunningTime, String marker, String altBegin, String altEnd, String... occupations) {
		Builder builder = RouteSection.newBuilder()
				.setSequenceNumber(nextSequenceNumber++)
				.setMinimumRunningTimeSeconds(minRunningTime)
				.addSectionMarker(marker)
				.setRouteAlternativeMarkerAtEntry(altBegin)
				.setRouteAlternativeMarkerAtExit(altEnd);
		
		for(String occupation: occupations) {
			builder.addResourceOccupations(ResourceOccupations.newBuilder().setResource(occupation));
		}
		
		return builder.build();		
	}

	protected RouteSection section(int minRunningTime) {
		return section(minRunningTime, "", "", "");
	}

	protected RouteSection section(int minRunningTime, String altBegin) {
		return section(minRunningTime, altBegin, "", "");
	}

	protected RoutePath path(RouteSection... routeSections) {
		RoutePath.Builder b = RoutePath.newBuilder();
		b.addAllRouteSections(Arrays.asList(routeSections));
		return b.build();
	}
	
	protected RouteGraph.Edge edge(Node start, Node end) {
		return new RouteGraph.Edge(start, end);
	}
	
	protected Map<Integer, Node> buildNodeIndex(RouteGraph graph) {
		HashMap<Integer, Node> nodeIndex = new HashMap<>();
		for(Edge edge: graph.getEdges()) {
			nodeIndex.put(edge.start.getId(), edge.start);
			nodeIndex.put(edge.end.getId(), edge.end);
		}
		return nodeIndex;
	}
	
	protected Map<Integer, Edge> buildEdgeIndex(RouteGraph graph) {
		HashMap<Integer, Edge> edgeIndex = new HashMap<>();
		for(Edge edge: graph.getEdges()) {
			edgeIndex.put(edge.section.getSequenceNumber(), edge);
		}
		return edgeIndex;
	}
	
	protected void assertWeight(double expected, double actual) {
		assertEquals(expected, actual, GraphConstraints.EPS);
	}
	
	protected int time(String time) {
		return TimeUtil.parseTime(time);
	}

	protected Route route;
	protected ServiceIntention intention;
	protected RouteGraph graph;
	protected Map<Integer, Node> nodes;
	protected Map<Integer, Edge> edges;

	
	protected void init(Route route) {
		this.route = route;
		this.graph = RouteGraph.build(route);
		this.nodes = buildNodeIndex(graph);
		this.edges = buildEdgeIndex(graph);
	}

	protected void buildSimplePath() {
		Route.Builder builder = Route.newBuilder();
		builder.setId("simple");
		builder.addRoutePaths(path(
			section(10, "A", "", "", "z"),
			section(20, "", "", "", "x"),
			section(30, "B", "", "", "x", "y"),
			section(20, "", "" ,"", "y"),
			section(50, "C", "", "", "y")));
		init(builder.build());
	
		intention = ServiceIntention.newBuilder()
				.addSectionRequirements(
						SectionRequirement.newBuilder()
							.setSectionMarker("A")
							.setEntryEarliest("12:20:00"))
				.addSectionRequirements(
						SectionRequirement.newBuilder()
							.setSectionMarker("B")
							.setEntryEarliest("12:30:00")
							.setEntryLatest("12:50:00"))
				.addSectionRequirements(
						SectionRequirement.newBuilder()
							.setSectionMarker("C")
							.setExitEarliest("12:30:01")
							.setExitLatest("13:00:00"))
				.build();
	}

	protected void buildForkGraph() {
		String jointA = "ja";
		String jointB = "jb";
		
		Route.Builder builder = Route.newBuilder();
		builder.setId("fork");
		builder.addRoutePaths(path(
				section(10, "A", "", jointA, "x"),
				section(20, "", jointA, "", "x", "y"),
				section(20, "M", "", jointB, "x", "y"),
				section(60, "B", jointB, "", "x")));
		builder.addRoutePaths(path(
				section(20, "A", "", jointA)));
		builder.addRoutePaths(path(
				section(120, "B", jointB, "")));
		init(builder.build());
		
		intention = ServiceIntention.newBuilder()
				.addSectionRequirements(
						SectionRequirement.newBuilder().setSectionMarker("A").setEntryEarliest("12:20:00"))
				.addSectionRequirements(
						SectionRequirement.newBuilder().setSectionMarker("M").setEntryEarliest("12:30:00"))
				.addSectionRequirements(
						SectionRequirement.newBuilder().setSectionMarker("B").setExitLatest("12:40:00"))
				.build();
	}

	protected void buildComplexGraph() {
		Route.Builder builder = Route.newBuilder();
		builder.setId("complex");
		builder.addRoutePaths(path(
			section(10, "S", "", "A1"),
			section(20, "",  "A1", ""),
			section(40, "M"),
			section(20, "", "", "C"),
			section(120,"", "C", "D"),
			section(30, "", "D", ""),
			section(30, "", "", "E"),
			section(60, "F","E", "")));
		builder.addRoutePaths(path(
			section(20, "S", "", "A2"),
			section(10, "", "A2", "B1"),
			section(60, "M","B1", ""),
			section(20, "", "", "C")));
		builder.addRoutePaths(path(
			section(30, "S", "", "A3"),
			section(20, "", "A3", "B2"),
			section(70, "M", "B2", ""),
			section(10, "", "", "C")));
		builder.addRoutePaths(path(
			section(30, "", "A1", "B1")));
		builder.addRoutePaths(path(
				section(20, "", "A2", "B2")));
		builder.addRoutePaths(path(
				section(30, "", "D", ""),
				section(10, "", "", "E")));
		init(builder.build());
		
		intention = ServiceIntention.newBuilder()
				.addSectionRequirements(
						SectionRequirement.newBuilder().setSectionMarker("S").setEntryEarliest("12:00:00"))
				.addSectionRequirements(
						SectionRequirement.newBuilder().setSectionMarker("M").setExitEarliest("12:30:00").setExitLatest("12:55:50"))
				.addSectionRequirements(
						SectionRequirement.newBuilder().setSectionMarker("C").setExitEarliest("12:30:01"))
				.addSectionRequirements(
						SectionRequirement.newBuilder().setSectionMarker("F").setExitLatest("13:00:00"))
				.build();
	}
	
	protected void buildSlowFastPathGraph() {
		Route.Builder builder = Route.newBuilder();
		builder.setId("slow-fast");
		builder.addRoutePaths(path(
				section(60, "A", "", "X1"),
				section(60, "", "X1", "X2", "Ra"),
				section(60, "", "X2", "X3", "Ra", "Rb"),
				section(60, "", "X3" ,"X4", "Rb"),
				section(60, "B", "X4", "")));
		builder.addRoutePaths(path(
				section(600, "", "X1", "X2", "Ry"),
				section(600, "", "X2", "X3", "Ry"),
				section(600, "", "X3" ,"X4", "Rz")));
		init(builder.build());
		
		intention = ServiceIntention.newBuilder()
				.addSectionRequirements(
						SectionRequirement.newBuilder().setSectionMarker("A").setEntryEarliest("12:00:00"))
				.addSectionRequirements(
						SectionRequirement.newBuilder().setSectionMarker("B").setExitLatest("13:00:00"))
				.build();
	}

}
