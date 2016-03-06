package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.ZeroControlFlowAnalysis;

public class ZeroCFACallGraphSmartView extends CallGraphSmartView {

	@Override
	public String getTitle() {
		return "0-CFA Call Graph";
	}

	@Override
	protected Q getCallEdges() {
		Q callEdges = Common.universe().edgesTaggedWithAny(ZeroControlFlowAnalysis.CALL);
		if(callEdges.eval().edges().isEmpty()){
			ZeroControlFlowAnalysis zeroCFA = new ZeroControlFlowAnalysis();
			zeroCFA.run();
		}
		return callEdges;
	}

}
