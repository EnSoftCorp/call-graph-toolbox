package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.open.cg.analysis.ReachabilityAnalysis; 

public class RACallGraphSmartView extends CallGraphSmartView {

	@Override
	public String getTitle() {
		return "RA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		return ReachabilityAnalysis.getInstance(enableCallGraphConstruction).getCallGraph();
	}

}
