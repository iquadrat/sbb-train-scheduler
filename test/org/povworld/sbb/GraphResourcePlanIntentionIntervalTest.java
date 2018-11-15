package org.povworld.sbb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.povworld.collection.mutable.HashMultiMap;
import org.povworld.sbb.ResourcePlan.IntentionInterval;

import com.google.common.truth.Truth;

public class GraphResourcePlanIntentionIntervalTest {
	private final ResourcePlan rp = new ResourcePlan("R", 0 /* release seconds */, 0.0 /* boost */,
			new HashMultiMap<>());
	
	private void assertDensity(double expected, double actual) {
		assertEquals(expected, actual, Util.WEIGHT_EPS);
	}
	
	@Test
	public void testDensityNoSlop() {
		IntentionInterval o = rp.createIntentionInterval("i", 1000, 1000, 1400, 1400, 10, 1.0);
		assertEquals(1.0, o.getMaxDensity(), Util.WEIGHT_EPS);
		assertEquals(1.0, o.getAvgDensity(), Util.WEIGHT_EPS);
	}

	@Test
	public void testDensityWithSlop() {
		IntentionInterval o = rp.createIntentionInterval("i", 1000, 1200, 1100, 1300, 100, 1.0);
		assertDensity(2.0/3, o.getMaxDensity());
		assertDensity(4.0/9, o.getAvgDensity());
	}
	
	@Test
	public void testDensityIncreasedEarliestExit() {
		IntentionInterval o = rp.createIntentionInterval("i", 1000, 1200, 1200, 1300, 100, 1.0);
		assertDensity(1.0, o.getMaxDensity());
		assertDensity(1.0/2, o.getAvgDensity());
	}
	
	@Test
	public void testDensityDecreasedLatestEntry() {
		IntentionInterval o = rp.createIntentionInterval("i", 1000, 1100, 1100, 1300, 100, 1.0);
		assertEquals(1.0, o.getMaxDensity(), Util.WEIGHT_EPS);
		assertEquals(1.0/2, o.getAvgDensity(), Util.WEIGHT_EPS);
	}
	
	@Test
	public void testMinDurationIsMinusTwoD() {
		IntentionInterval o = rp.createIntentionInterval("i", 1000, 1100, 1300, 1400, 100, 1.0);
		assertDensity(1.0, o.getMaxDensity());
		assertDensity(0.75, o.getAvgDensity());
	}
	
	@Test
	public void testMinDurationIsTwoD() {
		IntentionInterval o = rp.createIntentionInterval("i", 1000, 1300, 1100, 1400, 100, 1.0);
		assertDensity(0.5, o.getMaxDensity());
		assertDensity(3.0/8, o.getAvgDensity());
	}
	
	@Test
	public void testDensityChangedBoth() {
		IntentionInterval o = rp.createIntentionInterval("i", 1000, 1100, 1200, 1300, 100, 1.0);
		assertDensity(1.0, o.getMaxDensity());
		assertDensity(2.0/3, o.getAvgDensity());
	}
	
	@Test
	public void testDensityIncreasingMinDuration() {
		IntentionInterval o = rp.createIntentionInterval("i", 1000, 1350, 1050, 1400, 50, 1.0);
		assertEquals(0.25, o.getMaxDensity(), Util.WEIGHT_EPS);
		assertEquals(7.0/32, o.getAvgDensity(), Util.WEIGHT_EPS);
		o = rp.createIntentionInterval("i", 1000, 1300, 1100, 1400, 100, 1.0);
		assertEquals(0.5, o.getMaxDensity(), Util.WEIGHT_EPS);
		assertEquals(0.375, o.getAvgDensity(), Util.WEIGHT_EPS);
		o = rp.createIntentionInterval("i", 1000, 1250, 1150, 1400, 150, 1.0);
		assertEquals(0.75, o.getMaxDensity(), Util.WEIGHT_EPS);
		assertEquals(15.0/32, o.getAvgDensity(), Util.WEIGHT_EPS);
		o = rp.createIntentionInterval("i", 1000, 1200, 1200, 1400, 200, 1.0);
		assertEquals(1.0, o.getMaxDensity(), Util.WEIGHT_EPS);
		assertEquals(0.5, o.getAvgDensity(), Util.WEIGHT_EPS);
	}
	
	@Test
	public void addToDensityMapSparse() {
		IntentionInterval o = rp.createIntentionInterval("i", 100, 130, 110, 140, 10, 1.0);
		DensityMap map = new DensityMap();
		o.addTo(map);
		Truth.assertThat(map.points()).containsExactly(
				100, 0.0,
				110, 0.5,
				130, 0.5,
				140, 0.0);
	}
	
	@Test
	public void addToDensityMapDense() {
		IntentionInterval o = rp.createIntentionInterval("i", 100, 110, 130, 140, 10, 1.0);
		DensityMap map = new DensityMap();
		o.addTo(map);
		Truth.assertThat(map.points()).containsExactly(
				100, 0.0,
				110, 1.0,
				130, 1.0,
				140, 0.0);
	}
	
	@Test
	public void addToDensityMapNoSlop() {
		IntentionInterval o = rp.createIntentionInterval("i", 100, 100, 140, 140, 10, 1.0);
		DensityMap map = new DensityMap();
		o.addTo(map);
		Truth.assertThat(map.points()).containsExactly(
				100, 1.0,
				140, 0.0);
	}
}
