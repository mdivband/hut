package maxsum;

import java.util.ArrayList;
import java.util.Map;


import maxsum.Maximizer.MultiKeyMap;
import maxsum.Domain.State;
import server.model.task.Task;

/**
 * @author Feng Wu, Yuai Liu
 * 
 */

//Task node
public class Constraint implements Comparable<Constraint>{

	protected final Task task;

	protected MultiKeyMap<Variable, State, Double> values;
	protected ArrayList<Variable> variables;
	protected ArrayList<Message> messages;

	protected Maximizer maximizer;
	protected EvaluationFunction function;


	public Constraint(Task task, EvaluationFunction function){
		this.task = task;
		this.function = function;

		values = new MultiKeyMap<Variable, State, Double>();
		variables = new ArrayList<Variable>();
		messages = new ArrayList<Message>();

		maximizer = new Maximizer();
	}


	
	@Override
	public String toString() {
		return this.task.toString();
	}
	

	@Override
	public int compareTo(Constraint cst) {		
		return this.task.compareTo(cst.getTask());
	}
	

	//Two constraints are the same if they represent the same task
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Constraint) {
			return this.task.getId().equals(((Constraint)obj).getTask().getId());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return this.task.hashCode();
	}


	//Get the task that the constraint represents
	public Task getTask(){
		return this.task;
	}




	public void setMaximizer(Maximizer maximizer){
		this.maximizer = maximizer;
	}


	//set the evaluation function, which is used to evaluate the utility of each possible allocation
	public void setFunction(EvaluationFunction function){
		this.function = function;
	}

	public EvaluationFunction getFunction(){
		return this.function;
	}

	//Evaluate a given alloation plan
	public Double evaluate(Map<Variable, State> solution){
		return this.function.evaluate(this, solution);
	}




	//Connected agent nodes
	public void addVariable(Variable var){
		this.variables.add(var);
	}

	//Connect an agent node to this task node
	public void addVariables(Variable... vars){
		for(Variable var : vars){
			this.variables.add(var);
		}
	}

	//Return all the agent nodes that are connected with this task node.
	public ArrayList<Variable> getVariables(){
		return this.variables;
	}



	//Calculate marginal value for each possible assignments
	public void computeMarginalValues(){
		for(Variable var : this.variables){
			values.clear(var);
		}

		for(Message msg : this.messages){
			Variable var = msg.getVariable();

			for(State dom : var.getDomains()){
				Double val = msg.getValue(dom);

				Double v = this.values.get(var, dom);
				if(v != null){
					val += v;
				}

				this.values.put(var, dom, val);
			}
		}
	}

	//This method takes an agent node as a parameter
	//and calculates a message from this task to the agent
	public Message computeCMessage(Variable var){

		ArrayList<Variable> others = new ArrayList<Variable>(this.variables);
		others.remove(var);

		Message message = new Message(this, var);
		for(State dom : var.getDomains()){
			Double maxVal = this.maximizer.getMaxValue(this, others, this.values, var, dom);
			message.setValue(dom, maxVal);
		}

		return message;

	}

	//This method is used to receive new messages 
 	public void addMessage(Message msg){
		this.messages.add(msg);
	}

	//This message returns all the messages that the task node received
	public ArrayList<Message> getMessages(){
		return this.messages;
	}

	public void clearMessage(){
		this.messages.clear();
	}

}