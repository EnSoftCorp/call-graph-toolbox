package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.RapidTypeAnalysis;

public class RTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "RTA Call Graph";
	}

	@Override
	protected Q getCallEdges() {
		Q callEdges = Common.universe().edgesTaggedWithAny(RapidTypeAnalysis.CALL);
		if(callEdges.eval().edges().isEmpty()){
			RapidTypeAnalysis rta = new RapidTypeAnalysis();
			rta.run();
		}
		return callEdges;
	}

}
