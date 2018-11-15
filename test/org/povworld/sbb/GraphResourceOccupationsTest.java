package org.povworld.sbb;

import org.junit.Test;
import org.povworld.collection.Collection;
import org.povworld.collection.Map;
import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.sbb.GraphResourceOccupations.ResourceOccupation;
import org.povworld.sbb.Input.Route;
import org.povworld.sbb.Input.ServiceIntention;
import org.povworld.sbb.RouteGraph.Edge;
import org.povworld.sbb.RouteGraph.Node;

import com.google.common.truth.Correspondence;
import com.google.common.truth.IterableSubject.UsingCorrespondence;
import com.google.common.truth.Truth;

public class GraphResourceOccupationsTest extends GraphTestBase {
	
	private static final class ResourceOccupationComparator extends Correspondence<ResourceOccupation, ResourceOccupation> {

		@Override
		public boolean compare(ResourceOccupation actual, ResourceOccupation expected) {
			if (!actual.intention.equals(expected.intention)) {
				return false;
			}
			if (!actual.resource.equals(expected.resource)) {
				return false;
			}
			if (actual.start != expected.start) {
				return false;
			}
			if (actual.end != expected.end) {
				return false;
			}
			if (!actual.flow.equals(expected.flow)) {
				return false;
			}
			if (actual.minDuration != expected.minDuration) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			return "is";
		}
		
	}
	
	private UsingCorrespondence<ResourceOccupation, ResourceOccupation> assertThat(Collection<ResourceOccupation> occupation) {
		return Truth.assertThat(occupation).comparingElementsUsing(new ResourceOccupationComparator());
	}
	
	@Test
	public void testSimplePath() {
		buildSimplePath();
		Map<Integer, Node> nodes = buildNodeIndex(graph);
		Map<Integer, Edge> edges = buildEdgeIndex(graph);
		
		GraphResourceOccupations gro = GraphResourceOccupations.create(graph, intention);
		
		Truth.assertThat(gro.getOccupiedResources()).containsExactly("x", "y", "z");
		
		ResourceOccupation xOcc = new ResourceOccupation(intention.getId(), "x", edges.get(2), edges.get(3),
				ImmutableCollections.listOf(edges.get(2), edges.get(3)), 50);
		ResourceOccupation yOcc = new ResourceOccupation(intention.getId(), "y", edges.get(3), edges.get(5),
				ImmutableCollections.listOf(edges.get(3), edges.get(4), edges.get(5)), 100);
		ResourceOccupation zOcc = new ResourceOccupation(intention.getId(), "z", edges.get(1), edges.get(1),
				ImmutableCollections.listOf(edges.get(1)), 10);
		
		assertThat(gro.getOccupations("x")).containsExactly(xOcc);
		assertThat(gro.getOccupations("y")).containsExactly(yOcc);
		assertThat(gro.getOccupations("z")).containsExactly(zOcc);
		
		assertThat(gro.getOccupationsStartingAt(nodes.get(0))).containsExactly(zOcc);
		assertThat(gro.getOccupationsStartingAt(nodes.get(1))).containsExactly(xOcc);
		assertThat(gro.getOccupationsStartingAt(nodes.get(2))).containsExactly(yOcc);
		assertThat(gro.getOccupationsStartingAt(nodes.get(3))).containsExactly();
		
		assertThat(gro.getOccupationsEndingAt(nodes.get(0))).containsExactly();
		assertThat(gro.getOccupationsEndingAt(nodes.get(1))).containsExactly(zOcc);
		assertThat(gro.getOccupationsEndingAt(nodes.get(3))).containsExactly(xOcc);
	}
	
	@Test
	public void testMergeMultiResourceOccupation() {
		Route.Builder builder = Route.newBuilder();
		builder.setId("123");
		builder.addRoutePaths(path(
			section(10, "", "", "", "x"),
			section(20),
			section(30, "", "", "", "x")));
		route = builder.build();
		graph = RouteGraph.build(route);
		intention = ServiceIntention.newBuilder().build();
		
		GraphResourceOccupations gro = GraphResourceOccupations.create(graph, intention);
		
		Map<Integer, Edge> edges = buildEdgeIndex(graph);
		
		Truth.assertThat(gro.getOccupiedResources()).containsExactly("x");
		
		assertThat(gro.getOccupations("x")).containsExactly(
					new ResourceOccupation(intention.getId(), "x", edges.get(1), edges.get(3),
							ImmutableCollections.listOf(edges.get(1), edges.get(2), edges.get(3)), 60));
	}
	
	@Test
	public void testForkedGraph() {
		buildForkGraph();
		
		GraphResourceOccupations gro = GraphResourceOccupations.create(graph, intention);
		
		Map<Integer, Edge> edges = buildEdgeIndex(graph);
		
		Truth.assertThat(gro.getOccupiedResources()).containsExactly("x", "y");
		
		assertThat(gro.getOccupations("y")).containsExactly(
				new ResourceOccupation(intention.getId(), "y", edges.get(2), edges.get(3), 
						ImmutableCollections.listOf(
								edges.get(2), edges.get(3)), 40));
		
		assertThat(gro.getOccupations("x")).containsExactly(
				new ResourceOccupation(intention.getId(), "x", edges.get(1), edges.get(4), 
						ImmutableCollections.listOf(
								edges.get(1), edges.get(2), edges.get(3), edges.get(4)), 110),
				new ResourceOccupation(intention.getId(), "x", edges.get(1), edges.get(3), 
						ImmutableCollections.listOf(
								edges.get(1), edges.get(2), edges.get(3), edges.get(6)), 50),
				new ResourceOccupation(intention.getId(), "x", edges.get(2), edges.get(4), 
						ImmutableCollections.listOf(
								edges.get(5), edges.get(2), edges.get(3), edges.get(4)), 100),
				new ResourceOccupation(intention.getId(), "x", edges.get(2), edges.get(3), 
						ImmutableCollections.listOf(
								edges.get(5), edges.get(2), edges.get(3), edges.get(6)), 40));
	}
	
}
