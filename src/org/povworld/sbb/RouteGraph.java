package org.povworld.sbb;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.povworld.collection.Collection;
import org.povworld.collection.CollectionUtil;
import org.povworld.collection.List;
import org.povworld.collection.MultiMap;
import org.povworld.collection.Set;
import org.povworld.collection.common.TopologySorter;
import org.povworld.collection.common.TopologySorter.Topology;
import org.povworld.collection.immutable.ImmutableArrayList;
import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.collection.immutable.ImmutableHashSet;
import org.povworld.collection.immutable.ImmutableList;
import org.povworld.collection.immutable.ImmutableSet;
import org.povworld.collection.mutable.ArrayList;
import org.povworld.collection.mutable.HashMap;
import org.povworld.collection.mutable.HashSet;
import org.povworld.collection.mutable.HashMultiMap;
import org.povworld.collection.persistent.PersistentCollections;
import org.povworld.collection.persistent.PersistentList;
import org.povworld.sbb.Input.ResourceOccupations;
import org.povworld.sbb.Input.RoutePath;
import org.povworld.sbb.Input.RouteSection;

public class RouteGraph {

	public static final class Node {
		private final int id;
		private final ArrayList<Edge> previous = new ArrayList<>();
		private final ArrayList<Edge> next = new ArrayList<>();

		public Node(int id) {
			this.id = id;
		}

		public int getId() {
			return id;
		}

		public List<Edge> getPrevious() {
			return previous;
		}
		
		public List<Edge> getNext() {
			return next;
		}

		public boolean isSource() {
			return previous.isEmpty();
		}

		public boolean isSink() {
			return next.isEmpty();
		}

		Iterable<Node> getPreviousNodes() {
			ArrayList<Node> nodes = new ArrayList<>(previous.size());
			for (Edge edge : previous) {
				nodes.push(edge.start);
			}
			return nodes;
		}

		Iterable<Node> getNextNodes() {
			ArrayList<Node> nodes = new ArrayList<>(next.size());
			for (Edge edge : next) {
				nodes.push(edge.end);
			}
			return nodes;
		}

		@Override
		public int hashCode() {
			return id;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Node other = (Node) obj;
			if (id != other.id)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return String.valueOf(id);
		}

	}

	public static final class Edge {
		public final Node start;
		public final Node end;
		public final RouteSection section;

		public Edge(Node start, Node end) {
			this(start, end, null);
		}

		public Edge(Node start, Node end, @CheckForNull RouteSection section) {
			this.start = start;
			this.end = end;
			this.section = (section != null) ? section : RouteSection.getDefaultInstance();
		}
		
		public double getPenalty() {
			return section.getPenalty();
		}

		@Override
		public String toString() {
			return "<" + start + "," + end + ">";
		}
		
		@Override
		public int hashCode() {
			return start.id + 255 * end.id;
		}
	}

	private static final Logger logger = Logger.getLogger(RouteGraph.class.getSimpleName());

	private final String id;
	private final Node source;
	private final Node sink;
	private final ImmutableList<Edge> edges;
	private final ImmutableSet<Node> nodes;
	private final MultiMap<String, Edge> edgesByMarker;
	private final ImmutableList<Node> topologicallySortedNodes;
	private final ImmutableList<Edge> topologicallySortedEdges;
	private final MultiMap<Edge, String> edgeResourceOccupations;

	private RouteGraph(String id, Node source, Node sink, ImmutableList<Edge> edges) {
		this.id = id;
		this.source = source;
		this.sink = sink;
		this.edges = edges;
		this.nodes = collectNodes(edges);
		this.edgesByMarker = indexEdgesByMarker(edges);
		this.topologicallySortedNodes = sortNodesTopologically(source);
		this.topologicallySortedEdges = sortEdgesTopologically(source.getNext());
		this.edgeResourceOccupations = createResourceOccpationMap(this);
	}

	private static ImmutableSet<Node> collectNodes(ImmutableList<Edge> edges) {
		ImmutableHashSet.Builder<Node> nodes = ImmutableHashSet.newBuilder();
		for (Edge edge : edges) {
			nodes.add(edge.start);
			nodes.add(edge.end);
		}
		return nodes.build();
	}

	private static MultiMap<String, Edge> indexEdgesByMarker(ImmutableList<Edge> edges) {
		HashMultiMap<String, Edge> map = new HashMultiMap<>();
		for (Edge edge : edges) {
			String marker = getMarker(edge);
			if (marker != null) {
				map.put(marker, edge);
			}
		}
		return map;
	}
	
	@CheckForNull
	private static String getMarker(Edge edge) {
		if (edge.section.getSectionMarkerCount() == 0) {
			return null;
		}
		return edge.section.getSectionMarker(0);
	}

	private static ImmutableList<Node> sortNodesTopologically(Node startNode) {
		class NodeTopology implements Topology<Node> {
			@Override
			public Iterable<Node> getParents(Node node) {
				return node.getNextNodes();
			}
		}
		List<Node> nodes = CollectionUtil.reverse(TopologySorter.sort(
				new NodeTopology(), ImmutableCollections.listOf(startNode), ImmutableArrayList.newBuilder()));
		return ImmutableCollections.asList(nodes);
	}
	
	private static ImmutableList<Edge> sortEdgesTopologically(Collection<Edge> startEdges) {
		class EdgeTopology implements Topology<Edge> {
			@Override
			public Iterable<Edge> getParents(Edge edge) {
				return edge.end.getNext();
			}
		}
		List<Edge> edges = CollectionUtil
				.reverse(TopologySorter.sort(new EdgeTopology(), startEdges, ImmutableArrayList.newBuilder()));
		return ImmutableCollections.asList(edges);
	}

