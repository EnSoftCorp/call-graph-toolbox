package com.ensoftcorp.open.cg.analysis;

/**
 * Performs a Variable Type Analysis (VTA).
 * 
 * In terms of call graph construction precision this algorithm ranks better than
 * CHA and RTA.
 * 
 * Reference: http://people.cs.vt.edu/ryder/516/sp03/lectures/ReferenceAnalysis-6-304.pdf
 * 
 * @author Ben Holland
 */
public class VariableTypeAnalysis extends CGAnalysis {

	@Override
	protected void runAnalysis() {
		// TODO implement
		
		// 1) Start with a CHA analysis
		// 2) Build a type propagation graph
		// 3) Remove conservative CHA edges
	}

}
