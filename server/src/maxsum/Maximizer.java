package maxsum;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import maxsum.Domain.State;

/**
 * @author Feng Wu
 *
 */

public class Maximizer{


	public static class MultiKeyMap<T, S, V> {
		protected final Map<T, Map<S, V>> _values;
		
		public MultiKeyMap() {
			_values = new HashMap<T, Map<S, V>>();
		}
		
		public final V put(final T t, final S s, final V v) {
			Map<S, V> map = _values.get(t);
			if (map == null) {
				map = new HashMap<S, V>();
				_values.put(t, map);
			}
			return map.put(s, v);
		}
		
		public final Map<S, V> get(final T t) {
			return _values.get(t);
		}
		
		public final V get(final T t, final S s) {
			Map<S, V> map = _values.get(t);
			if (map != null) {
				return map.get(s);
			}
			return null;
		}
		
		public final void clear(final T t) {
			Map<S, V> map = _values.get(t);
			if (map == null) {
				map = new HashMap<S, V>();
				_values.put(t, map);
			}
			map.clear();
		}
		
		public final void clear() {
			_values.clear();
		}
		
		@Override
		public String toString() {
			return _values.toString();
		}
		
		@Override
		public int hashCode() {
			return _values.hashCode();
		}
		
		@Override
		public boolean equals(Object o) {
			return _values.equals(o);
		}
	}



	public Double getMaxValue(Constraint constraint, List<Variable> others,
			MultiKeyMap<Variable, State, Double> values, Variable var, State dom) {
		assert (constraint != null);
		assert (var != null && dom != null);
		assert (others != null && values != null);	
		
		return computeMaxValue(constraint, others, values, var, dom);
	}

	public static Double computeMaxValue(Constraint constraint, List<Variable> others,
			MultiKeyMap<Variable, State, Double> values, Variable var, State dom) {
		Map<Variable, State> solution = new HashMap<Variable, State>();
		if (others.isEmpty()) {
			solution.put(var, dom);
			return constraint.evaluate(solution);
		}
		
		int[] indices = new int[others.size()];
		Double maxVal = Double.NEGATIVE_INFINITY;
		do {
			solution.clear();
			double tmpVal = 0.0;
			
			for (int i = 0; i < indices.length; ++i) {
				Variable v = others.get(i);
				State d = v.getDomains().get(indices[i]);
				
				solution.put(v, d);
				
				Double val = values.get(v, d);
				if (val != null) {
					tmpVal += val;
				}
			}
			
			solution.put(var, dom);
			Double value = constraint.evaluate(solution);
			
			if (value == null) {
				continue;
			}
			
			tmpVal += value;
			if (tmpVal > maxVal) {
				maxVal = tmpVal;
			}
		} while (nextSolution(others, indices));
		
		return maxVal;
	}
	
	public static boolean nextSolution(List<Variable> list, int[] indices) {
		++indices[0];
		for (int i = 0; i < indices.length; ++i) {
			int size = list.get(i).getDomains().size();
			if (indices[i] >= size) {
				if (i == indices.length-1) {
					return false;
				}
				indices[i] = 0;
				++indices[i+1];
			}
		}
		return true;
	}

}