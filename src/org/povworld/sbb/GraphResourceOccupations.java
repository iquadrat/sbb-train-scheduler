package org.povworld.sbb;

import java.util.logging.Logger;

import org.povworld.collection.Collection;
import org.povworld.collection.Map;
import org.povworld.collection.MultiMap;
import org.povworld.collection.common.PreConditions;
import org.povworld.collection.immutable.ImmutableList;
import org.povworld.collection.mutable.HashMap;
import org.povworld.collection.mutable.HashMultiMap;
import org.povworld.collection.persistent.PersistentCollections;
import org.povworld.collection.persistent.PersistentList;
import org.povworld.sbb.Input.SectionRequirement;
import org.povworld.sbb.Input.ServiceIntention;
import org.povworld.sbb.RouteGraph.Edge;
import org.povworld.sbb.RouteGraph.Node;

public class GraphResourceOccupations {

	private static final SectionRequirement NO_REQUIREMENTS = SectionRequirement.getDefaultInstance();

	public static class ResourceOccupation {
		public final String intention;
		public final String resource;

		public final Edge start;
		public final Edge end;
		public final ImmutableList<Edge> flow;

		public final int minDuration;

		public ResourceOccupation(String intention, String resource, Edge start, Edge end, ImmutableList<Edge> flow,
				int minDuration) {
			PreConditions.paramNotEmpty(flow);
			this.intention = intention;
			this.resource = resource;
			this.start = start;
			this.end = end;
			this.flow = flow;
			this.minDuration = minDuration;
		}

		public Edge getStart() {
			return start;
		}

		public Edge getEnd() {
			return end;
		}

		@Override
		public String toString() {
			return start + "-" + end + " via " + flow + " (" + minDuration + ")s";
		}

		public int getFlowStartIndex() {
			return flow.getFirst() == start ? 0 : 1;
		}
		
		public int getFlowEndIndex() {
			return flow.getLast() == end ? flow.size() - 1 : flow.size() - 2;
		}
		
	}
	
	private static final Logger logger = Logger.getLogger(GraphResourceOccupations.class.getSimpleName());

	private final MultiMap<String, ResourceOccupation> occupationsByResource;
	private final MultiMap<Node, ResourceOccupation> occupationsByStartNode;
	private final MultiMap<Node, ResourceOccupation> occupationsByEndNode;

	private GraphResourceOccupations(
			MultiMap<String, ResourceOccupation> occupationsByResource,
			MultiMap<Node, ResourceOccupation> occupationsByStartNode,
			MultiMap<Node, ResourceOccupation> occupationsByEndNode) {
		this.occupationsByResource = occupationsByResource;
		this.occupationsByStartNode = occupationsByStartNode;
		this.occupationsByEndNode = occupationsByEndNode;
	}

	public Collection<String> getOccupiedResources() {
		return occupationsByResource.keys();
	}

	public Collection<ResourceOccupation> getOccupations(String resource) {
		return occupationsByResource.get(resource);
	}

	public Collection<ResourceOccupation> getOccupationsStartingAt(Node node) {
		return occupationsByStartNode.get(node);
	}

	public Collection<ResourceOccupation> getOccupationsEndingAt(Node node) {
		return occupationsByEndNode.get(node);
	}

	public static GraphResourceOccupations create(RouteGraph graph, ServiceIntention intention) {
		return new Builder(graph, intention).build();
	}
	
	public static Map<Edge, Integer> findEdgeMinDurations(RouteGraph graph, ServiceIntention intention) {
		HashMap<String, SectionRequirement> requirements = new HashMap<>();

		for (SectionRequirement r : intention.getSectionRequirementsList()) {
			if (!r.getSectionMarker().isEmpty()) {
				requirements.put(r.getSectionMarker(), r);
			}
		}

		HashMap<Edge, Integer> minDurations = new HashMap<>();

		for (Edge edge : graph.getEdges()) {
			int minStoppingTime = 0;
			if (edge.section.getSectionMarkerCount() > 0) {
				minStoppingTime = requirements.getOrDefault(edge.section.getSectionMarker(0), NO_REQUIREMENTS)
						.getMinStoppingTimeSeconds();
			}
			int minDuration = edge.section.getMinimumRunningTimeSeconds() + minStoppingTime;
			minDurations.put(edge, minDuration);
		}

		return minDurations;
	}
	
