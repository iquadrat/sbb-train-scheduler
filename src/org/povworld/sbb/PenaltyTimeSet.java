package org.povworld.sbb;

import java.util.Arrays;

import javax.annotation.CheckForNull;

import org.povworld.collection.common.ArrayUtil;
import org.povworld.collection.common.Assert;

public final class PenaltyTimeSet {
	private final int maxTimeByDelay[];
	
	private PenaltyTimeSet(int[] maxTimeByDelay) {
		this.maxTimeByDelay = maxTimeByDelay;
	}
	
	public PenaltyTimeSet(int maxTime) {
		this(new int[] { maxTime });
	}
	
	public PenaltyTimeSet with(int maxTime) {
		int pos = 0;
		while(pos < maxTimeByDelay.length && maxTimeByDelay[pos] < maxTime) {
			pos++;
		}
		return new PenaltyTimeSet(ArrayUtil.insertArrayElement(maxTimeByDelay, pos, maxTime));
	}
	
	public PenaltyTimeSet subtract(int timeDelta) {
		int[] times = new int[maxTimeByDelay.length];
		for(int i=0; i<times.length; ++i) {
			times[i] = maxTimeByDelay[i] - timeDelta;
		}
		return new PenaltyTimeSet(times);
	}
	
	public PenaltyTimeSet replaced(int oldTime, int newTime) {
		int pos = 0;
		while(maxTimeByDelay[pos] != oldTime) {
			pos++;
		}
		int[] updated = maxTimeByDelay.clone();
		updated[pos] = newTime;
		// TODO optimize
		Arrays.sort(updated);
		return new PenaltyTimeSet(updated);
	}
	
	public double delay(int time) {
		int delaySeconds = 0;
		for(int i=0; maxTimeByDelay[i] < time; ++i) {
			delaySeconds += time - maxTimeByDelay[i];
		}
		return delaySeconds / 60.0;
	}

	public int maximumTime(double maxPenalty) {
		int result = maxTimeByDelay[0];
		for(int i=1; i<maxTimeByDelay.length; ++i) {
			double delta = (maxTimeByDelay[i] - maxTimeByDelay[i-1]) * i;
			if (delta > maxPenalty) {
				return result + (int)Math.floor(60 * maxPenalty / i);
			}
			maxPenalty -= delta;
		}
		return result + (int)Math.floor(60 * maxPenalty / maxTimeByDelay.length);
	}
	
	public static PenaltyTimeSet min(PenaltyTimeSet timeSet1, @CheckForNull PenaltyTimeSet timeSet2) {
		if (timeSet2 == null) {
			return timeSet1;
		}
		Assert.assertTrue(timeSet1.maxTimeByDelay.length == timeSet2.maxTimeByDelay.length, "Difference in penalty count");
		int[] min = new int[timeSet1.maxTimeByDelay.length];
		for(int i = 0; i< timeSet1.maxTimeByDelay.length; ++i) {
			min[i] = Math.max(timeSet1.maxTimeByDelay[i], timeSet2.maxTimeByDelay[i]);
		}
		return new PenaltyTimeSet(min);
	}
	
	@Override
	public String toString() {
		return Arrays.toString(maxTimeByDelay);
	}

	
}