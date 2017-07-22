package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.ExceptionTypeAnalysis;

public class ETACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "ETA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		ExceptionTypeAnalysis eta = ExceptionTypeAnalysis.getInstance(enableCallGraphConstruction);
		if(!eta.hasRun()){
			eta.run();
		}
		return eta.getCallGraph();
	}

}
