package org.povworld.sbb;


import java.util.Iterator;

import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.collection.immutable.ImmutableList;
import org.povworld.sbb.RouteGraph.Edge;

public class Path {
	
	private final int id;
	private final ImmutableList<Edge> edges;
	private final double penalty;

	public Path(int id, double penalty, Edge... edges) {
		this(id, ImmutableCollections.listOf(edges), penalty);
	}

	public Path(int id, ImmutableList<Edge> edges, double penalty) {
		this.id = id;
		this.edges = edges;
		this.penalty = penalty;
	}

	public int getId() {
		return id;
	}

	public ImmutableList<Edge> getEdges() {
		return edges;
	}

	public double getPenalty() {
		return penalty;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("(" + id + ") ");
		Iterator<Edge> i = edges.iterator();
		while (i.hasNext()) {
			Edge edge = i.next();
			sb.append(edge.section.getSequenceNumber());
			if (i.hasNext()) {
				sb.append(" - ");
			}
		}
		if (penalty > 0) {
			sb.append("(+");
			sb.append(penalty);
			sb.append(")");
		}
		return sb.toString();
	}
}