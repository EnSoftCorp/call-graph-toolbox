package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.HybridTypeAnalysis;
import com.ensoftcorp.open.cg.ui.CallGraphPreferences;

public class XTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "XTA Call Graph";
	}

	@Override
	protected Q getCallEdges() {
		Q callEdges = Common.universe().edgesTaggedWithAny(HybridTypeAnalysis.CALL, HybridTypeAnalysis.LIBRARY_CALL);
		if(callEdges.eval().edges().isEmpty()){
			HybridTypeAnalysis xta = new HybridTypeAnalysis();
			xta.run(CallGraphPreferences.isLibraryCallGraphConstructionEnabled());
		}
		return callEdges;
	}

}
