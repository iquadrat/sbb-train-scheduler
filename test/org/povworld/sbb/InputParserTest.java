package org.povworld.sbb;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

public class InputParserTest {
	@Test
	public void parseDummyFile() throws IOException {
		InputParser.parseScenario(new File("problem_instances/01_dummy.json"));
	}
	
	@Test
	public void parseInputFile2() throws IOException {
		InputParser.parseScenario(new File("problem_instances/02_a_little_less_dummy.json"));
	}
	
	@Test
	public void parseInputFile5() throws IOException {
		InputParser.parseScenario(new File("problem_instances/05_V1.02_FWA_with_obstruction.json"));
	}
	
	@Test
	public void parseInputFile8() throws IOException {
		InputParser.parseScenario(new File("problem_instances/08_V1.30_FWA.json"));
	}

	@Test
	public void parseInputFile9() throws IOException {
		InputParser.parseScenario(new File("problem_instances/09_ZUE-ZG-CH_0600-1200.json"));
	}
}
