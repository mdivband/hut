package server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.beans.Transient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import server.controller.AgentController;
import server.controller.TaskController;
import server.model.Coordinate;
import server.model.State;
import server.model.Agent;
import server.model.task.Task;
import server.model.target.Target;
import server.model.hazard.Hazard;
import java.lang.Thread.*;
import java.lang.reflect.*; 

import java.util.*;

import javax.naming.directory.DirContext;

class SimulatorTest {

	Simulator sim = new Simulator();
	@Test
	@DisplayName("Should start the Simulator on a new thread")
	void startTest() {
		Integer number_of_threads = Thread.activeCount();
		sim.start();
		assertEquals(number_of_threads+1,Thread.activeCount());
	}

	@Test
	@DisplayName("Should start the Simulator in Sandbox method")
	void startSandboxModeTest() {
		sim.startSandboxMode();
		State testState = sim.getState();
		assertEquals(0,testState.getGameType());
	}

	@Test
	@DisplayName("Should Load Scenario from File")
	void loadScenarioFromFileTestTrue(){
		Method lsffa[] = Simulator.class.getDeclaredMethods();
		Method lsff = lsffa[14];
		lsff.setAccessible(true);
		try{
			Object o = lsff.invoke(sim,"/web/scenarios/debrisTest.json");
			assertEquals(true,o);
		}
		catch(Exception e)
		{	
			System.out.println("Exception is: "+e);
		}
	}

	@Test
	@DisplayName("Should Not Load Scenario from File")
	void loadScenarioFromFileTestFail(){
		Method lsffa[] = Simulator.class.getDeclaredMethods();
		Method lsff = lsffa[14];
		lsff.setAccessible(true);
		try{
			Object o = lsff.invoke(sim,"/web/scenarios/debris.json");
			assertEquals(false,o);
		}
		catch(Exception e)
		{	
			System.out.println("Execption is: "+e);
		}
	}

	@Test
	@DisplayName("Should Load Scenario Mode")
	void loadScenarioModeTrueTest() {
		assertEquals(true, sim.loadScenarioMode("debrisTest.json"));
	}

	@Test
	@DisplayName("Should Return False")
	void loadScenarioModeFalseTest() {
		assertEquals(false, sim.loadScenarioMode("debris.json"));
	}

	@Test
	@DisplayName("Should start Simulation")
	void startSimulationTestTrue()
	{
		sim.startSimulation();
		State tempState = sim.getState();
		assertEquals(true,tempState.isInProgress());
	}

	@Test
	@DisplayName("Should not start Simulation")
	void startSimulationTestFalse()
	{
		State tempState = sim.getState();
		assertEquals(false,tempState.isInProgress());
	}

	@Test
	@DisplayName("Should return Map of Scenario Files with GameIds")
	void getScenarioFileListWithGameIdsTest() {
		Map<String,String> mp = sim.getScenarioFileListWithGameIds();
		assertNotNull(mp);
	}

	@Test
	@DisplayName("Should change view")
	void changeViewTestTrue()
	{
		sim.changeView(true);
		State testState = sim.getState();
		assertEquals(true,testState.isEditMode());
	}

	@Test
	@DisplayName("Should not change view")
	void changeViewTestFalse()
	{
		sim.changeView(false);
		State testState = sim.getState();
		assertEquals(false,testState.isEditMode());
	}

	@Test
	@DisplayName("Set prov docs")
	void setProvDocTest()
	{
		sim.setProvDoc("testID");
		State testState = sim.getState();
		
		try{
			Field field = State.class.getDeclaredField("prov_doc");
			field.setAccessible(true);
			Object testField = field.get(testState);
			assertEquals("testID",testField);
		}
		catch(Exception e)
		{
			System.out.println("Exception: "+e);
		}
	}

	@Test
	@DisplayName("Reset the State of Simulator")
	void resetTest()
	{
		sim.reset();
		State testState = sim.getState();
		try{
			double time = testState.getTime();
			boolean editMode = testState.isEditMode();
			boolean inProgress = testState.isInProgress();
			Collection<Agent> testAgents = testState.getAgents();
			Collection<Task> testTasks = testState.getTasks();
			Collection<Target> testTargets = testState.getTargets();
			Collection<Hazard> testHazards = testState.getHazards();
			Map<String, String> testAllocation = testState.getAllocation();
			Map<String, String> testTempAllocation = testState.getTempAllocation();
			
			Field completedTasksField = State.class.getDeclaredField("completedTasks");
			Field hazardHitsField = State.class.getDeclaredField("hazardHits");

			completedTasksField.setAccessible(true);
			hazardHitsField.setAccessible(true);

			Object completedTaskFieldObject = completedTasksField.get(testState);
			Object hazardHitsFieldObject = hazardHitsField.get(testState);

			assertEquals("[]",completedTaskFieldObject.toString(),"List of completed tasks should be empty");
			assertEquals(0,time,"Time should be 0 in state");
			assertEquals(false,editMode,"Edit mode should be false");
			assertEquals(false,inProgress,"In progress should be false");
			assertEquals(true,testAgents.isEmpty(),"List of agents should be empty");
			assertEquals(true,testTasks.isEmpty(),"List of Tasks should be empty");
			assertEquals(true,testTargets.isEmpty(),"List of Targets should be empty");
			assertEquals(true,testHazards.isEmpty(),"List of Hazards should be empty");
			assertEquals(true,testAllocation.isEmpty(),"List of Allocations should be empty");
			assertEquals(true,testTempAllocation.isEmpty(),"List of Allocations should be empty");
		}
		catch(Exception e)
		{
			System.out.println("Exception: "+e);
		}
	}
	
	@Test
	@DisplayName("Should get scenario from file name")
	void getScenarioNameFromFileTest() {
		Method gsnffl[] = Simulator.class.getDeclaredMethods();
		Integer c = 0;
		try
		{
			Method gsnffMethod = gsnffl[15];
			gsnffMethod.setAccessible(true);
			Object scenario = gsnffMethod.invoke(sim,"web/scenarios/debrisTest.json");
			assertEquals("Debris Test",scenario);
		}
		catch(Exception e)
		{
			System.out.println("Exception: "+e);
		}
	}


	@Test
	@DisplayName("Should get state as string")
	void getStateAsStringTest() {
		String state_as = sim.getStateAsString();
		assertNotNull(state_as);
	}

	@Test
	@DisplayName("Should get state")
	void getStateTest() {
		State var = sim.getState();
		assertNotNull(var);
	}

	@Test
	@DisplayName("Should return allocator")
	void getAllocatorTest() {
		Allocator allocator = new Allocator(sim);
		assertNotNull(allocator);
	}

	@Test
	@DisplayName("Should return AgentController")
	void getAgentControllerTest(){
		AgentController agentcontroller = sim.getAgentController();
		assertNotNull(agentcontroller);
	}

	@Test
	@DisplayName("Should return TaskController")
	void getTaskController(){
		TaskController taskcontroller = sim.getTaskController();
		assertNotNull(taskcontroller);
	}

	@Test
	@DisplayName("Should return QueueManager")
	void getQueueManagerTest(){
		QueueManager queuemanager = sim.getQueueManager();
		assertNotNull(queuemanager);
	}
}