	public String getId() {
		return id;
	}

	public ImmutableSet<Node> getNodes() {
		return nodes;
	}

	public Node getSource() {
		return source;
	}
	
	public Node getSink() {
		return sink;
	}

	public int getNodeCount() {
		return nodes.size();
	}

	public Set<Edge> getEdgesByMarker(String marker) {
		return edgesByMarker.get(marker);
	}

	public ImmutableList<Node> getTopologicallySortedNodes() {
		return topologicallySortedNodes;
	}
	
	public ImmutableList<Edge> getTopologicallySortedEdges() {
		return topologicallySortedEdges;
	}

	public Set<String> getEdgeResourceOccupations(Edge edge) {
		return edgeResourceOccupations.get(edge);
	}

	public RouteGroup generatePaths() {
		return generatePaths("?", 1.0, Double.POSITIVE_INFINITY);
	}

	public RouteGroup generatePaths(String intention, double boost, double maxPenalty) {
		ArrayList<Path> paths = new ArrayList<>();
		for (Edge edgeToStart : source.getNext()) {
			generatePaths(edgeToStart, PersistentCollections.listOf(edgeToStart), paths, 0, maxPenalty);
		}
		return new RouteGroup(intention, boost, paths);
	}

	private void generatePaths(Edge edge, PersistentList<Edge> prefix, ArrayList<Path> result, double penalty,
			double maxPenalty) {
		if (edge.end == sink) {
			result.push(new Path(result.size(), prefix, penalty));
			return;
		}
		
		for (Edge next : edge.end.next) {
			PersistentList<Edge> path = prefix.with(next);
			double newPenalty = penalty + next.section.getPenalty();
			if (newPenalty <= maxPenalty) {
				generatePaths(next, path, result, newPenalty, maxPenalty);
			}
		}
	}

	private static class Builder {
		private final Node source = new Node(-1);
		private final Node sink = new Node(-2);
		private final HashMap<String, Node> nodes = new HashMap<>();
		private final HashSet<Node> startNodes = new HashSet<>();
		private final HashSet<Node> endNodes = new HashSet<>();
		private final ImmutableArrayList.Builder<Edge> edges = ImmutableArrayList.newBuilder();
		private final String id;
		private int nextNodeId = 0;

		public Builder(String id) {
			this.id = id;
		}

		private void add(Input.RoutePath path) {
			if (path.getRouteSectionsCount() == 0) {
				throw new RuntimeException("Path with no RouteSections");
			}
			RouteSection section = path.getRouteSections(0);
			Node node = getNode(section.getRouteAlternativeMarkerAtEntry());
			startNodes.add(node);
			for (int i = 0; i < path.getRouteSectionsCount(); ++i) {
				section = path.getRouteSections(i);
				Node end = getNode(section.getRouteAlternativeMarkerAtExit());
				createEdge(node, end, section);
				node = end;
			}
			endNodes.add(node);
		}
		
		private void createEdge(Node from, Node to, @CheckForNull RouteSection section) {
			Edge e = new Edge(from, to, section);
			edges.add(e);
			from.next.push(e);
			to.previous.push(e);
		}

		private Node getNode(String name) {
			if (name.isEmpty()) {
				return new Node(nextNodeId++);
			}
			Node n = nodes.get(name);
			if (n != null) {
				return n;
			}
			n = new Node(nextNodeId++);
			nodes.put(name, n);
			return n;
		}

		RouteGraph build() {
			for (Node n : startNodes) {
				if (n.previous.isEmpty()) {
					createEdge(source, n, null);
				}
			}

			for (Node n : endNodes) {
				if (n.next.isEmpty()) {
					createEdge(n, sink, null);
				}
			}

			return new RouteGraph(id, source, sink, edges.build());
		}
	}

	public static RouteGraph build(Input.Route route) {
		Builder builder = new Builder(route.getId());
		for (RoutePath path : route.getRoutePathsList()) {
			builder.add(path);
		}
		return builder.build();
	}

	private static MultiMap<Edge, String> createResourceOccpationMap(RouteGraph graph) {
		HashMultiMap<Edge, String> map = new HashMultiMap<>();

		for (Edge edge : graph.getEdges()) {
			for (ResourceOccupations ro : edge.section.getResourceOccupationsList()) {
				map.put(edge, ro.getResource());
			}
		}

		// Fill gaps for multi-use of resources:
		for (Path path : graph.generatePaths().getPaths()) {
			HashMap<String, Integer> occupationEntry = new HashMap<>();
			HashMap<String, Integer> occupationExit = new HashMap<>();

			int node = 0;
			for (Edge edge : path.getEdges()) {
				for (String resource : ImmutableCollections.asList(map.get(edge))) {
					map.put(edge, resource);
					if (occupationEntry.containsKey(resource)) {
						int exit = occupationExit.get(resource);

						if (exit != node) {
							logger.log(Level.WARNING, "Multi-use of resource " + resource + " by " + graph.getId()
									+ " last exit was " + exit + ", new entry at " + node + ".");
							for (int i = exit; i < node; ++i) {
								map.put(path.getEdges().get(i), resource);
							}
							// TODO increase min time delta for instance 09?
						}

						// Update exit node.
						occupationExit.put(resource, node + 1);
					} else {
						occupationEntry.put(resource, node);
						occupationExit.put(resource, node + 1);
					}

				}
				node++;
			}
		}
		return map;
	}

	ImmutableList<Edge> getEdges() {
		return edges;
	}

}
