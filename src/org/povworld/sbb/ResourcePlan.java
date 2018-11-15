package org.povworld.sbb;

import static org.povworld.sbb.Debug.SOLVER;

import java.util.Comparator;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.povworld.collection.Collection;
import org.povworld.collection.CollectionUtil;
import org.povworld.collection.List;
import org.povworld.collection.common.Assert;
import org.povworld.collection.common.Interval;
import org.povworld.collection.common.PreConditions;
import org.povworld.collection.immutable.ImmutableCollections;
import org.povworld.collection.immutable.ImmutableHashSet;
import org.povworld.collection.immutable.ImmutableSet;
import org.povworld.collection.mutable.ArrayList;
import org.povworld.collection.mutable.HashMap;
import org.povworld.collection.mutable.HashMultiMap;
import org.povworld.collection.mutable.HashSet;
import org.povworld.collection.persistent.PersistentHashMap;
import org.povworld.collection.persistent.PersistentIntervalMap;
import org.povworld.collection.persistent.PersistentMap;
import org.povworld.sbb.ConflictSchedule.Badness;
import org.povworld.sbb.GraphResourceOccupations.ResourceOccupation;
import org.povworld.sbb.RouteGraph.Edge;

public class ResourcePlan {
    
    public static class Max {
        public final Interval interval;
        public final double density;
        public final int conflictingIntentionsCount;
        
        private final Interval fullInterval;
        private final double startDensity;
        private final double endDensity;
        
        public Max(Interval interval, double density, int conflictingIntentionsCount) {
            this(interval, density, conflictingIntentionsCount, null, Double.NaN, Double.NaN);
        }
        
        private Max(Interval interval, double density, int conflictingIntentionsCount,
                Interval fullInterval,
                double startDensity,
                double endDensity) {
            this.interval = interval;
            this.density = density;
            this.conflictingIntentionsCount = conflictingIntentionsCount;
            this.fullInterval = fullInterval;
            this.startDensity = startDensity;
            this.endDensity = endDensity;
        }
        
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + conflictingIntentionsCount;
            long temp;
            temp = Double.doubleToLongBits(density);
            result = prime * result + (int)(temp ^ (temp >>> 32));
            result = prime * result + ((interval == null) ? 0 : interval.hashCode());
            return result;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (!(obj instanceof Max))
                return false;
            Max other = (Max)obj;
            if (conflictingIntentionsCount != other.conflictingIntentionsCount)
                return false;
            if (Double.doubleToLongBits(density) != Double.doubleToLongBits(other.density))
                return false;
            if (interval == null) {
                if (other.interval != null)
                    return false;
            } else if (!interval.equals(other.interval))
                return false;
            return true;
        }
        
