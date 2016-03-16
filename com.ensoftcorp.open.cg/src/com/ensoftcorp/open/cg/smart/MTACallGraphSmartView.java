package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.open.cg.analysis.MethodTypeAnalysis;

public class MTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "MTA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		MethodTypeAnalysis mta = MethodTypeAnalysis.getInstance(enableCallGraphConstruction);
		return mta.getCallGraph();
	}

}
