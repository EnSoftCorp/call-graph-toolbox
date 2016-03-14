package com.ensoftcorp.open.cg.analysis;

import com.ensoftcorp.atlas.core.log.Log;

public abstract class CGAnalysis {

	private boolean hasRun = false;
	
	/**
	 * Returns true if the call graph construction has completed
	 * @return
	 */
	public boolean hasRun(){
		return hasRun || graphHasEvidenceOfPreviousRun();
	}
	
	public abstract boolean graphHasEvidenceOfPreviousRun();
	
	/**
	 * Runs the call graph construction (if it hasn't been run already)
	 * and returns the time in milliseconds to complete the analysis
	 * @return
	 */
	public double run(final boolean libraryCallGraphConstructionEnabled){
		if(hasRun()){
			Log.info(getClass().getSimpleName() + " Call Graph construction has already completed.");
			return 0;
		} else {
			try {
				long start = System.nanoTime();
				Log.info("Starting " + getClass().getSimpleName() + " Call Graph Construction");
				runAnalysis(libraryCallGraphConstructionEnabled);
				Log.info("Finished " + getClass().getSimpleName() + " Call Graph Construction");
				hasRun = true;
				long stop = System.nanoTime();
				return (stop - start)/1000.0/1000.0;
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
	protected abstract void runAnalysis(boolean libraryCallGraphConstructionEnabled);
	
}
