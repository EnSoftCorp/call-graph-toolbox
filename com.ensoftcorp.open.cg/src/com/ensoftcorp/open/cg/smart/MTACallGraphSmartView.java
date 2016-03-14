package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.MethodTypeAnalysis;
import com.ensoftcorp.open.cg.ui.CallGraphPreferences;

public class MTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "MTA Call Graph";
	}

	@Override
	protected Q getCallEdges() {
		Q callEdges = Common.universe().edgesTaggedWithAny(MethodTypeAnalysis.CALL);
		if(callEdges.eval().edges().isEmpty()){
			MethodTypeAnalysis mta = new MethodTypeAnalysis();
			mta.run(CallGraphPreferences.isLibraryCallGraphConstructionEnabled());
		}
		return callEdges;
	}

}
