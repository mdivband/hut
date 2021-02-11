package maxsum;

import java.util.ArrayList;
import java.util.Collection;


import server.model.task.Task;

/**
 * @author Feng Wu, Yuai Liu
 * 
 */

//A domain of an agent node is a collection of all the task nodes that are connected to this agent node.
public class Domain extends ArrayList<Domain.State>{
	private static final long serialVersionUID = 9165514451793783661L;


	//Each state represents an Assginment
	public static class State{
		protected final Task task;

		public State(Task task) {
			this.task = task;
		}

		public Task getTask(){
			return this.task;
		}

		public boolean equals(Object obj){
			if (obj != null && obj instanceof State){
				return this.task.getId().equals(((State)obj).getTask().getId());
			}
			return false;
		}

		public String toString(){
			return this.task.getId();
		}

	}


	public Domain(){
		super();
	}

	public Domain(int size){
		super(size);
	}

	public Domain(Collection<? extends State> states){
		super(states);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		boolean first = true;
		for (State dom : this) {
			if (first) first = false;
			else sb.append(',');
			sb.append(dom.toString());
		}
		sb.append(']');
		return sb.toString();
	}

}
