package org.povworld.sbb;

import java.util.Map;
import java.util.TreeMap;

import javax.annotation.CheckForNull;

import org.povworld.collection.EntryIterator;
import org.povworld.collection.common.Interval;
import org.povworld.collection.mutable.IntervalMap;
import org.povworld.collection.mutable.TreeMultiMap;

public class DensityMap {
    
    private static final double EPS = 1e-8;
    
    private static class Range implements Comparable<Range> {
        final String id;
        final int start;
        final int end;
        final double slope;
        final double endDensity;
        
        public Range(String id, int start, int end, double slope, double endDensity) {
            this.id = id;
            this.start = start;
            this.end = end;
            this.slope = slope;
            this.endDensity = endDensity;
        }
        
        public double densityAt(int t) {
            return endDensity - slope * (end - t);
        }
        
        @Override
        public String toString() {
            return endDensity + "-" + slope + "*(" + end + "-t)";
        }
        
        @Override
        public int compareTo(Range o) {
            if (start == o.start) {
                return id.compareTo(o.id);
            }
            return Integer.compare(start, o.start);
        }
        
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + id.hashCode();
            result = prime * result + start;
            return result;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (!(obj instanceof Range))
                return false;
            Range other = (Range)obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            if (start != other.start)
                return false;
            return true;
        }
    }
    
    /** Maps interval by start to their density. */
    private IntervalMap<Range> densities = IntervalMap.create(Range.class);
    
    public void addTrapezoid(String id, Interval rampup, Interval rampdown, double density) {
        add(id, rampup.getStart(), rampup.getEnd(), 0.0, density);
        if (rampup.getEnd() != rampdown.getStart()) {
            add(id, rampup.getEnd(), rampdown.getStart(), density, density);
        }
        add(id, rampdown.getStart(), rampdown.getEnd(), density, 0.0);
    }
    
    public void removeTrapezoid(String id, Interval rampup, Interval rampdown, double density) {
        remove(id, rampup.getStart(), rampup.getEnd(), 0.0, density);
        if (rampup.getEnd() != rampdown.getStart()) {
            remove(id, rampup.getEnd(), rampdown.getStart(), density, density);
        }
        remove(id, rampdown.getStart(), rampdown.getEnd(), density, 0.0);
    }
    
    public void add(String id, Interval interval, double density) {
        add(id, interval.getStart(), interval.getEnd(), density, density);
    }
    
    public void add(String id, int start, int end, double startDensity, double endDensity) {
        int length = end - start;
        double slope = (endDensity - startDensity) / length;
        densities.add(new Interval(start, end), new Range(id, start, end, slope, endDensity));
        assertRangeConsistency();
    }
    
    public void remove(String id, Interval interval, double density) {
        remove(id, interval.getStart(), interval.getEnd(), density, density);
    }
    
    public void remove(String id, int start, int end, double startDensity, double endDensity) {
        int length = end - start;
        double slope = (endDensity - startDensity) / length;
        densities.remove(new Interval(start, end), new Range(id, start, end, slope, endDensity));
    }
    
    public boolean isEmpty() {
        return densities.isEmpty();
    }
    
    public double getMaxValue() {
        return calculateMax(1).density;
    }
    
    private double getDensity(Iterable<Range> ranges, int time) {
        double density = 0;
        for (Range r: ranges) {
            density += r.densityAt(time);
        }
        return density;
    }
    
    private double getAbsSlope(Iterable<Range> ranges) {
        double slope = 0;
        for (Range r: ranges) {
            slope += r.slope;
        }
        return Math.abs(slope);
    }
    
    public Max getMaxDensityWithInterval(int minOverlappers) {
        return calculateMax(minOverlappers);
    }
    
    @CheckForNull
    public Interval getMax() {
        return getMax(1);
    }
    
    @CheckForNull
    public Interval getMax(int minOverlappers) {
        return calculateMax(minOverlappers).interval;
    }
    
    public static class Max {
        Max(Interval interval, double density) {
            this.interval = interval;
            this.density = density;
        }
        final Interval interval;
        final double density;
    }
    
    private Max calculateMax(int minOverlappers) {
        Interval maxInterval = null;
        double max = 0;
        double maxSlope = 0;
        
        int now = Integer.MIN_VALUE;
        TreeMultiMap<Integer, Range> activeRanges = TreeMultiMap.create(Integer.class, Range.class);
        
        EntryIterator<Interval, Range> it = densities.entryIterator();
        while (it.next()) {
            Range range = it.getCurrentValue();
            int next = range.start;
            
            int firstEnd;
            while (!activeRanges.isEmpty() && (firstEnd = activeRanges.getFirstKeyOrNull()) <= next) {
                if (activeRanges.valueCount() >= minOverlappers) {
                    double startDensity = getDensity(activeRanges.flatValues(), now);
                    double endDensity = getDensity(activeRanges.flatValues(), firstEnd);
                    double maxStartEnd = Math.max(startDensity, endDensity);
                    if ((maxStartEnd > max + EPS) || (maxStartEnd > max - EPS && getAbsSlope(activeRanges.flatValues()) < maxSlope)) {
                        max = maxStartEnd;
                        maxInterval = new Interval(now, firstEnd);
                        maxSlope = getAbsSlope(activeRanges.flatValues());
                    }
                }
                activeRanges.remove(firstEnd);
                now = firstEnd;
            }
            
            if (now < next) {
                if (activeRanges.valueCount() >= minOverlappers) {
                    double startDensity = getDensity(activeRanges.flatValues(), now);
                    double endDensity = getDensity(activeRanges.flatValues(), next);
                    double maxStartEnd = Math.max(startDensity, endDensity);
                    if ((maxStartEnd > max + EPS) || (maxStartEnd > max - EPS && getAbsSlope(activeRanges.flatValues()) < maxSlope)) {
                        max = maxStartEnd;
                        maxInterval = new Interval(now, next);
                        maxSlope = getAbsSlope(activeRanges.flatValues());
                    }
                }
                now = next;
            }
            activeRanges.put(range.end, range);
        }
        
        while (activeRanges.keyCount() >= minOverlappers) {
            int end = activeRanges.getFirstKeyOrNull();
            double startDensity = getDensity(activeRanges.flatValues(), now);
            double endDensity = getDensity(activeRanges.flatValues(), end);
            double maxStartEnd = Math.max(startDensity, endDensity);
            if ((maxStartEnd > max + EPS) || (maxStartEnd > max - EPS && getAbsSlope(activeRanges.flatValues()) < maxSlope)) {
                max = maxStartEnd;
                maxInterval = new Interval(now, end);
                maxSlope = getAbsSlope(activeRanges.flatValues());
            }
            now = end;
            // TODO process all ranges with same end in inner loop
            activeRanges.remove(end, activeRanges.get(end).getFirst());
        }
        
        return new Max(maxInterval, max);
    }
    
    public Map<Integer, Double> points() {
        Map<Integer, Double> result = new TreeMap<>();
        if (isEmpty()) {
            return result;
        }
        
        EntryIterator<Interval, Range> it = densities.entryIterator();
        while (it.next()) {
            int start = it.getCurrentKey().getStart();
            int end = it.getCurrentKey().getEnd();
            result.put(start, getDensity(start));
            result.put(end, getDensity(end));
        }
        return result;
    }
    
    @CheckForNull
    public double getDensity(int time) {
        double density = 0;
        for (Range range: densities.getOverlappers(time)) {
            density += range.densityAt(time);
        }
        return density;
    }
    
//	public double integrate(int start, int end) {
//		double density = 0;
//		for(Entry<Range<Integer>, Double> e: densities.subRangeMap(Range.closed(start, end)).asMapOfRanges().entrySet()) {
//			Range<Integer> range = e.getKey();
//			density += (range.upperEndpoint() - range.lowerEndpoint() + 1) * e.getValue();
//		}
//		return density;
//	}
    
    private void assertRangeConsistency() {
        if (!Debug.ENABLE_DENSITY_MAP_CONSISTENCY_CHECKS) {
            return;
        }
//		int lastEnd = Integer.MIN_VALUE;
//		for (Entry<Integer, Range> e : densities.entrySet()) {
//			Integer start = e.getKey();
//			int end = e.getValue().end - 1;
//			if (start > end) {
//				throw new AssertionError(start + " > " + e.getValue());
//			}
//			if (start < lastEnd) {
//				throw new AssertionError(start + " < " + lastEnd);
//			}
//			lastEnd = end;
//		}
    }
    
    @Override
    public String toString() {
        return densities.toString();
    }
    
}
