package org.povworld.sbb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.povworld.collection.common.Interval;

public class ResourceAllocatorTest {
	private final ResourceAllocator allocator = new ResourceAllocator("R", 10);

	@Test
	public void testOccupyConsecutiveSuceeds() {
		assertTrue(allocator.occupy(new Interval(1000, 1100)));
		assertTrue(allocator.occupy(new Interval(1110, 1200)));
		assertTrue(allocator.occupy(new Interval(1210, 1300)));
		assertTrue(allocator.occupy(new Interval(900, 990)));
		assertTrue(allocator.occupy(new Interval(1310, 1310)));
		assertTrue(allocator.occupy(new Interval(1320, 1320)));
	}

	@Test
	public void testOccupyOverlappingFails() {
		assertTrue(allocator.occupy(new Interval(1000, 1100)));
		assertFalse(allocator.occupy(new Interval(1050, 1060)));
		assertFalse(allocator.occupy(new Interval(1100, 1101)));
		assertFalse(allocator.occupy(new Interval(1109, 1110)));
		assertFalse(allocator.occupy(new Interval(995, 996)));
		assertFalse(allocator.occupy(new Interval(990, 991)));
		assertFalse(allocator.occupy(new Interval(996, 1005)));
	}
}
