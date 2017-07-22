package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.MethodTypeAnalysis;

public class MTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "MTA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		MethodTypeAnalysis mta = MethodTypeAnalysis.getInstance(enableCallGraphConstruction);
		if(!mta.hasRun()){
			mta.run();
		}
		return mta.getCallGraph();
	}

}
