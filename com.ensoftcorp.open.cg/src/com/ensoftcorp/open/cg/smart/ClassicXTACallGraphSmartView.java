package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.ClassHierarchyAnalysis;
import com.ensoftcorp.open.cg.analysis.ClassicHybridTypeAnalysis;
import com.ensoftcorp.open.cg.log.Log; 

public class ClassicXTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "XTA (Classic) Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		return ClassicHybridTypeAnalysis.getInstance(enableCallGraphConstruction).getCallGraph();
	}

}
