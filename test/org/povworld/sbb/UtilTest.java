package org.povworld.sbb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.povworld.collection.immutable.ImmutableCollections;

public class UtilTest {

	@Test
	public void testPathIdsToString() {
		Path p1 = new Path(1, 0);
		Path p2 = new Path(2, 0);
		Path p3 = new Path(3, 0);
		Path p4 = new Path(4, 0);

		assertEquals("/1", Util.pathIdsString(ImmutableCollections.listOf(p1)));
		assertEquals("/1/3", Util.pathIdsString(ImmutableCollections.listOf(p1, p3)));
		assertEquals("/1-3", Util.pathIdsString(ImmutableCollections.listOf(p1, p2, p3)));
		assertEquals("/1-2/4", Util.pathIdsString(ImmutableCollections.listOf(p1, p2, p4)));
		assertEquals("/1/3-4", Util.pathIdsString(ImmutableCollections.listOf(p1, p3, p4)));
	}

}
