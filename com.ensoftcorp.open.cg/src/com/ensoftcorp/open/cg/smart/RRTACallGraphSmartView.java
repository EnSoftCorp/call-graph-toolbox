package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.open.cg.analysis.ReallyRapidTypeAnalysis; 

public class RRTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "RRTA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		return ReallyRapidTypeAnalysis.getInstance(enableCallGraphConstruction).getCallGraph();
	}

}
