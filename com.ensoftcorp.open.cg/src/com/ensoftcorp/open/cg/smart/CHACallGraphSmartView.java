package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.open.cg.analysis.ClassHierarchyAnalysis;

public class CHACallGraphSmartView extends CallGraphSmartView {

	@Override
	public String getTitle() {
		return "CHA Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		ClassHierarchyAnalysis cha = ClassHierarchyAnalysis.getInstance(enableCallGraphConstruction);
		return cha.getCallGraph();
	}

}
