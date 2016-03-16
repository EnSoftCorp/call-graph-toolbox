package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.open.cg.analysis.ZeroControlFlowAnalysis;

public class ZeroCFACallGraphSmartView extends CallGraphSmartView {

	@Override
	public String getTitle() {
		return "0-CFA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		ZeroControlFlowAnalysis zeroCFA = ZeroControlFlowAnalysis.getInstance(enableCallGraphConstruction);
		return zeroCFA.getCallGraph();
	}

}
