package org.povworld.sbb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimeUtilTest {

	@Test
	public void parseTime() {
		assertEquals(24, TimeUtil.parseTime("00:00:24"));
		assertEquals(60 * 12 + 24, TimeUtil.parseTime("00:12:24"));
		assertEquals(3600 * 13 + 60 * 07 + 33, TimeUtil.parseTime("13:07:33"));
	}
	
	@Test
	public void parseEmptyDuration() {
		assertEquals(0, TimeUtil.parseDuration(""));
	}
	
	@Test
	public void parseDuration() {
		assertEquals(10, TimeUtil.parseDuration("PT10S"));
		assertEquals(65, TimeUtil.parseDuration("PT1M5S"));
	}
	
	@Test
	public void unparseTime() {
		assertEquals("00:00:24", TimeUtil.unparseTime(24));
		assertEquals("00:12:24", TimeUtil.unparseTime(60 * 12 + 24));
		assertEquals("13:07:33", TimeUtil.unparseTime(3600 * 13 + 60 * 07 + 33));
	}
	
}
