package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.FieldTypeAnalysis;
import com.ensoftcorp.open.cg.analysis.MethodTypeAnalysis;
import com.ensoftcorp.open.cg.log.Log; 

public class MTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "MTA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		return MethodTypeAnalysis.getInstance(enableCallGraphConstruction).getCallGraph();
	}

}
