package com.ensoftcorp.open.cg.analysis;

/**
 * Performs a 0-CFA Andersen style points-to analysis
 * to resolve runtime types and infer dynamic dispatches
 * on the fly. Of CHA, RTA, and VTA this is the most precise
 * analysis for call graph construction.
 * 
 * Note: The CFA expands to Control Flow Analysis, but the
 * name no longer really captures what the algorithm is
 * doing under the hood.  
 * 
 * Reference: http://matt.might.net/articles/implementation-of-kcfa-and-0cfa/
 * 
 * @author Ben Holland
 */
public class ZeroControlFlowAnalysis extends CGAnalysis {

	@Override
	protected void runAnalysis() {
		// TODO Auto-generated method stub
		
	}

}
