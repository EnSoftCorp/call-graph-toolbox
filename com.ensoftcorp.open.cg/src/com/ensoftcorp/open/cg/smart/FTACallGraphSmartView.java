package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.open.cg.analysis.FieldTypeAnalysis;

public class FTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "FTA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		FieldTypeAnalysis fta = FieldTypeAnalysis.getInstance(enableCallGraphConstruction);
		return fta.getCallGraph();
	}

}
