package com.ensoftcorp.open.cg.ui.smart;

import com.ensoftcorp.open.cg.log.Log;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.java.core.script.Common;
import com.ensoftcorp.open.cg.analysis.CGAnalysis;
import com.ensoftcorp.open.cg.analysis.ReachabilityAnalysis;
import com.ensoftcorp.open.cg.preferences.CallGraphPreferences; 

public class RACallGraphSmartView extends CallGraphSmartView {

	@Override
	public String getTitle() {
		return "RA Call Graph";
	}

	@Override
	protected Q getCallGraph() {
		CGAnalysis cgAnalysis = ReachabilityAnalysis.getInstance();
		if(!CallGraphPreferences.isReachabilityAnalysisEnabled()){
			Log.warning(cgAnalysis.getName() + " has not been run. Smart View will not contain results.");
			return Common.empty();
		}
		return cgAnalysis.getCallGraph();
	}

}
