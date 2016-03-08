package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.ReachabilityAnalysis;

public class RACallGraphSmartView extends CallGraphSmartView {

	@Override
	public String getTitle() {
		return "RA Call Graph";
	}

	@Override
	protected Q getCallEdges() {
		Q callEdges = Common.universe().edgesTaggedWithAny(ReachabilityAnalysis.CALL);
		if(callEdges.eval().edges().isEmpty()){
			ReachabilityAnalysis ra = new ReachabilityAnalysis();
			ra.run();
		}
		return callEdges;
	}

}
