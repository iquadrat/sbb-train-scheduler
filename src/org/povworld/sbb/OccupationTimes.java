package org.povworld.sbb;

import org.povworld.collection.common.Interval;

public final class OccupationTimes {
	final int entryEarliest;
	final int exitEarliest;
	final int entryLatest;
	final int exitLatest;
	final double weight;

	public OccupationTimes(int entryEarliest, int exitEarliest, int entryLatest, int exitLatest, double weight) {
		this.entryEarliest = entryEarliest;
		this.exitEarliest = exitEarliest;
		this.entryLatest = entryLatest;
		this.exitLatest = exitLatest;
		this.weight = weight;
	}

	public boolean isRestrictedBy(Interval interval) {
		return interval.getStart() > entryEarliest || interval.getEnd() < exitLatest;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + entryEarliest;
		result = prime * result + entryLatest;
		result = prime * result + exitEarliest;
		result = prime * result + exitLatest;
		long temp;
		temp = Double.doubleToLongBits(weight);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof OccupationTimes))
			return false;
		OccupationTimes other = (OccupationTimes) obj;
		if (entryEarliest != other.entryEarliest)
			return false;
		if (entryLatest != other.entryLatest)
			return false;
		if (exitEarliest != other.exitEarliest)
			return false;
		if (exitLatest != other.exitLatest)
			return false;
		if (Double.doubleToLongBits(weight) != Double.doubleToLongBits(other.weight))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return TimeUtil.printTimeInterval(new Interval(entryEarliest, exitEarliest)) + " - "
				+ TimeUtil.printTimeInterval(new Interval(entryLatest, exitLatest));
	}
}