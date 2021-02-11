package maxsum;

import java.util.ArrayList;
import java.util.Random;
import java.util.HashMap;

import maxsum.Domain.State;

/**
 * @author Feng Wu, Yuai Liu
 *
 */

public class MaxSum{

	protected ArrayList<Variable> variables;
	protected ArrayList<Constraint> constraints;


	public MaxSum(){
		variables = new ArrayList<Variable>();
		constraints = new ArrayList<Constraint>();
	}



	//Agent nodes
	public void addVariable(Variable var){
		this.variables.add(var);
	}

	public void addVariables(Variable... vars){
		for(Variable var : vars){
			addVariable(var);
		}
	}

	public ArrayList<Variable> getVariables(){
		return this.variables;
	}




	//Task nodes
	public void addConstraint(Constraint cst){
		this.constraints.add(cst);
	}

	public void addConstraints(Constraint... csts){
		for(Constraint cst : csts){
			addConstraint(cst);
		}
	}

	public ArrayList<Constraint> getConstraints(){
		return this.constraints;
	}




	public void setConstantFactors(long seed){
		Random rand = new Random(seed);
		for(Variable var : this.variables){
			for(State dom : var.getDomains()){
				double val = rand.nextDouble() * 1e-6;
				var.setConstant(dom, new Double(val));
			}
		}
	}


	public void computeSolution(int steps){
		setConstantFactors(System.currentTimeMillis());

		//Iterate to get the converged solution
		for(int t=0; t<steps; t++){

			//Messages sent from Agent nodes to Task nodes
			for(Constraint cst : this.constraints){
				cst.clearMessage();
			}
			for(Variable var : variables){
				for(Constraint cst : var.getConstraints()){
					Message msg = var.computeVMessage(cst);
					cst.addMessage(msg);
				}
			}


			//Messages sent from Task nodes to Agent nodes
			for(Variable var : this.variables){
				var.clearMessage();
			}
			for(Constraint cst : this.constraints){
				cst.computeMarginalValues();
				for(Variable var : cst.getVariables()){
					Message msg = cst.computeCMessage(var);
					var.addMessage(msg);
				}
			}
		}
	}


	//Return the allocation solution that provide the marginal value
	public HashMap<Variable, State> getSolution(){
		HashMap<Variable, State> solution = new HashMap<Variable, State>();

		for(Variable var : this.variables){
			var.computeMarginalSolution();
			solution.put(var, var.getSolution());
		}

		return solution;
	}

	//Return the utility provided by the solution
	public Double[][] getSolutionValues(){
		Double[][] values = new Double[this.variables.size()][];
		for(int i=0; i<values.length; i++){
			Variable var = this.variables.get(i);
			values[i] = var.getSolutionValues();
		}
		return values;
	}
}
