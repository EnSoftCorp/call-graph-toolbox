package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.java.core.script.Common;
import com.ensoftcorp.open.cg.analysis.CGAnalysis;
import com.ensoftcorp.open.cg.analysis.RapidTypeAnalysis; 

public class RTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "RTA Call Graph";
	}

	@Override
	protected Q getCallGraph() {
		CGAnalysis cgAnalysis = RapidTypeAnalysis.getInstance();
		if(!cgAnalysis.hasRun()){
			Log.warning(cgAnalysis.getName() + " has not been run. Smart View will not contain results.");
			return Common.empty();
		}
		return cgAnalysis.getCallGraph();
	}

}
