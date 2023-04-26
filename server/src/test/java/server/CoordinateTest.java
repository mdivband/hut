package server.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import server.model.Coordinate;

import java.util.Arrays;
import java.beans.Transient;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;



class CoordinateTest {

	Coordinate coordinate;

	@BeforeEach
	void init() {
		coordinate = new Coordinate(86.13, 110.20);
	}

	@Test
	@DisplayName("Should set and display Coordinate")
	void initTest() {
		Coordinate coordinate = new Coordinate(-45, 165);
		assertEquals(this.coordinate.getClass(), Coordinate.class);
	}

	static Stream<Arguments> setTestArguments() {
		return Stream.of(
				Arguments.of(35, 56),
				Arguments.of(-105, 225),
				Arguments.of(85, 250),
				Arguments.of(200, 45));
	}

	@ParameterizedTest
	@MethodSource("setTestArguments")
	@DisplayName("Test Case for set Function")
	void setTest(double lat, double lng) {
		
			coordinate.set(lat, lng);
			assertEquals(coordinate.latitude, lat);
			assertEquals(coordinate.longitude, lng);
		
	}

	@Test
	@DisplayName("Test Case for getLatitude Function")
	void getLatitude() {
		double lat_test = coordinate.getLatitude();
		assertEquals(lat_test, coordinate.latitude);
	}

	@Test
	@DisplayName("Test Case for getLongitude Function")
	void getLongitudeTest() {
		double lng_test = coordinate.getLongitude();
		assertEquals(lng_test, coordinate.longitude);
	}

	static Stream<Arguments> setLatitudeTestArguments(){
		return Stream.of(
			Arguments.of(
				56.4, new Coordinate(0, 0)
			),
			Arguments.of(
				-32, new Coordinate(0, 0)
			),
			Arguments.of(
				91, new Coordinate(0, 0)
			),
			Arguments.of(
			  -104, new Coordinate(0, 0)
			)
		);
	}

	@ParameterizedTest
	@MethodSource("setLatitudeTestArguments")
	@DisplayName("Test Case for setLatitude Function")
	void setLatitudeTest(double d) {
			Coordinate result = coordinate.setLatitude(d);
			assertEquals(d, result.latitude);
		}

	static Stream<Arguments> setLongitudeTestArguments(){
		return Stream.of(
			Arguments.of(12,new Coordinate(0,12)),
			Arguments.of(45,new Coordinate(0,45))
		);
	}

	@ParameterizedTest
	@MethodSource("setLongitudeTestArguments")
	@DisplayName("Test Case for setLongitude Function")
	void setLongitudeTest(double d, Coordinate expectedResult) {
		Coordinate result = coordinate.setLongitude(d);
		assertEquals(result.longitude, expectedResult.longitude);
	}

	static Stream<Arguments> getAngleTestArguments() {
		return Stream.of(
				Arguments.of(new Coordinate(40.3,85.7), -1.60686132659956),
				Arguments.of(new Coordinate(48.1,105.6), -1.5789598929404594));
	}

	@ParameterizedTest
	@MethodSource("getAngleTestArguments")
	@DisplayName("Test Case for getAngle Function")
	void getAngleTest(Coordinate object, double expectedResult) {
		// TODO Make getAngle a class function
		double actual_result = coordinate.getAngle(object);
		assertEquals(actual_result, expectedResult);

	}

	static Stream<Arguments> getCoordinateTestArguments() {
		return Stream.of(
				Arguments.of(10, 23, new Coordinate(86.12992384625592, 110.19928953802221)),
				Arguments.of(50, 30, new Coordinate(86.12955542133545, 110.20102836898225)));
	}

	@ParameterizedTest
	@MethodSource("getCoordinateTestArguments")
	@DisplayName("Test Case for getCoordinate function")
	void getCoordinateTest(double lat, double lng, Coordinate expectedResult) {
		// TODO Make getCoordinate(double distance, double angle) a class function
		Coordinate actual_result = coordinate.getCoordinate(lat,
				lng);
		assertEquals(actual_result.latitude, expectedResult.latitude);
		assertEquals(actual_result.longitude, expectedResult.longitude);

	}

	static Stream<Arguments> getDistanceTestArguments() {
		return Stream.of(
				Arguments.of(new Coordinate(72.1, 158.6), 1733818.3140835818),
				Arguments.of(new Coordinate(15.4, 50.2), 8083133.765400689));
	}

	@ParameterizedTest
	@MethodSource("getDistanceTestArguments")
	@DisplayName("Test Case for getDistance Function")
	void getDistanceTest(Coordinate cdn, double expectedResult) {
		double actual_result = coordinate.getDistance(cdn);
		assertEquals(actual_result, expectedResult);
	}

	static Stream<Arguments> toCartesianTestArguments() {
		return Stream.of(
				Arguments.of(45, new double[] { 8664.660870362684, 9577.219031895844 }),
				Arguments.of(90, new double[] { 7.503215555917266E-13, 9577.219031895844 }));
	}

	@ParameterizedTest
	@MethodSource("toCartesianTestArguments")
	@DisplayName("Test Case for toCartesian Function")
	void toCartesianTest(double lat0, double[] expected_result) {

		
		double[] actual_result = coordinate.toCartesian(lat0);
		assertEquals(actual_result[0], expected_result[0]);
		assertEquals(actual_result[1], expected_result[1]);

	}

	static Stream<Arguments> fromCartesianTestArguments() {
		return Stream.of(
				Arguments.of(4, 50, 32, new Coordinate(0.4496608029593653, 0.04241842461505141)),
				Arguments.of(32, 42, 10, new Coordinate(0.37771507448586683, 0.29222242921398517)));
	}

	@ParameterizedTest
	@MethodSource("fromCartesianTestArguments")
	@DisplayName("Test Case for fromCartesian Function")
	void fromCartesianTest(double x, double y, double lat0, Coordinate expected_result) {

		Coordinate actual_result = Coordinate.fromCartesian(x, y, lat0);
		assertEquals(actual_result.latitude, expected_result.latitude);
		assertEquals(actual_result.longitude, expected_result.longitude);

	}

	static Stream<Arguments> findCentreTestArguments() {
		return Stream.of(
				Arguments.of(
					Arrays.asList(new Coordinate(80, 10), new Coordinate(5, 60), new Coordinate(14, 35), new Coordinate(67, 162)),
						new Coordinate(49.43494231436073, 55.17474569275251)),
				Arguments.of(Arrays.asList(new Coordinate(-1, 120), new Coordinate(-84, 60), new Coordinate(84, 145)),new Coordinate(-0.8711334072160812, 117.68570793588889))
						);
	}

	@ParameterizedTest
	@MethodSource("findCentreTestArguments")
	@DisplayName("Test Case for findCentre Function")
	void findCentreTest(List<Coordinate> coordinates, Coordinate expectedResult) {
		Coordinate actual_result = Coordinate.findCentre(coordinates);
		assertEquals(actual_result.latitude, expectedResult.latitude);
		assertEquals(actual_result.longitude, expectedResult.longitude);
	}

	@Test
	@DisplayName("Test Case for getJSON Function")
	void getJSONTest() {
	JsonObject result = coordinate.getJSON();
		assertEquals(result.getClass(), JsonObject.class);
	}
}
