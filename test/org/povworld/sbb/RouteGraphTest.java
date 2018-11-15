package org.povworld.sbb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.povworld.collection.immutable.ImmutableList;
import org.povworld.collection.mutable.HashMap;
import org.povworld.sbb.Input.Route;
import org.povworld.sbb.Input.Route.Builder;
import org.povworld.sbb.Input.RoutePath;
import org.povworld.sbb.Input.RouteSection;
import org.povworld.sbb.RouteGraph.Edge;

import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;

public class RouteGraphTest {
	
	private static class PathComparator extends Correspondence<Path, Path> {
		@Override
		public boolean compare(Path actual, Path expected) {
			if (!actual.getEdges().equals(expected.getEdges())) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			return "equals to ";
		}
	}
	
	private static final PathComparator PATH_COMPARATOR = new PathComparator();
	private static final double EPS = 1e-8;
	
	private HashMap<RouteSection, Edge> edgeIndex = null;
	
	private void createEdgeIndex(RouteGraph graph) {
		ImmutableList<Edge> edges = graph.getEdges();
		HashMap<RouteSection, Edge> map = new HashMap<>();
		for(Edge edge: edges) {
			map.put(edge.section, edge);
		}
		this.edgeIndex = map;
	}
	
	private Edge[] edges(RouteSection... sections) {
		Edge[] result = new Edge[sections.length + 2];
		result[0] = edgeIndex.get(sections[0]).start.getPrevious().getFirst();
		for(int i=0; i<sections.length; ++i) {
			result[i + 1] = edgeIndex.get(sections[i]);
		}
		result[sections.length + 1] = edgeIndex.get(sections[sections.length - 1]).end.getNext().getFirst();
		return result;
	}
	
	@Test
	public void linearGraph() {
		Builder builder = Route.newBuilder();
		builder.setId("123");
		RouteSection s1 = RouteSection.newBuilder().setSequenceNumber(1).setPenalty(0.4).build();
		RouteSection s2 = RouteSection.newBuilder().setSequenceNumber(2).setPenalty(0.1).build();
		RouteSection s3 = RouteSection.newBuilder().setSequenceNumber(3).setPenalty(0.5).build();
		builder.addRoutePaths(RoutePath.newBuilder()
			.addRouteSections(s1)
			.addRouteSections(s2)
			.addRouteSections(s3));
		RouteGraph graph = RouteGraph.build(builder.build());
		createEdgeIndex(graph);
		
		RouteGroup routeGroup = graph.generatePaths();
		
		Truth.assertThat(routeGroup.getPaths()).comparingElementsUsing(PATH_COMPARATOR)
			.containsExactly(new Path(0, 1.0, edges(s1, s2, s3)));
		assertEquals(1.0, routeGroup.getBoostedWeight(), 0);
	}
	