        @Override
        public String toString() {
            return "Max [interval=" + interval + ", density=" + density + ", conflictingIntentionsCount="
                    + conflictingIntentionsCount + "]";
        }
        
    }
    
    public enum ConflictIntervalSelection {
        Interval,
        PointAlwaysMid,
        PointMidOrMaxIfNotBorder,
        PointMidOrMaxIfNotBorderUpdateDensity
    }
    
    IntentionInterval createIntentionInterval(String intention, int entryEarliest, int entryLatest, int exitEarliest,
            int exitLatest, int minDuration, double weight) {
        return new IntentionInterval(intention, entryEarliest, entryLatest, exitEarliest, exitLatest, minDuration, weight);
    }
    
    static class IntentionInterval {
        final String intention;
        final int entryEarliest;
        final int entryLatest;
        final int exitEarliest;
        final int exitLatest;
        final int minDuration;
        final double weight;
        
        private IntentionInterval(int start, int end) {
            this("", start, start, end, end, 0, 0);
        }
        
        private IntentionInterval(String intention, int entryEarliest, int entryLatest, int exitEarliest,
                int exitLatest, int minDuration, double weight) {
            this.intention = intention;
            this.entryEarliest = entryEarliest;
            this.entryLatest = entryLatest;
            this.exitEarliest = exitEarliest;
            this.exitLatest = exitLatest;
            this.minDuration = minDuration;
            this.weight = weight;
            
            if (exitLatest - entryLatest < minDuration) {
                throw new IllegalArgumentException("bad latest");
            }
            if (exitEarliest - entryEarliest < minDuration) {
                throw new IllegalArgumentException("bad earliest");
            }
            if (entryEarliest > entryLatest) {
                throw new IllegalArgumentException(entryEarliest + " > " + entryLatest);
            }
            if (exitEarliest > exitLatest) {
                throw new IllegalArgumentException(entryEarliest + " > " + entryLatest);
            }
        }
        
        public int start() {
            return entryEarliest;
        }
        
        public int end() {
            return exitLatest;
        }
        
        public int getEntryEarliest() {
            return entryEarliest;
        }
        
        public int getEntryLatest() {
            return entryLatest;
        }
        
        public int getExitEarliest() {
            return exitEarliest;
        }
        
        public int getExitLatest() {
            return exitLatest;
        }
        
        public int getMinDuration() {
            return minDuration;
        }
        
        double getMaxDensity() {
            int D = entryLatest - exitEarliest;
            double density;
            switch (Debug.PATH_WEIGHT_DENSITY_FUNCTION) {
                case Identity:
                    density = weight;
                    break;
                case Sqrt:
                    density = Math.sqrt(weight);
                    break;
                default:
                    throw new RuntimeException("Unhandled path weight density function");
            }
            // TODO just 1 * minDuration?
            return density * ((D <= 0) ? 1.0 : 1.0 - 1.0 * Math.max(0, D) / (D + 2 * minDuration));
        }
        
        double getDensityAt(int time) {
            DensityMap dm = new DensityMap();
            addTo(dm);
            return dm.getDensity(time);
        }
        
        double getAvgDensity() {
            int D = entryLatest - exitEarliest;
            double maxDensity = getMaxDensity();
            return maxDensity * 0.5 * (exitLatest - entryEarliest + Math.abs(D)) / (exitLatest - entryEarliest);
        }
        
        public void addTo(DensityMap densityMap) {
            int rampUpEnd = Math.min(exitEarliest, entryLatest);
            int rampDownStart = Math.max(exitEarliest, entryLatest);
            densityMap.addTrapezoid(intention, new Interval(entryEarliest, rampUpEnd), new Interval(rampDownStart, exitLatest), getMaxDensity());
        }
        
        public boolean isVisitedOnAllPaths() {
            return Util.weightEquals(weight, 1.0);
        }
        
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + entryEarliest;
            result = prime * result + entryLatest;
            result = prime * result + exitEarliest;
            result = prime * result + exitLatest;
            result = prime * result + ((intention == null) ? 0 : intention.hashCode());
            result = prime * result + minDuration;
            long temp;
            temp = Double.doubleToLongBits(weight);
            result = prime * result + (int)(temp ^ (temp >>> 32));
            return result;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (!(obj instanceof IntentionInterval))
                return false;
            IntentionInterval other = (IntentionInterval)obj;
            if (entryEarliest != other.entryEarliest)
                return false;
            if (entryLatest != other.entryLatest)
                return false;
            if (exitEarliest != other.exitEarliest)
                return false;
            if (exitLatest != other.exitLatest)
                return false;
            if (intention == null) {
                if (other.intention != null)
                    return false;
            } else if (!intention.equals(other.intention))
                return false;
            if (minDuration != other.minDuration)
                return false;
            if (Double.doubleToLongBits(weight) != Double.doubleToLongBits(other.weight))
                return false;
            return true;
        }
        
        @Override
        public String toString() {
            return intention + ": " +
                    TimeUtil.printTimeInterval(new Interval(entryEarliest, exitEarliest)) + "-" +
                    TimeUtil.printTimeInterval(new Interval(entryLatest, exitLatest)) + " / " +
                    weight;
        }
    }
    
    private static final double EPS = Util.WEIGHT_EPS;
    private static final int TMAX = GraphConstraints.TMAX;
    private static final Logger logger = Logger.getLogger(ResourcePlan.class.getSimpleName());
    
    private final String resource;
    private final int releaseSeconds;
    private final double boost;
    private final HashMultiMap<String, ResourceOccupation> occupationsByIntention;
    
    // Need to be saved/restored:
    private PersistentMap<ResourceOccupation, OccupationTimes> timeByOccupation;
    private PersistentMap<String, IntentionInterval> mergedOccupation;
    private PersistentIntervalMap<IntentionInterval> intentOccupations;
    
    // Reset on 'copy' and modifications:
    @CheckForNull
    private Max cachedMax = null;
    private boolean hasCachedMax = false;
    
    public ResourcePlan(String resource, int releaseSeconds, double boost,
            HashMultiMap<String, ResourceOccupation> occupationsByIntention) {
        this(resource, releaseSeconds, boost, occupationsByIntention, PersistentHashMap.empty(),
                PersistentHashMap.empty(), PersistentIntervalMap.empty());
    }
    
    private ResourcePlan(String resource, int releaseSeconds, double boost,
            HashMultiMap<String, ResourceOccupation> occupationsByIntention,
            PersistentMap<ResourceOccupation, OccupationTimes> timeByOccupation,
            PersistentMap<String, IntentionInterval> mergedOccupation,
            PersistentIntervalMap<IntentionInterval> intentOccupations) {
        this.resource = resource;
        this.releaseSeconds = releaseSeconds;
        this.boost = boost;
        this.occupationsByIntention = occupationsByIntention;
        
        this.timeByOccupation = timeByOccupation;
        this.mergedOccupation = mergedOccupation;
        this.intentOccupations = intentOccupations;
    }
    
    public boolean hasConflicts() {
        return getMaxDensityRange(ConflictIntervalSelection.Interval) != null;
    }
    
    public ResourcePlan copy() {
        return new ResourcePlan(resource, releaseSeconds, boost, occupationsByIntention,
                timeByOccupation, mergedOccupation, intentOccupations);
    }
    
    public void set(ResourceOccupation occupation, OccupationTimes times, IntentionRestrictionQueue queue) {
        // TODO we could check if the old 'times' equals to the new times
        timeByOccupation = timeByOccupation.with(occupation, times);
        clearCache();
        if (updateMergedOccupation(occupation.intention)) {
            scan(occupation.intention, queue);
        }
    }
    
    public void remove(ResourceOccupation occupation, IntentionRestrictionQueue queue) {
        timeByOccupation = timeByOccupation.without(occupation);
        clearCache();
        if (updateMergedOccupation(occupation.intention)) {
            scan(occupation.intention, queue);
        }
    }
    
    private boolean updateMergedOccupation(String intention) {
        int entryEarliest = TMAX;
        int entryLatest = 0;
        int exitEarliest = TMAX;
        int exitLatest = 0;
        double weight = 0;
        int minDuration = TMAX;
        //logger.log(Level.INFO, "Merged: "+ occupationsByIntention.get(intention));
        for (ResourceOccupation o: occupationsByIntention.get(intention)) {
            OccupationTimes times = timeByOccupation.get(o);
            if (times == null) {
                continue;
            }
            //logger.log(Level.INFO, times.toString());
            entryEarliest = Math.min(entryEarliest, times.entryEarliest);
            entryLatest = Math.max(entryLatest, times.entryLatest);
            exitEarliest = Math.min(exitEarliest, times.exitEarliest);
            exitLatest = Math.max(exitLatest, times.exitLatest);
            minDuration = Math.min(minDuration, o.minDuration);
            weight += times.weight;
        }
        
        if (weight == 0) {
            IntentionInterval old = mergedOccupation.get(intention);
            mergedOccupation = mergedOccupation.without(intention);
            if (old != null) {
                intentOccupations = intentOccupations.without(new Interval(old.start(), old.end()), old);
                // TODO return false?
            }
            return true;
        }
        
        //logger.log(Level.INFO, " -> "+entryEarliest+","+entryLatest+","+exitEarliest+","+exitLatest);
//		if (weight > 1.0 + EPS) {
//			logger.log(Level.SEVERE, "Excessive weight: " + weight + " for " + intention + " in " + occupationsByIntention.get(intention));
//			throw new IllegalStateException();
//		}
        
        // Correct by release time:
        exitEarliest += releaseSeconds;
        exitLatest += releaseSeconds;
        minDuration += releaseSeconds;
        
        IntentionInterval old = mergedOccupation.get(intention);
        if (old != null) {
            if (old.entryEarliest == entryEarliest && old.entryLatest == entryLatest && old.exitEarliest == exitEarliest
                    && old.exitLatest == exitLatest && weight == old.weight) {
                return false;
            }
            intentOccupations = intentOccupations.without(new Interval(old.start(), old.end()), old);
        }
        
        IntentionInterval ii = new IntentionInterval(intention, entryEarliest, entryLatest, exitEarliest, exitLatest, minDuration, weight);
        mergedOccupation = mergedOccupation.with(intention, ii);
        intentOccupations = intentOccupations.with(new Interval(ii.start(), ii.end()), ii);
        return true;
    }
    
    private void scan(String updatedIntention, IntentionRestrictionQueue queue) {
        final IntentionInterval occupation = mergedOccupation.get(updatedIntention);
        if (occupation == null) {
            return;
        }
        
        Iterable<IntentionInterval> overlappers =
                intentOccupations.getOverlappers(new Interval(occupation.start(), occupation.end()));
        
        // Scan for unconditionally visited occupations conflicting with the updated one.
        for (IntentionInterval conflict: overlappers) {
            if (!conflict.isVisitedOnAllPaths()) {
                continue;
            }
            checkForAdditionalRestrictions(occupation, conflict, queue);
        }
        
        if (occupation.isVisitedOnAllPaths()) {
            // Scan overlappers for impossible scheduling conditions.
            for (IntentionInterval conflict: overlappers) {
                checkForAdditionalRestrictions(conflict, occupation, queue);
            }
        }
    }
    
    @CheckForNull
    private void checkForAdditionalRestrictions(
            IntentionInterval occupations, IntentionInterval conflict, IntentionRestrictionQueue queue) {
        if (occupations == conflict) {
            return;
        }
        Interval constraint = getConstraintInterval(occupations, conflict);
        if (constraint == null) {
            queue.add(createMarkInfeasibleRestriction(occupations.intention,
                    occupationsByIntention.get(occupations.intention)));
        } else {
            if (constraint.getStart() != occupations.getEntryEarliest()) {
                // TODO only those overlapping with start?
                queue.add(createIncreaseMinTimeRestriction(occupations.intention, constraint,
                        getOverlappingResourceOccupations(occupations.intention, constraint)));
            }
            if (constraint.getEnd() != occupations.getExitLatest()) {
                // TODO only those overlapping with end?				
                queue.add(createDecreaseMaxTimeRestriction(occupations.intention, constraint,
                        getOverlappingResourceOccupations(occupations.intention, constraint)));
            }
        }
    }
    
    /** 
     * Calculates the interval in which {@code occupations} can be scheduled given {@code conflict}. 
     * 
     * @param occupations the {@code IntentionInterval} to test
     * @param conflict the conflicting occupation that is visited unconditionally
     * @return the constraint interval or the occupation's interval or null if it is infeasible
     */
    @CheckForNull
    private Interval getConstraintInterval(IntentionInterval occupations, IntentionInterval conflict) {
        PreConditions.paramCheck(conflict, "Is must be visited unconditionally", conflict.isVisitedOnAllPaths());
        int entryEarliest = occupations.getEntryEarliest();
        int exitLatest = occupations.getExitLatest();
        if (occupations.getExitEarliest() > conflict.getEntryLatest()) {
            // conflict cannot be scheduled before occupations
            entryEarliest = Math.max(entryEarliest, conflict.getExitEarliest());
        }
        
        if (occupations.getEntryLatest() < conflict.getExitEarliest()) {
            // conflict cannot be scheduled after occupations
            exitLatest = Math.min(exitLatest, conflict.getEntryLatest());
        }
        if (exitLatest - entryEarliest < occupations.getMinDuration()) {
            return null;
        }
        return new Interval(entryEarliest, exitLatest);
    }
    
    Map<Integer, Double> getDensities() {
        return getDensityMap().points();
    }
    
    public DensityMap getDensityMap() {
        DensityMap densities = new DensityMap();
        for (IntentionInterval occupation: mergedOccupation.values()) {
            assertOccupationWeightOk(occupation);
            occupation.addTo(densities);
        }
        return densities;
    }
    
    private void assertOccupationWeightOk(IntentionInterval occupation) {
        if (!Debug.ENABLE_RESOURCE_PLAN_CONSITENCY_CHECKS) {
            return;
        }
        if (occupation.weight > 1.0 + EPS) {
            throw new IllegalStateException("Excessive weight of " + occupation);
        }
    }
    
    @CheckForNull
    public Max getMaxDensityRange(ConflictIntervalSelection intervalSelection) {
        //hasCachedMax = false;
        if (!hasCachedMax) {
            cachedMax = calculateMax();
            hasCachedMax = true;
        }
        if (cachedMax == null) {
            return null;
        }
        return selectPoint(cachedMax, intervalSelection);
    }
    
    private Max selectPoint(Max max, ConflictIntervalSelection intervalSelection) {
        Interval maxRange = max.interval;
        final int midPoint = (maxRange.getStart() + maxRange.getEnd()) / 2;
        double density = max.density;
        
        switch (intervalSelection) {
            case Interval:
                // Nothing to do, we already have the interval.
                break;
            case PointAlwaysMid:
                maxRange = new Interval(midPoint, midPoint);
                break;
            case PointMidOrMaxIfNotBorder: {
                int point = max.startDensity > max.endDensity ? maxRange.getStart() : maxRange.getEnd();
                if ((!Util.weightEquals(max.startDensity, max.density) ||
                        !Util.weightEquals(max.endDensity, max.density))
                        && point > max.fullInterval.getStart() && point < max.fullInterval.getEnd()) {
                    maxRange = new Interval(point, point);
                } else {
                    maxRange = new Interval(midPoint, midPoint);
                }
                break;
            }
            case PointMidOrMaxIfNotBorderUpdateDensity: {
                int point = max.startDensity > max.endDensity ? maxRange.getStart() : maxRange.getEnd();
                if ((!Util.weightEquals(max.startDensity, max.density) ||
                        !Util.weightEquals(max.endDensity, max.density))
                        && point > max.fullInterval.getStart() && point < max.fullInterval.getEnd()) {
                    maxRange = new Interval(point, point);
                } else {
                    maxRange = new Interval(midPoint, midPoint);
                    density = (max.startDensity + max.endDensity) / 2;
                }
                break;
            }
            default:
                throw new RuntimeException("Unhandled enum value " + intervalSelection);
        }
        return new Max(maxRange, density, max.conflictingIntentionsCount);
    }
    
    private void clearCache() {
        cachedMax = null;
        hasCachedMax = false;
    }
    
    @CheckForNull
    private Max calculateMax() {
        DensityMap densities = getDensityMap();
        
        DensityMap.Max max = densities.getMaxDensityWithInterval(2);
        if (max.interval == null) {
            return null;
        }
        
        Collection<IntentionInterval> overlappers = intentOccupations.getOverlappers(max.interval);
        int activeAtMax = overlappers.size();
        Interval intersection = new Interval(Integer.MIN_VALUE, Integer.MAX_VALUE);
        for (IntentionInterval interval: overlappers) {
            intersection = Interval.intersect(intersection, new Interval(interval.start(), interval.end()));
        }
        
        double startDensity = densities.getDensity(max.interval.getStart());
        double endDensity = densities.getDensity(max.interval.getEnd());
        
        Assert.assertTrue(max.interval.getStart() >= intersection.getStart()
                && max.interval.getEnd() <= intersection.getEnd(), "Intersection is smaller than max range!");
        return new Max(max.interval, max.density * boost, activeAtMax, intersection, startDensity, endDensity);
    }
    
    public String getResource() {
        return resource;
    }
    
    public List<ConflictSchedule> createConflictSchedules(Interval range) {
        PreConditions.paramCheck(range, "Must have length 0!", range.length() == 0);
        Collection<IntentionInterval> conflicts =
                intentOccupations.getOverlappers(new Interval(range.getStart(), range.getEnd() + 1));
        if (conflicts.size() < 2) {
            // This should only happen if we use a 'priority' conflict which is actually not a conflict at all on this branch!
            return ImmutableCollections.listOf(new ConflictSchedule(ImmutableCollections.listOf(), 0));
        }
        
        ArrayList<IntentionInterval> sortedConflicts = CollectionUtil.sort(conflicts, new Comparator<IntentionInterval>() {
            @Override
            public int compare(IntentionInterval c1, IntentionInterval c2) {
                double d1 = c1.getAvgDensity();
                double d2 = c2.getAvgDensity();
//				int mid = (range.getStart() + range.getEnd()) / 2;
//				double d1 = c1.getDensityAt(mid);
//				double d2 = c2.getDensityAt(mid);
                if (Util.weightEquals(d1, d2)) {
                    return c1.intention.compareTo(c2.intention);
                }
                return -Double.compare(d1, d2);
            }
        });
        
        logger.log(SOLVER, "Conflicts for resource " + getResource() + " in " + range);
        for (IntentionInterval ii: sortedConflicts) {
            logger.log(SOLVER, ii.toString() + " " + Util.printDensity(ii.getAvgDensity()) + " / " + Util.printDensity(ii.getMaxDensity()));
        }
        
        ArrayList<ConflictSchedule> conflictSchedules =
                buildConflictSchedulesForPoint(sortedConflicts.get(0), sortedConflicts.get(1), range.getStart());
        return score(sortedConflicts, conflictSchedules);
    }
    
    private ArrayList<ConflictSchedule> score(List<IntentionInterval> conflicts, List<ConflictSchedule> conflictSchedules) {
        ArrayList<ConflictSchedule> rescoredSchedules = new ArrayList<>(conflictSchedules.size());
        
        for (ConflictSchedule schedule: conflictSchedules) {
            HashMap<String, IntentionInterval> merged = new HashMap<>();
            for (IntentionInterval conflict: conflicts) {
                IntentionInterval o = mergedOccupation.get(conflict.intention);
                merged.put(conflict.intention, o);
            }
            
            double infeasibleWeight = 0;
            for (IntentionRestriction restriction: schedule.restrictions) {
                IntentionInterval ii = merged.get(restriction.intention);
                IntentionInterval restricted = restrictedIntentionInterval(ii, restriction.getRestrictionInterval());
                if (restricted == null) {
                    infeasibleWeight += ii.weight;
                } else {
                    merged.put(restriction.intention, restricted);
                }
            }
            
            double maxAvgDenisty = 0;
            for (IntentionInterval ii: merged.values()) {
                maxAvgDenisty = Math.max(maxAvgDenisty, ii.getAvgDensity());
            }
            
            Badness newBadness = new Badness();
            switch (Debug.BADNESS_METRIC) {
                case ConflictMaxAvgDensity:
                    newBadness = newBadness.push(maxAvgDenisty + Debug.REMOVED_PATH_BADNESS_FACTOR * infeasibleWeight);
                    break;
                case Random:
                    newBadness = new Badness(Math.random());
                    break;
                default:
                    throw new IllegalStateException();
            }
            
            rescoredSchedules.push(new ConflictSchedule(schedule.restrictions, newBadness));
        }
        return rescoredSchedules;
    }
    
    @CheckForNull
    public IntentionInterval restrictedIntentionInterval(IntentionInterval intentionInterval, @CheckForNull Interval restriction) {
        if (restriction == null) {
            return null;
        }
        
        int entryEarliest = Math.max(intentionInterval.entryEarliest, restriction.getStart());
        int exitEarliest = Math.max(intentionInterval.exitEarliest, entryEarliest + intentionInterval.minDuration);
        
        int exitLatest = Math.min(intentionInterval.exitLatest, restriction.getEnd() + releaseSeconds);
        int entryLatest = Math.min(intentionInterval.entryLatest, restriction.getEnd() + releaseSeconds - intentionInterval.minDuration);
        
        if (entryLatest < entryEarliest || exitLatest < exitEarliest) {
            return null;
        }
        return new IntentionInterval(intentionInterval.intention, entryEarliest, entryLatest, exitEarliest, exitLatest,
                intentionInterval.minDuration, intentionInterval.weight);
    }
    
    private ArrayList<ConflictSchedule> buildConflictSchedulesForPoint(
            IntentionInterval primary,
            IntentionInterval secondary,
            int point) {
        ArrayList<ConflictSchedule> result = new ArrayList<ConflictSchedule>();
        
        // Case 1: No restriction on primary. Primary uses point.
        Assert.assertTrue(primary.start() <= point && primary.end() > point, "Primary does not overlap point!");
        {
            // Case 1a: Secondary after primary
            int earliestEntryAfter = Math.max(primary.exitEarliest, point);
            if (earliestEntryAfter == secondary.entryEarliest) {
                // This should only happen if the conflict interval is a single second!
                earliestEntryAfter++;
            }
            if (secondary.entryLatest >= earliestEntryAfter) {
                Interval restriction = new Interval(earliestEntryAfter, secondary.exitLatest);
                result.push(new ConflictSchedule(
                        ImmutableCollections.listOf(createIncreaseMinTimeRestriction(secondary.intention, restriction,
                                getOverlappingResourceOccupations(secondary.intention, restriction)))));
            }
            
            // Case 1b: Secondary before primary
            int latestExitBefore = Math.min(primary.entryLatest, point);
            if (secondary.exitEarliest <= latestExitBefore) {
                Interval restriction = new Interval(secondary.entryEarliest, latestExitBefore);
                result.push(new ConflictSchedule(
                        ImmutableCollections.listOf(createDecreaseMaxTimeRestriction(secondary.intention, restriction,
                                getOverlappingResourceOccupations(secondary.intention, restriction)))));
            }
        }
        
        // Case 2: No restriction on secondary. Secondary uses point
        Assert.assertTrue(secondary.start() <= point && secondary.end() > point, "Secondary does not overlap point!");
        {
            // Case 1a: Primary after secondary
            int earliestEntryAfter = Math.max(secondary.exitEarliest, point);
            if (earliestEntryAfter == primary.entryEarliest) {
                // This should only happen if the conflict interval is a single second!
                earliestEntryAfter++;
            }
            if (primary.entryLatest >= earliestEntryAfter) {
                Interval restriction = new Interval(earliestEntryAfter, primary.exitLatest);
                result.push(new ConflictSchedule(
                        ImmutableCollections.listOf(createIncreaseMinTimeRestriction(primary.intention, restriction,
                                getOverlappingResourceOccupations(primary.intention, restriction)))));
            }
            
            // Case 1b: Primary before primary
            int latestExitBefore = Math.min(secondary.entryLatest, point);
            if (primary.exitEarliest <= latestExitBefore) {
                Interval restriction = new Interval(primary.entryEarliest, latestExitBefore);
                result.push(new ConflictSchedule(
                        ImmutableCollections.listOf(createDecreaseMaxTimeRestriction(primary.intention, restriction,
                                getOverlappingResourceOccupations(primary.intention, restriction)))));
            }
        }
        
        // Case 4: Drop primary.
        if (!primary.isVisitedOnAllPaths()) {
            result.push(new ConflictSchedule(ImmutableCollections.listOf(createMarkInfeasibleRestriction(primary.intention,
                    occupationsByIntention.get(primary.intention)))));
        }
        
        // Case 5: Drop secondary.
        if (!secondary.isVisitedOnAllPaths()) {
            result.push(new ConflictSchedule(ImmutableCollections.listOf(createMarkInfeasibleRestriction(secondary.intention,
                    occupationsByIntention.get(secondary.intention)))));
        }
        
        return result;
    }
    
    private IntentionRestriction createIncreaseMinTimeRestriction(String intention, Interval restriction,
            Collection<ResourceOccupation> affected) {
        HashSet<Edge> edges = new HashSet<>(affected.size());
        for (ResourceOccupation o: affected) {
            edges.add(o.getStart());
        }
        return new IntentionRestriction.IncreaseMinTimeRestriction(intention, restriction.getStart(), edges);
    }
    
    private IntentionRestriction createDecreaseMaxTimeRestriction(String intention, Interval restriction,
            Collection<ResourceOccupation> affected) {
        HashSet<Edge> edges = new HashSet<>(affected.size());
        for (ResourceOccupation o: affected) {
            edges.add(o.getEnd());
        }
        return new IntentionRestriction.DecreaseMaxTimeRestriction(intention, restriction.getEnd() - releaseSeconds, edges);
    }
    
    private IntentionRestriction createMarkInfeasibleRestriction(String intention, Collection<ResourceOccupation> affected) {
        HashSet<Edge> edges = new HashSet<>();
        for (ResourceOccupation o: affected) {
            for (int i = o.getFlowStartIndex(); i <= o.getFlowEndIndex(); ++i) {
                edges.add(o.flow.get(i));
            }
        }
        return new IntentionRestriction.MarkPathInfeasibleRestriction(intention, edges);
    }
    
    private ImmutableSet<ResourceOccupation> getOverlappingResourceOccupations(String intention,
            Interval interval) {
        ImmutableHashSet.Builder<ResourceOccupation> builder = ImmutableHashSet.newBuilder();
        for (ResourceOccupation o: occupationsByIntention.get(intention)) {
            OccupationTimes time = timeByOccupation.get(o);
            if (time != null && time.isRestrictedBy(new Interval(interval.getStart(), interval.getEnd() - releaseSeconds))) {
                builder.add(o);
            }
        }
        return builder.build();
    }
    
    public void printOccupations() {
        System.out.println("Occupations for " + resource);
        ArrayList<IntentionInterval> sorted = CollectionUtil.sort(mergedOccupation.values(), new Comparator<IntentionInterval>() {
            @Override
            public int compare(IntentionInterval o1, IntentionInterval o2) {
                return Integer.compare(o1.entryEarliest, o2.entryEarliest);
            }
        });
        
        for (IntentionInterval o: sorted) {
            System.out.println(o.toString());
        }
        System.out.println();
    }
    
    public void check(ResourceOccupation o, OccupationTimes expectedTimes) {
        OccupationTimes actualTimes = timeByOccupation.get(o);
        Assert.assertEquals(expectedTimes, actualTimes);
    }
    
}
