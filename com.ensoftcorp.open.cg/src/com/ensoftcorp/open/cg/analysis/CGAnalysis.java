package com.ensoftcorp.open.cg.analysis;

import com.ensoftcorp.atlas.core.log.Log;

public abstract class CGAnalysis {

	private boolean hasRun = false;
	
	/**
	 * Returns true if the call graph analysis has completed
	 * @return
	 */
	public boolean hasRun(){
		return hasRun;
	}
	
	/**
	 * Runs the call graph analysis (if it hasn't been run already)
	 * and returns the time in milliseconds to complete the analysis
	 * @return
	 */
	public long run(){
		if(hasRun){
			return 0;
		} else {
			try {
				long start = System.currentTimeMillis();
				Log.info("Starting " + getClass().getSimpleName() + " Points-to Analysis");
				runAnalysis();
				Log.info("Finished " + getClass().getSimpleName() + " Points-to Analysis");
				hasRun = true;
				return System.currentTimeMillis() - start;
			} catch (Exception e){
				Log.error("Error constructing call graph.", e);
				hasRun = false;
				return -1;
			}
		}
	}
	
	/**
	 * Runs the call graph analysis algorithm
	 */
	protected abstract void runAnalysis();
	
}
