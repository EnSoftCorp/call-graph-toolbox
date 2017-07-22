package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.FieldTypeAnalysis;

public class FTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "FTA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		FieldTypeAnalysis fta = FieldTypeAnalysis.getInstance(enableCallGraphConstruction);
		if(!fta.hasRun()){
			fta.run();
		}
		return fta.getCallGraph();
	}

}
