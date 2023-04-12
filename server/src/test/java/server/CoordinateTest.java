
package server.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.beans.Transient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoordinateTest {

	// @Test
	// @DisplayName("Should Return True")
	// void addsTwoNumbers() {
	// 	Simulator sim = new Simulator();
	// 	assertEquals(true, sim.loadScenarioMode("debrisTest.json"), "1 + 1 should equal 2");
	// }

	@Test
	@DisplayName("Should set and display Coordinate")
	void addsTwoNumber()
	{
		Coordinate cd = new Coordinate(1313.1313, 420.420);
		assertEquals(1313.1313, cd.getLatitude(), "1+1 should equal 2");
	}
}
