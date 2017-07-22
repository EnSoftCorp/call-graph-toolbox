package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.open.cg.analysis.RapidTypeAnalysis;

public class RTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "RTA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		RapidTypeAnalysis rta = RapidTypeAnalysis.getInstance(enableCallGraphConstruction);
		if(!rta.hasRun()){
			rta.run();
		}
		return rta.getCallGraph();
	}

}
