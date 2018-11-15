package org.povworld.sbb;

import org.povworld.collection.common.PreConditions;

public class PathSchedule {
	private final Path path;
	private final int[] nodeTimes;
	private final double penalty;

	public PathSchedule(Path path, int[] nodeTimes, double penalty) {
		PreConditions.conditionCheck("Path and nodeTimes length does not match!",
				path.getEdges().size() - 1 == nodeTimes.length);
		this.path = path;
		this.nodeTimes = nodeTimes;
		this.penalty = penalty;
	}

	public Path getPath() {
		return path;
	}

	public int getNodeTime(int node) {
		return nodeTimes[node];
	}

	public double getPathPenalty() {
		return path.getPenalty();
	}
	
	public double getTotalPenalty() {
		return penalty;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < nodeTimes.length - 1; ++i) {
			sb.append(nodeTimes[i]);
			sb.append(' ');
			sb.append(path.getEdges().get(i + 1).section.getSequenceNumber());
			sb.append(' ');
		}
		sb.append(nodeTimes[nodeTimes.length - 1]);
		return sb.toString();
	}
}
