package com.ensoftcorp.open.cg.analysis;

import java.text.DecimalFormat;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.open.cg.log.Log;

public abstract class CGAnalysis {

	private boolean hasRun = false;

	protected CGAnalysis(){}
	
	public abstract String getName();
	
	/**
	 * Returns the call graph produced by the algorithm
	 * @return
	 */
	public Q getCallGraph(){
		return Query.universe().edges(getCallEdgeTags());
	}
	
	/**
	 * Returns the set of tags applied to edges during call graph construction
	 * @return
	 */
	public abstract String[] getCallEdgeTags();
	
	/**
	 * Returns the call graph produced by the algorithm
	 * @return
	 */
	public Q getPerControlFlowGraph(){
		return Query.universe().edges(getPerControlFlowEdgeTags());
	}
	
	/**
	 * Returns the set of tags applied to per control flow edges during call graph construction
	 * @return
	 */
	public abstract String[] getPerControlFlowEdgeTags();

	
	/**
	 * Returns true if the call graph construction has completed
	 * @return
	 */
	public boolean hasRun(){
		return hasRun;
	}
	
	/**
	 * Runs the call graph construction (if it hasn't been run already)
	 * and returns the time in milliseconds to complete the analysis
	 * @return
	 */
	public double run(){
		if(hasRun()){
			Log.info(getClass().getSimpleName() + " Call Graph construction has already completed.");
			return 0;
		} else {
			try {
				Log.info("Starting " + getClass().getSimpleName() + " call graph construction");
				long start = System.nanoTime();
				runAnalysis();
				long stop = System.nanoTime();
				double time = (stop - start)/1000.0/1000.0;
				DecimalFormat decimalFormat = new DecimalFormat("#.##");
				Log.info("Finished " + getClass().getSimpleName() + " call graph construction in " + decimalFormat.format(time) + "ms");
				hasRun = true;
				return time;
			} catch (Exception e){
				Log.error("Error constructing call graph.", e);
				hasRun = false;
				return -1;
			}
		}
	}
	
	/**
	 * Runs the call graph construction algorithm
	 */
	protected abstract void runAnalysis();
	
}
