package maxsum;

import maxsum.Domain.State;
import server.model.Agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Feng Wu, Yuai Liu
 * 
 */

//Agent node
public class Variable{

	protected final Agent agent;
	protected Domain domain;
	protected ArrayList<Message> messages;
	protected ArrayList<Constraint> constraints;
	protected HashMap<State, Double> constants;

	protected State solution;
	protected Double solutionValue;


	public Variable(Agent agent){
		this.agent = agent;

		this.domain = new Domain();
		this.messages = new ArrayList<Message>();
		this.constraints = new ArrayList<Constraint>();
		this.constants = new HashMap<State, Double>();

		this.solution = null;
		this.solutionValue = null;
	}


	public Agent getAgent(){
		return this.agent;
	}

	//Two nodes are the same if they represent the same agent
	public boolean equals(Object obj) {

		if (obj instanceof Variable) {
			return this.agent.getId().equals(((Variable)obj).getAgent().getId());
		}
		return false;
	}



	//Set factor constants
	public void setConstant(State dom, Double val){
		this.constants.put(dom, val);
	}

	public Double getConstant(State dom){
		return this.constants.get(dom);
	}




	//Possible assignments 
	public void addDomain(State dom){
		this.domain.add(dom);
		this.constants.put(dom, null);
	}

	public void addDomains(State... domains){
		for(State dom:domains){
			addDomain(dom);
		}
	}

	public void setDomains(Domain dom){
		this.domain = dom;
	}

	public Domain getDomains(){
		return this.domain;
	}




	//Connected Task Nodes
	public void addConstraint(Constraint cst){
		this.constraints.add(cst);
	}

	public void addConstraints(Constraint... csts){
		for(Constraint cst : csts){
			this.constraints.add(cst);
		}
	}

	public ArrayList<Constraint> getConstraints(){
		return this.constraints;
	}




	//This method takes a task node as a parameter
	//and calculates a message from this agent to the task
	public Message computeVMessage(Constraint cst){
		Message message = new Message(this, cst);

		for(State dom : this.domain){
			Double messageVal = 0.0;
			for(Message msg: this.messages){
				if(!msg.getConstraint().equals(cst)){
					messageVal += msg.getValue(dom);
				}
			}
			if(this.getConstant(dom) != null){
				messageVal += this.getConstant(dom);
			}
			message.setValue(dom, messageVal);
		}

		return message;
	}

	
	public void addMessage(Message msg){
		this.messages.add(msg);
	}

	public ArrayList<Message> getMessages(){
		return this.messages;
	}

	public void clearMessage(){
		this.messages.clear();
	}




	//Calculat total utility for each posible assignment
	public HashMap<State, Double> computeMarginalValues(){
		HashMap<State, Double> domVal = new HashMap<State, Double>();

		for(State dom : this.domain){
			Double sumVal = 0.0;
			for(Message msg : this.messages){
				sumVal += msg.getValue(dom);
			}
			domVal.put(dom, sumVal);
		}
		return domVal;
	}



	//Find the assignment which provides the maximum value
	public void computeMarginalSolution(){

		if(this.domain.size() > 0){
			this.solution = this.domain.get(0);
			Double maxVal = Double.NEGATIVE_INFINITY;

			//Find the assignment provides maximum
			HashMap<State, Double> domVal = computeMarginalValues();
			
			for(State dom : this.domain){
				Double tempVal = domVal.get(dom);
				
				if(tempVal.doubleValue() > maxVal.doubleValue()){
					this.solution = dom;
					this.solutionValue = tempVal;
					maxVal = tempVal;
				}
			}
		}
	}



	public void setSolution(State dom){
		this.solution = dom;
	}


	public State getSolution(){
		if(this.solution == null){
			this.computeMarginalSolution();
		}

		return this.solution;
	}


	public void setSolutionValue(Double val){
		this.solutionValue = val;
	}


	public Double getSolutionValue(){
		return this.solutionValue;
	}


	public Double[] getSolutionValues(){
		Map<State, Double> domVal = computeMarginalValues();
		Double[] values = new Double[this.domain.size()];

		for(int i=0; i< values.length; i++){
			values[i] = new Double(domVal.get(this.domain.get(i)));		
		}

		return values;
	}


	
}