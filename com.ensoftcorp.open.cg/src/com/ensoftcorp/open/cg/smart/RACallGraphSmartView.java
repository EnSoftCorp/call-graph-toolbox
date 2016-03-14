package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.ReachabilityAnalysis;
import com.ensoftcorp.open.cg.ui.CallGraphPreferences;

public class RACallGraphSmartView extends CallGraphSmartView {

	@Override
	public String getTitle() {
		return "RA Call Graph";
	}

	@Override
	protected Q getCallEdges() {
		Q callEdges = Common.universe().edgesTaggedWithAny(ReachabilityAnalysis.CALL, ReachabilityAnalysis.LIBRARY_CALL);
		if(callEdges.eval().edges().isEmpty()){
			ReachabilityAnalysis ra = new ReachabilityAnalysis();
			ra.run(CallGraphPreferences.isLibraryCallGraphConstructionEnabled());
		}
		return callEdges;
	}

}
