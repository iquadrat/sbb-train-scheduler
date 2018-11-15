package org.povworld.sbb;

import java.time.Duration;

import org.povworld.collection.common.Interval;

public class TimeUtil {
	
	public static int parseTime(String time) {
		if (time.isEmpty()) {
			throw new IllegalArgumentException(time);
		}
		int seconds = 0;
		int start = 0;
		int index;
		while ((index = time.indexOf(':', start)) != -1) {
			seconds = 60 * seconds + Integer.parseInt(time.substring(index - 2, index));
			start = index + 1;
		}
		seconds = 60 * seconds + Integer.parseInt(time.substring(start));
		return seconds;
	}

	public static int parseDuration(String duration) {
		if (duration.isEmpty()) {
			return 0;
		}
		return (int) Duration.parse(duration).getSeconds();
	}

	public static String unparseTime(int nodeTime) {
		int hour = nodeTime / 3600;
		nodeTime = nodeTime % 3600;
		int minute = nodeTime / 60;
		nodeTime = nodeTime % 60;
		return String.format("%02d:%02d:%02d", hour, minute, nodeTime);
	}
	
	public static String printTime(int time) {
		if (Debug.UNPARSE_INTERVAL_TIME) {
			return TimeUtil.unparseTime(time);
		} else {
			return String.valueOf(time);
		}
	}

	public static String printTimeInterval(Interval interval) {
		if (interval == null) {
			return "[]";
		}
		if (Debug.UNPARSE_INTERVAL_TIME) {
			return "[" + TimeUtil.unparseTime(interval.getStart()) + "-" + TimeUtil.unparseTime(interval.getEnd()) + ")";
		} else {
			return interval.toString();
		}
	}

}
