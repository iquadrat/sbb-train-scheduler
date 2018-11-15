package org.povworld.sbb;

import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.povworld.collection.common.Interval;

public class ResourceAllocator {

	private static final Logger logger = Logger.getLogger(ResourceAllocator.class.getSimpleName());

	private static class PathScheduleInterval extends Interval {
		final @CheckForNull PathSchedule schedule;
		final @CheckForNull String route;

		PathScheduleInterval(Interval interval, @CheckForNull PathSchedule schedule, @CheckForNull String route) {
			super(interval.getStart(), interval.getEnd());
			this.schedule = schedule;
			this.route = route;
		}
	}

	private final String resource;
	private final int releaseTime;

	private final TreeMap<Integer, PathScheduleInterval> occupations = new TreeMap<>();

	public ResourceAllocator(String resource, int releaseTimeSeconds) {
		this.resource = resource;
		this.releaseTime = releaseTimeSeconds;
		occupations.put(Integer.MIN_VALUE,
				new PathScheduleInterval(new Interval(Integer.MIN_VALUE, -releaseTime), null, null));
		occupations.put(GraphConstraints.TMAX + releaseTime, new PathScheduleInterval(
				new Interval(GraphConstraints.TMAX + releaseTime, Integer.MAX_VALUE), null, null));
	}

	public boolean occupy(Interval interval) {
		return occupy(interval, null, null);
	}

	public boolean occupy(Interval interval, @CheckForNull String route, @CheckForNull PathSchedule schedule) {
		Entry<Integer, PathScheduleInterval> floor = occupations.floorEntry(interval.getStart());
		Entry<Integer, PathScheduleInterval> ceil = occupations.ceilingEntry(interval.getStart());
		if (floor.getValue().getEnd() + releaseTime > interval.getStart()) {
			logger.log(Level.SEVERE,
					"The resource " + resource + " is already occupied at interval " + TimeUtil.printTimeInterval(interval) + " by route "
							+ floor.getValue().route + " [" + floor.getValue().schedule + "] during " + TimeUtil.printTimeInterval(floor.getValue())
							+ " (release time is " + releaseTime + ")");
			return false;
		}
		if (interval.getEnd() + releaseTime > ceil.getValue().getStart()) {
			logger.log(Level.SEVERE,
					"The resource " + resource + " is already occupied at interval " + TimeUtil.printTimeInterval(interval) + " by route"
							+ ceil.getValue().route + " [" + ceil.getValue().schedule + "] during " + TimeUtil.printTimeInterval(ceil.getValue())
							+ " (release time is " + releaseTime + ")");
			return false;
		}
		occupations.put(interval.getStart(), new PathScheduleInterval(interval, schedule, route));
		return true;
	}
}
