package com.ensoftcorp.open.cg.ui.smart;

import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.java.core.script.Common;
import com.ensoftcorp.open.cg.analysis.CGAnalysis;
import com.ensoftcorp.open.cg.analysis.FieldTypeAnalysis;
import com.ensoftcorp.open.cg.preferences.CallGraphPreferences; 

public class FTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "FTA Call Graph";
	}

	@Override
	protected Q getCallGraph() {
		CGAnalysis cgAnalysis = FieldTypeAnalysis.getInstance();
		if(!CallGraphPreferences.isFieldTypeAnalysisEnabled()){
			Log.warning(cgAnalysis.getName() + " has not been run. Smart View will not contain results.");
			return Common.empty();
		}
		return cgAnalysis.getCallGraph();
	}

}
