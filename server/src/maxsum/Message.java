package maxsum;


import java.util.Map;
import java.util.HashMap;

import maxsum.Domain.State;


/**
 * @author Feng Wu, Yuai Liu
 * 
 */

public class Message{
	public final static int V_MESSAGE = 0;
	public final static int C_MESSAGE = 0;

	protected int type;
	protected Variable variable;
	protected Constraint constraint;
	protected Map<State, Double> values;



	public Message(Variable var, Constraint cst, int type){
		this.variable = var;
		this.constraint = cst;
		this.type = type;
		values = new HashMap<State, Double>();
	}


	public Message(Variable var, Constraint cst){
		this(var, cst, V_MESSAGE);
	}

	public Message(Constraint cst, Variable var){
		this(var, cst, C_MESSAGE);
	}

	public int getType(){
		return this.type;
	}


	public Double getValue(State dom){
		return values.get(dom);
	}
	
	public void setValue(State dom, Double val){
		values.put(dom, val);
	}

	public void clearValues(){
		for(State dom : values.keySet()){
			setValue(dom, null);
		}
	}

	public Variable getVariable(){
		return this.variable;
	}


	public Constraint getConstraint(){
		return this.constraint;
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (this.type == V_MESSAGE) {
			sb.append(this.variable.getAgent().getId() + "->" + this.constraint.getTask().getId() + " = ");
		}
		if (this.type == C_MESSAGE) {
			sb.append(this.constraint.getTask().getId() + "->" + this.variable.getAgent().getId() + " = ");
		}
		for (State dom : this.values.keySet()) {
			sb.append(dom.getTask().getId() + ":" + this.values.get(dom) + " ");
		}
		return sb.toString();
	}

//	public void normValues() {
//		Double val = new Double();
//		for (State dom : _values.keySet()) {
//			val.add(_values.get(dom));
//		}
//
//		int size = _values.keySet().size();
//		val.divide(new Double(size)).negate();
//		
//		for (State dom : _values.keySet()) {
//			Double v = _values.get(dom);
//			_values.put(dom, v.add(val));
//		}
//	}
}