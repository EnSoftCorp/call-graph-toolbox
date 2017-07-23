package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.HybridTypeAnalysis;
import com.ensoftcorp.open.cg.analysis.ZeroControlFlowAnalysis;
import com.ensoftcorp.open.cg.log.Log; 

public class ZeroCFACallGraphSmartView extends CallGraphSmartView {

	@Override
	public String getTitle() {
		return "0-CFA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		return ZeroControlFlowAnalysis.getInstance(enableCallGraphConstruction).getCallGraph();
	}

}