	@Test
	public void forkMinimal() {
		Builder builder = Route.newBuilder();
		builder.setId("123");
		String jointA = "A";
		String jointB = "B";
		RouteSection s1 = RouteSection.newBuilder().setSequenceNumber(1).setRouteAlternativeMarkerAtEntry(jointA).setRouteAlternativeMarkerAtExit(jointB).build();
		RouteSection s2 = RouteSection.newBuilder().setSequenceNumber(2).setRouteAlternativeMarkerAtEntry(jointA).setRouteAlternativeMarkerAtExit(jointB).build();
		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s1));
		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s2));
		RouteGraph graph = RouteGraph.build(builder.build());
		createEdgeIndex(graph);
		
		RouteGroup routeGroup = graph.generatePaths();
		
		Truth.assertThat(routeGroup.getPaths()).comparingElementsUsing(PATH_COMPARATOR).containsExactly(
				new Path(0, 0.0, edges(s1)),
				new Path(1, 0.0, edges(s2)));
		assertEquals(0.5, routeGroup.getBoostedWeight(), 0);
	}
	
	@Test
	public void forkedBeginning() {
		Builder builder = Route.newBuilder();
		String joint = "xx";
		builder.setId("123");
		RouteSection s1 = RouteSection.newBuilder().setSequenceNumber(1).setRouteAlternativeMarkerAtExit(joint).build();
		RouteSection s2 = RouteSection.newBuilder().setSequenceNumber(2).setRouteAlternativeMarkerAtEntry(joint).build();
		RouteSection s3 = RouteSection.newBuilder().setSequenceNumber(3).build();
		RouteSection s4 = RouteSection.newBuilder().setSequenceNumber(4).setRouteAlternativeMarkerAtExit(joint).setPenalty(0.1).build();
		RouteSection s5 = RouteSection.newBuilder().setSequenceNumber(5).setPenalty(0.2).build();
		RouteSection s6 = RouteSection.newBuilder().setSequenceNumber(6).setRouteAlternativeMarkerAtExit(joint).setPenalty(0.4).build();

		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s1)
				.addRouteSections(s2)
				.addRouteSections(s3));
		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s4));
		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s5)
				.addRouteSections(s6));
		
		RouteGraph graph = RouteGraph.build(builder.build());
		createEdgeIndex(graph);
		
		RouteGroup routeGraph = graph.generatePaths();
		
		Truth.assertThat(routeGraph.getPaths()).comparingElementsUsing(PATH_COMPARATOR).containsExactly(
				new Path(0, 0.0, edges(s1, s2, s3)),
				new Path(1, 0.1, edges(s4, s2, s3)),
				new Path(2, 0.6, edges(s5, s6, s2, s3)));
		assertEquals(1.0/3, routeGraph.getBoostedWeight(), EPS);
	}
	
	@Test
	public void forkedEnding() {
		Builder builder = Route.newBuilder();
		String joint = "xx";
		builder.setId("123");
		RouteSection s1 = RouteSection.newBuilder().setSequenceNumber(1).setRouteAlternativeMarkerAtExit(joint).build();
		RouteSection s2 = RouteSection.newBuilder().setSequenceNumber(2).setRouteAlternativeMarkerAtEntry(joint).build();
		RouteSection s3 = RouteSection.newBuilder().setSequenceNumber(3).build();
		RouteSection s4 = RouteSection.newBuilder().setSequenceNumber(4).setRouteAlternativeMarkerAtEntry(joint).build();
		RouteSection s5 = RouteSection.newBuilder().setSequenceNumber(5).setRouteAlternativeMarkerAtEntry(joint).setPenalty(0.1).build();
		RouteSection s6 = RouteSection.newBuilder().setSequenceNumber(6).setPenalty(0.2).build();

		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s1)
				.addRouteSections(s2)
				.addRouteSections(s3));
		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s4));
		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s5)
				.addRouteSections(s6));
		
		RouteGraph graph = RouteGraph.build(builder.build());
		createEdgeIndex(graph);
		
		Truth.assertThat(graph.generatePaths().getPaths()).comparingElementsUsing(PATH_COMPARATOR).containsExactly(
				new Path(0, 0.0, edges(s1, s2, s3)),
				new Path(1, 0.0, edges(s1, s4)),
				new Path(2, 0.3, edges(s1, s5, s6)));
	}
	
	@Test
	public void forkedMiddle() {
		Builder builder = Route.newBuilder();
		String joint12 = "12";
		String joint23 = "23";
		String joint34 = "E";
		builder.setId("123");
		RouteSection s1 = RouteSection.newBuilder().setSequenceNumber(1).setRouteAlternativeMarkerAtExit(joint12).build();
		RouteSection s2 = RouteSection.newBuilder().setSequenceNumber(2).setPenalty(1.0).setRouteAlternativeMarkerAtEntry(joint12).setRouteAlternativeMarkerAtExit(joint23).build();
		RouteSection s3 = RouteSection.newBuilder().setSequenceNumber(3).setRouteAlternativeMarkerAtEntry(joint23).setRouteAlternativeMarkerAtExit(joint34).build();
		RouteSection s4 = RouteSection.newBuilder().setSequenceNumber(4).setRouteAlternativeMarkerAtEntry(joint34).build();
		RouteSection s5 = RouteSection.newBuilder().setSequenceNumber(5).setRouteAlternativeMarkerAtEntry(joint12).build();
		RouteSection s6 = RouteSection.newBuilder().setSequenceNumber(6).build();
		RouteSection s7 = RouteSection.newBuilder().setSequenceNumber(7).setRouteAlternativeMarkerAtExit(joint34).build();
		RouteSection s8 = RouteSection.newBuilder().setSequenceNumber(8).setRouteAlternativeMarkerAtEntry(joint23).setRouteAlternativeMarkerAtExit(joint34).build();

		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s1)
				.addRouteSections(s2)
				.addRouteSections(s3)
				.addRouteSections(s4));
		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s5)
				.addRouteSections(s6)
				.addRouteSections(s7));
		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s8));
		
		RouteGraph graph = RouteGraph.build(builder.build());
		createEdgeIndex(graph);
		
		Truth.assertThat(graph.generatePaths().getPaths()).comparingElementsUsing(PATH_COMPARATOR).containsExactly(
				new Path(0, 1.0, edges(s1, s2, s3, s4)),
				new Path(1, 0.0, edges(s1, s5, s6, s7, s4)),
				new Path(2, 1.0, edges(s1, s2, s8, s4)));
	}
	
	@Test 
	public void mutliFork() {
		Builder builder = Route.newBuilder();
		String jointA = "A";
		String jointB = "B";
		builder.setId("123");
		RouteSection s1 = RouteSection.newBuilder().setSequenceNumber(1).setRouteAlternativeMarkerAtExit(jointA).build();
		RouteSection s2 = RouteSection.newBuilder().setSequenceNumber(2).setPenalty(1.0).setRouteAlternativeMarkerAtEntry(jointA).build();
		RouteSection s3 = RouteSection.newBuilder().setSequenceNumber(3).setRouteAlternativeMarkerAtExit(jointB).build();
		RouteSection s4 = RouteSection.newBuilder().setSequenceNumber(4).setRouteAlternativeMarkerAtEntry(jointA).build();
		RouteSection s5 = RouteSection.newBuilder().setSequenceNumber(5).setRouteAlternativeMarkerAtExit(jointB).build();
		RouteSection s6 = RouteSection.newBuilder().setSequenceNumber(6).setRouteAlternativeMarkerAtEntry(jointB).build();
		RouteSection s7 = RouteSection.newBuilder().setSequenceNumber(7).setPenalty(0.1).setRouteAlternativeMarkerAtExit(jointA).build();
		RouteSection s8 = RouteSection.newBuilder().setSequenceNumber(8).setPenalty(0.2).setRouteAlternativeMarkerAtEntry(jointB).build();

		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s1)
				.addRouteSections(s2)
				.addRouteSections(s3));
		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s4)
				.addRouteSections(s5)
				.addRouteSections(s6));
		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s7));
		builder.addRoutePaths(RoutePath.newBuilder()
				.addRouteSections(s8));
		
		RouteGraph graph = RouteGraph.build(builder.build());
		createEdgeIndex(graph);
		
		Truth.assertThat(graph.generatePaths().getPaths()).comparingElementsUsing(PATH_COMPARATOR).containsExactly(
				new Path(0, 1.0, edges(s1, s2, s3, s6)),
				new Path(1, 1.2, edges(s1, s2, s3, s8)),
				new Path(2, 1.1, edges(s7, s2, s3, s6)),
				new Path(3, 1.3, edges(s7, s2, s3, s8)),
				new Path(4, 0.0, edges(s1, s4, s5, s6)),
				new Path(5, 0.2, edges(s1, s4, s5, s8)),
				new Path(6, 0.1, edges(s7, s4, s5, s6)),
				new Path(7, 0.3, edges(s7, s4, s5, s8)));
	}
	
}
