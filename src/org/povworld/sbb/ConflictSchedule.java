package org.povworld.sbb;

import java.util.Arrays;

import org.povworld.collection.common.ArrayUtil;
import org.povworld.collection.common.PreConditions;
import org.povworld.collection.immutable.ImmutableList;

public final class ConflictSchedule {
	
	public static final double BADNESS_EPS = 1e-6;
	
	public static final class Badness implements Comparable<Badness> {
		private final double[] values;
		
		public Badness(double value) {
			values = new double[] { value };
		}
		
		public Badness(double... values) {
			this.values = values;
		}
		
		public Badness push(double value) {
			return new Badness(ArrayUtil.prependArrayElement(values, value));
		}
		
		public boolean isFinite() {
			for(double v: values) {
				if (!Double.isFinite(v)) {
					return false;
				}
			}
			return true;
		}
		
		@Override
		public int compareTo(Badness o) {
			PreConditions.paramCheck(o, "Different length of badness array!", o.values.length == values.length);
			int i = 0;
			while (Math.abs(values[i] - o.values[i]) < BADNESS_EPS) {
				i++;
				if (i == values.length) {
					return 0;
				}
			}
			return values[i] < o.values[i] ? -1 : 1;
		}
		
		@Override
		public String toString() {
			return Arrays.toString(values);
		}
	}
	
	public final ImmutableList<IntentionRestriction> restrictions;
	public final Badness badness;
	
	public ConflictSchedule(ImmutableList<IntentionRestriction> restrictions) {
		this(restrictions, new Badness(new double[] {}));
	}
	
	public ConflictSchedule(ImmutableList<IntentionRestriction> restrictions, double badness) {
		this(restrictions, new Badness(badness));
	}

	public ConflictSchedule(ImmutableList<IntentionRestriction> restrictions, Badness badness) {
		this.restrictions = restrictions;
		this.badness = badness;
	}
	
	public ConflictSchedule withBadness(double badness) {
		return new ConflictSchedule(restrictions, this.badness.push(badness));
	}
	
	@Override
	public String toString() {
		return restrictions.toString() + " (" + badness + ")";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((restrictions == null) ? 0 : restrictions.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConflictSchedule other = (ConflictSchedule) obj;
		if (restrictions == null) {
			if (other.restrictions != null)
				return false;
		} else if (!restrictions.equals(other.restrictions))
			return false;
		return true;
	}

}