	private static class Builder {
		private final RouteGraph graph;
		private final ServiceIntention intention;
		private final Map<Edge, Integer> minDurations;
		private final HashMultiMap<String, ResourceOccupation> occupationsByResource = new HashMultiMap<>();
		private final HashMultiMap<Node, ResourceOccupation> occupationsByStartNode = new HashMultiMap<>();
		private final HashMultiMap<Node, ResourceOccupation> occupationsByEndNode = new HashMultiMap<>();

		public Builder(RouteGraph graph, ServiceIntention intention) {
			this.graph = graph;
			this.intention = intention;
			this.minDurations = findEdgeMinDurations(graph, intention);
		}

		GraphResourceOccupations build() {

			for (Node node : graph.getTopologicallySortedNodes()) {
				for (Edge edge : node.getNext()) {
					for (String resource : graph.getEdgeResourceOccupations(edge)) {
						int incomingOccupied = edgesOccupyCount(node.getPrevious(), resource);
						// TODO remove !node.isSource()?
						if (!node.isSource() && (incomingOccupied == node.getPrevious().size())) {
							continue;
						}

						int minDuration = minDurations.get(edge);
						if (incomingOccupied == 0) {
							traceResourceOccupation(edge, PersistentCollections.listOf(edge), edge, resource,
									minDuration);
						} else {
							for (Edge previous : node.getPrevious()) {
								if (edgeOccupies(previous, resource)) {
									continue;
								}
								traceResourceOccupation(edge,
										PersistentCollections.listOf(previous, edge), edge, resource,
										minDuration);
							}
						}

					}

				}
			}

			return new GraphResourceOccupations(occupationsByResource, occupationsByStartNode, occupationsByEndNode);
		}


		private void addOccupation(ResourceOccupation occupation) {
			occupationsByResource.put(occupation.resource, occupation);
			occupationsByStartNode.put(occupation.start.start, occupation);
			occupationsByEndNode.put(occupation.end.end, occupation);
		}

		private void traceResourceOccupation(Edge start, PersistentList<Edge> flow, Edge current, String resource,
				int minDuration) {
			int outgoingCount = edgesOccupyCount(current.end.getNext(), resource);
			// TODO remove !current.end.isSink()
			if (!current.end.isSink() && outgoingCount == current.end.getNext().size()) {
				for (Edge edge : current.end.getNext()) {
					traceResourceOccupation(start, flow.with(edge), edge, resource,
							minDuration + minDurations.get(edge));
				}
			} else if (outgoingCount == 0) {
				addOccupation(new ResourceOccupation(intention.getId(), resource, start, current,
						flow, minDuration));
			} else {
				for (Edge edge : current.end.getNext()) {
					if (edgeOccupies(edge, resource)) {
						traceResourceOccupation(start, flow.with(edge), edge, resource,
								minDuration + minDurations.get(edge));
					} else {
						addOccupation(new ResourceOccupation(intention.getId(), resource, start, current,
								flow.with(edge), minDuration));
					}
				}
			}
		}

		private int edgesOccupyCount(Iterable<Edge> edges, String resource) {
			int count = 0;
			for (Edge edge : edges) {
				boolean occupies = edgeOccupies(edge, resource);
				if (occupies) {
					count++;
				}
			}
			return count;
		}

		private boolean edgeOccupies(Edge edge, String resource) {
			return graph.getEdgeResourceOccupations(edge).contains(resource);
		}
		
	}

}
