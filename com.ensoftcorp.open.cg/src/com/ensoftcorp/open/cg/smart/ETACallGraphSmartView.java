package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.ClassicHybridTypeAnalysis;
import com.ensoftcorp.open.cg.analysis.ExceptionTypeAnalysis;
import com.ensoftcorp.open.cg.log.Log; 

public class ETACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "ETA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		return ExceptionTypeAnalysis.getInstance(enableCallGraphConstruction).getCallGraph();
	}

}
