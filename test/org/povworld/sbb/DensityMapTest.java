package org.povworld.sbb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;
import org.povworld.collection.common.Interval;

public class DensityMapTest {
	
	private static final double EPS = 1e-8;
	private DensityMap map = new DensityMap();

	@Test
	public void emtpy() {
		assertTrue(map.isEmpty());
		assertNull(map.getMax());
		assertEquals(0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void singleConstant() {
		map.add("x", new Interval(100, 200), 2.0);
		assertEquals(2.0, map.getMaxValue(), EPS);
		assertEquals(new Interval(100, 200), map.getMax());
	}
	
	@Test
	public void disjunct() {
		map.add("x", new Interval(100, 200), 1.0);
		map.add("y", new Interval(500, 600), 2.0);
		map.add("z", new Interval(400, 444), 3.0);
		assertEquals(new Interval(400, 444), map.getMax());
		assertEquals(3.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void overlappingBegin() {
		map.add("x", new Interval(100, 200), 1.0);
		map.add("y", new Interval(150, 300), 2.0);
		assertContainsExactly(
				100, 1.0,
				150, 3.0,
				200, 2.0);
		assertEquals(new Interval(150, 200), map.getMax());
		assertEquals(3.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void addSameDifferentIds() {
		map.add("x", new Interval(100, 200), 1.0);
		map.add("y", new Interval(100, 200), 1.0);
		assertContainsExactly(
				100, 2.0,
				199, 2.0);
	}
	
	@Test
	public void addOverlappingBeginByOne() {
		map.add("x", new Interval(100, 200), 1.0);
		map.add("y", new Interval(50, 101), 2.0);
		assertContainsExactly(
				50, 2.0,
				100, 3.0,
				101, 1.0);
		assertEquals(new Interval(100, 101), map.getMax());
		assertEquals(3.0, map.getMaxValue(), EPS);
	}
	
	private void assertContainsExactly(Object... objects) {
		for(int i=0; i<objects.length; i+=2) {
			int time = (Integer)objects[i];
			double value = (Double)objects[i+1];
			double actual = map.getDensity(time);
			if (Math.abs(actual - value) > EPS) {
				Assert.fail("Expected " + value + " but got " + actual + " at " + time);
			}
		}
	}

	@Test
	public void overlappingExtendOne() {
		map.add("x", new Interval(100, 201), 1.0);
		map.add("y", new Interval(100, 202), 2.0);
		assertContainsExactly(
				100, 3.0,
				201, 2.0);
		assertEquals(new Interval(100, 201), map.getMax());
		assertEquals(3.0, map.getMaxValue(), EPS);
	}

	@Test
	public void overlappingPrependOne() {
		map.add("x", new Interval(100, 200), 1.0);
		map.add("y", new Interval(99, 200), 2.0);
		assertContainsExactly(
				99, 2.0,
				100, 3.0);
		assertEquals(new Interval(100, 200), map.getMax());
		assertEquals(3.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void overlappingEnd() {
		map.add("x", new Interval(150, 300), 2.0);
		map.add("y", new Interval(100, 200), 1.0);
		assertContainsExactly(
				100, 1.0,
				150, 3.0,
				200, 2.0);
		assertEquals(new Interval(150, 200), map.getMax());
		assertEquals(3.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void addOverlappingEndByOne() {
		map.add("x", new Interval(100, 201), 1.0);
		map.add("y", new Interval(200, 300), 2.0);
		assertContainsExactly(
				100, 1.0,
				200, 3.0,
				201, 2.0);
		assertEquals(new Interval(200, 201), map.getMax());
		assertEquals(3.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void addOverlappingInnerRange() {
		map.add("x", new Interval(100, 300), 2.0);
		map.add("y", new Interval(150, 200), 1.0);
		assertContainsExactly(
				100, 2.0,
				150, 3.0,
				200, 2.0);
		assertEquals(new Interval(150, 200), map.getMax());
		assertEquals(3.0, map.getMaxValue(), EPS);
	}
	@Test
	public void duplicateRange() {
		map.add("x", new Interval(100, 200), 1.0);
		map.add("y", new Interval(100, 200), 2.0);
		assertContainsExactly(
				100, 3.0,
				199, 3.0);
		assertEquals(new Interval(100, 200), map.getMax());
		assertEquals(3.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void duplicateRangeWithConsecutive() {
		map.add("x", new Interval(100, 201), 1.0);
		map.add("y", new Interval(201, 202), 4.0);
		map.add("z", new Interval(202, 203), 8.0);
		map.add("w", new Interval(100, 202), 2.0);
		assertContainsExactly(
				100, 3.0,
				201, 6.0,
				202, 8.0);
		assertEquals(new Interval(202, 203), map.getMax());
		assertEquals(8.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void mutlipleOverlappingRange() {
		map.add("a", new Interval(100, 120), 1.0);
		map.add("b", new Interval(120, 140), 2.0);
		map.add("b", new Interval(160, 200), 4.0);
		map.add("c", new Interval(110, 180), 8.0);
		assertContainsExactly(
				100, 1.0,
				110, 9.0,
				120, 10.0,
				140, 8.0,
				160, 12.0,
				180, 4.0);
		assertEquals(new Interval(160, 180), map.getMax());
		assertEquals(12.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void triangle() {
		map.addTrapezoid("x", new Interval(10, 20), new Interval(20, 40), 0.5);
		assertContainsExactly(
				10, 0.0,
				14, 0.2,
				20, 0.5,
				30, 0.25,
				40, 0.0);
		
		assertEquals(new Interval(20, 40), map.getMax());
		assertEquals(0.5, map.getMaxValue(), EPS);
	}
	
	@Test
	public void trapezoid() {
		map.addTrapezoid("x", new Interval(10, 20), new Interval(40, 50), 1.0);
		assertContainsExactly(
				10, 0.0,
				14, 0.4,
				20, 1.0,
				25, 1.0,
				40, 1.0,
				45, 0.5,
				50, 0.0);
		
		assertEquals(new Interval(20, 40), map.getMax());
		assertEquals(1.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void overlappingTrapezoid() {
		map.addTrapezoid("x", new Interval(10, 20), new Interval(50, 60), 1.0);
		map.addTrapezoid("y", new Interval(30, 50), new Interval(55, 65), 2.0);
		assertContainsExactly(
				10, 0.0,
				20, 1.0,
				30, 1.0,
				50, 3.0,
				55, 2.5,
				60, 1.0,
				65, 0.0);
		
		assertEquals(new Interval(30, 50), map.getMax());
		assertEquals(3.0, map.getMaxValue(), EPS);
		
	}
	
	@Test
	public void overlappingBeginTrapezoid() {
		map.addTrapezoid("x", new Interval(30, 50), new Interval(55, 65), 2.0);
		map.addTrapezoid("y", new Interval(10, 20), new Interval(25, 35), 1.0);
		assertContainsExactly(
				10, 0.0,
				20, 1.0,
				25, 1.0,
				30, 0.5,
				35, 0.5,
				50, 2.0,
				55, 2.0,
				65, 0.0);
		
		assertEquals(new Interval(50, 55), map.getMax());
		assertEquals(2.0, map.getMaxValue(), EPS);
		
	}
	
	@Test
	public void addThreeOverlappingTrapezoid() {
		map.addTrapezoid("x", new Interval(10, 20), new Interval(50, 70), 1.0);
		map.addTrapezoid("y", new Interval(80, 100), new Interval(110, 120), 2.0);
		map.addTrapezoid("z", new Interval(30, 40), new Interval(60, 90), 4.0);
		assertContainsExactly(
				10, 0.0,
				20, 1.0,
				30, 1.0,
				40, 5.0,
				50, 5.0,
				60, 4.5,
				70, 8.0/3,
				80, 4.0/3,
				90, 1.0,
				100, 2.0,
				110, 2.0,
				120, 0.0);
		
		assertEquals(new Interval(40, 50), map.getMax());
		assertEquals(5.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void addTrapezoidInBetween() {
		map.addTrapezoid("x", new Interval(10, 11), new Interval(13, 14), 1.0);
		map.addTrapezoid("y", new Interval(15, 16), new Interval(16, 17), 2.0);
		map.addTrapezoid("z", new Interval(12, 14), new Interval(14, 16), 4.0);
		assertContainsExactly(
				10, 0.0,
				11, 1.0,
				12, 1.0,
				13, 3.0,
				14, 4.0,
				15, 2.0,
				16, 2.0,
				17, 0.0);
		
		assertEquals(new Interval(13, 14), map.getMax());
		assertEquals(4.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void sameSlopeTrapezoid() {
		map.addTrapezoid("x", new Interval(10, 20), new Interval(40, 50), 1.0);
		map.addTrapezoid("y", new Interval(20, 25), new Interval(35, 40), 0.5);
		assertContainsExactly(
				10, 0.0,
				25, 1.5,
				35, 1.5,
				50, 0.0);
		assertEquals(new Interval(25, 35), map.getMax());
		assertEquals(1.5, map.getMaxValue(), EPS);
	}
	
	@Test
	public void square() {
		map.addTrapezoid("x", new Interval(10, 10), new Interval(40, 40), 1.0);
		map.addTrapezoid("y", new Interval(20, 20), new Interval(60, 60), 2.0);
		assertContainsExactly(
				10, 1.0,
				20, 3.0,
				40, 2.0);
		assertEquals(new Interval(20, 40), map.getMax());
		assertEquals(3.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void removeSingle() {
		map.add("x", new Interval(100, 200), 2.0);
		map.remove("x", new Interval(100, 200), 2.0);
		assertTrue(map.isEmpty());
	}
	
	@Test
	public void removeMiddle() {
		map.add("x", new Interval(100, 200), 1.0);
		map.add("y", new Interval(150, 320), 4.0);
		map.add("z", new Interval(300, 400), 2.0);
		map.remove("y", new Interval(150, 320), 4.0);
		assertContainsExactly(
				100, 1.0,
				200, 0.0,
				300, 2.0);
		assertEquals(new Interval(300, 400), map.getMax());
		assertEquals(2.0, map.getMaxValue(), EPS);
	}
	
	@Test
	public void removeInner() {
		map.add("x", new Interval(100, 200), 1.0);
		map.add("y", new Interval(120, 140), 2.0);
		map.remove("y", new Interval(120, 140), 2.0);
		assertContainsExactly(
				100, 1.0);
	}
	
	@Test
	public void removeOuter() {
		map.add("x", new Interval(100, 200), 1.0);
		map.add("y", new Interval(120, 140), 2.0);
		map.remove("x", new Interval(100, 200), 1.0);
		assertContainsExactly(
				120, 2.0,
				140, 0.0);
	}
	
	@Test
	public void removeSingleEntries() {
		map.add("x", new Interval(5,7), 1.0);
		map.add("y", new Interval(6,7), 2.0);
		map.remove("x", new Interval(5,7), 1.0);
		assertContainsExactly(
				6, 2.0,
				7, 0.0);
	}
	
	@Test
	public void removeTriangle() {
		map.addTrapezoid("x", new Interval(10, 20), new Interval(20, 40), 0.5);
		map.removeTrapezoid("x", new Interval(10, 20), new Interval(20, 40), 0.5);
		assertTrue(map.isEmpty());
	}
	
	@Test
	public void removeTrapezoid() {
		map.addTrapezoid("x", new Interval(10, 20), new Interval(40, 50), 1.0);
		map.removeTrapezoid("x", new Interval(10, 20), new Interval(40, 50), 1.0);
		assertTrue(map.isEmpty());
	}
	
	@Test
	public void removeTrapezoidOverlap() {
		map.addTrapezoid("x", new Interval(1000, 1200), new Interval(1200, 1400), 1.0);
		map.addTrapezoid("y", new Interval(1100, 1210), new Interval(1310, 1410), 2.0);
		map.removeTrapezoid("y", new Interval(1100, 1210), new Interval(1310, 1410), 2.0);
		assertContainsExactly(
				1000, 0.0,
				1200, 1.0,
				1400, 0.0);
	}
	
	@Test
	public void removeSquare() {
		map.addTrapezoid("x", new Interval(10, 10), new Interval(40, 40), 1.0);
		map.addTrapezoid("y", new Interval(20, 20), new Interval(60, 60), 2.0);
		map.removeTrapezoid("x", new Interval(10, 10), new Interval(40, 40), 1.0);
		assertContainsExactly(
				20, 2.0,
				60, 0.0);
	}
	
	@Test
	public void removeOverlappingTrapezoid() {
		map.addTrapezoid("x", new Interval(10, 20), new Interval(50, 60), 1.0);
		map.addTrapezoid("y", new Interval(30, 50), new Interval(55, 65), 2.0);
		map.removeTrapezoid("x", new Interval(10, 20), new Interval(50, 60), 1.0);
		assertContainsExactly(
				30, 0.0,
				50, 2.0,
				55, 2.0,
				65, 0.0);
	}
	
	@Test
	public void removeTrapezoidOverlappingSlope() {
		map.addTrapezoid("x", new Interval(10, 20), new Interval(30, 80), 1.0);
		map.addTrapezoid("y", new Interval(40, 50), new Interval(60, 70), 2.0);
		map.removeTrapezoid("y", new Interval(40, 50), new Interval(60, 70), 2.0);
		assertContainsExactly(
				10, 0.0,
				20, 1.0,
				30, 1.0,
				80, 0.0);
	}
	
	@Test
	public void removeSame() {
		map.add("x", 10, 20, 1.0, 1.0);
		map.add("y", 20, 30, 1.0, 1.0);
		map.add("z", 10, 20, 1.0, 1.0);
		map.remove("x", 10, 20, 1.0, 1.0);
		map.remove("z", 10, 20, 1.0, 1.0);
		assertContainsExactly(
				20, 1.0,
				30, 0.0);
	}
	
	@Test
	public void unmergeSameSlope() {
		map.addTrapezoid("x", new Interval(10, 20), new Interval(40, 50), 1.0);
		map.addTrapezoid("y", new Interval(20, 25), new Interval(35, 40), 0.5);
		map.removeTrapezoid("y", new Interval(20, 25), new Interval(35, 40), 0.5);
		assertContainsExactly(
				10, 0.0,
				20, 1.0,
				40, 1.0,
				50, 0.0);
	}
	
	@Test
	public void maxEmpty() {
		assertNull(map.getMax());
	}
	
	@Test
	public void maxSingle() {
		map.add("x", new Interval(100, 120), 1.0);
		assertEquals(new Interval(100,  120), map.getMax());
		assertEquals(1.0, map.getDensity(map.getMax().getStart()), EPS);
	}
	
	@Test
	public void max() {
		map.add("x", new Interval(100, 200), 1.0);
		map.add("y", new Interval(150, 300), 2.0);
		assertEquals(new Interval(150,200), map.getMax());
		assertEquals(3.0, map.getDensity(map.getMax().getStart()), EPS);
	}
	
	@Test
	public void maxTrapezoid() {
		map.addTrapezoid("x", new Interval(100, 120), new Interval(180, 200), 1.0);
		assertEquals(new Interval(120, 180), map.getMax());
	}
	
	@Test
	public void maxTriangleLeftSlopeLower() {
		map.addTrapezoid("x", new Interval(100, 150), new Interval(150, 180), 2.0);
		assertEquals(new Interval(100, 150), map.getMax());
	}
	
	@Test
	public void maxTriangleRightSlopeLower() {	
		map.addTrapezoid("x", new Interval(100, 150), new Interval(150, 280), 2.0);
		assertEquals(new Interval(150, 280), map.getMax());
	}
	
	@Test
	public void getEntry() {
		map.add("x", new Interval(100, 200), 1.0);
		map.add("x", new Interval(200, 300), 2.0);
		map.add("x", new Interval(400, 500), 4.0);
		
		assertEquals(0, map.getDensity(99), EPS);
		assertEquals(1.0, map.getDensity(100), EPS);
		assertEquals(1.0, map.getDensity(150), EPS);
		assertEquals(1.0, map.getDensity(199), EPS);
		assertEquals(2.0, map.getDensity(200), EPS);
		assertEquals(2.0, map.getDensity(299), EPS);
		assertEquals(0, map.getDensity(300), EPS);
		assertEquals(0, map.getDensity(399), EPS);
		assertEquals(4.0, map.getDensity(400), EPS);
		assertEquals(4.0, map.getDensity(499), EPS);
		assertEquals(0, map.getDensity(500), EPS);
	}
}
