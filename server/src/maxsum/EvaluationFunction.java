package maxsum;


import maxsum.Domain.State;
import maxsum.Maximizer.MultiKeyMap;
import server.model.Agent;
import server.model.task.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Yuai Liu
 * 
 */

//This is used to evaluate allocations
public class EvaluationFunction{
	
	protected MultiKeyMap<Variable, State, Double> utilityTable;

	public EvaluationFunction(){
		utilityTable = new MultiKeyMap<Variable, State, Double>();
	}


	//Calulate the energy needed for a agent to perform a task
	public double checkEnergyConsume(Agent agent, Task task) {

		Double energy = 0.0;
		if (task.getId().equals("none")) {	
            return energy;
       	} else {
			energy = agent.getEnergyConsumption(agent.getCoordinate(), task.getCoordinate());
       	}
       	//System.out.println("Check Energy Consume for "+ agent.getId() + " and " + task.getId());
		
		return energy;
		
		
	}

	//Evaluate a given pair (agent -> task)
	public Double computeUtility(Variable var, State dom){
		double score = 0.0;


		Agent agent = var.getAgent();
		Task task  = dom.getTask();
		
        if (task.getId().equals("none")) {	
            return 0.0;
       	}

		if(utilityTable.get(var, dom) == null){

			double energy = this.checkEnergyConsume(agent, task);

			//Higher power consumption gives lower score
			//Higher task priority gives higher score

			/* Todo: Add more aspects avchieve more complicated evaluation */
			score = ((-1.0)*energy)/(task.getPriority()+1e-6);

			//The maxsum algrithem iterates many times to find the allocation solution.
			//In order to enhance the efficiency, each assignemtn will only be evaluated once 
			//then the score is recorded.
			utilityTable.put(var, dom, score);
		}
		else{

			//Directly read the record if the assignemtn has been evaluated before.
			score = utilityTable.get(var, dom);
		}

		return score;
	}


	//Compute total utility for a given allocation plan
	//The total utility of an allocation plan is the sum of the utilities of each assignment 
	public Double computeUtilities(List<Variable> vars,  List<State> doms){

		Double totalScore = 0.0;
		
		for (int i = 0; i < vars.size(); i++){
			totalScore += computeUtility(vars.get(i), doms.get(i));	
		}

		return totalScore;
	}


	//Evaluate the utiity that a task node can provide in a given allocation plan. 
	public Double evaluate(Constraint constraint, 
			Map<Variable, Domain.State> solution) {

	    
		ArrayList<Variable> vars = new ArrayList<Variable>();
		ArrayList<State> domains = new ArrayList<State>();

		Task task = constraint.getTask();

		if(task.getId().equals("none")){
			return 0.0;
		}

		for (Variable var : solution.keySet()) {
			Domain.State s = solution.get(var);
			Task t = s.getTask();
			
			if (t == task) {
				vars.add(var);
				domains.add(s);
			}
		}

		double value = 0.0;
		if ((vars.size()+task.getAgents().size()) > task.getGroup()) {	
			value =  -10000.0;
		} else if ((vars.size()+task.getAgents().size())!= task.getGroup()) {
			value = -1000.0;

		} else {
			value = computeUtilities(vars, domains);
		}

		/*
		System.out.print(task.getId() + ": ");

		for(Variable var : vars){
			System.out.print(((Agent)var.getId()).toString() + ", ");
		}
		System.out.println(value);
		*/
		return value;

	}


	//Compute utilty for given pair (agent -> task)
	public Double computeUtility(Agent agent, Task task){
		double score = 0.0;
		
        if (task.getId().equals("none")) {	
            return 0.0;
       	}

		double energy = this.checkEnergyConsume(agent, task);
		score = ((-1.0)*energy)/(task.getPriority()+1e-6);



		return score;
	}

}