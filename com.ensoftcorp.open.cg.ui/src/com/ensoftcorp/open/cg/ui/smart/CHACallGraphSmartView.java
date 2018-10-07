package com.ensoftcorp.open.cg.ui.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.java.core.script.Common;
import com.ensoftcorp.open.cg.analysis.CGAnalysis;
import com.ensoftcorp.open.cg.analysis.ClassHierarchyAnalysis;
import com.ensoftcorp.open.cg.preferences.CallGraphPreferences;
import com.ensoftcorp.open.cg.ui.log.Log; 

public class CHACallGraphSmartView extends CallGraphSmartView {

	@Override
	public String getTitle() {
		return "CHA Call Graph";
	}

	@Override
	protected Q getCallGraph() {
		CGAnalysis cgAnalysis = ClassHierarchyAnalysis.getInstance();
		if(!CallGraphPreferences.isClassHierarchyAnalysisEnabled()){
			Log.warning(cgAnalysis.getName() + " has not been run. Smart View will not contain results.");
			return Common.empty();
		}
		return cgAnalysis.getCallGraph();
	}

}
