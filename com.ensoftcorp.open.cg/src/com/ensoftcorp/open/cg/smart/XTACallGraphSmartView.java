package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.HybridTypeAnalysis;
import com.ensoftcorp.open.cg.analysis.RapidTypeAnalysis;
import com.ensoftcorp.open.cg.log.Log; 

public class XTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "XTA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		return HybridTypeAnalysis.getInstance(enableCallGraphConstruction).getCallGraph();
	}

}
