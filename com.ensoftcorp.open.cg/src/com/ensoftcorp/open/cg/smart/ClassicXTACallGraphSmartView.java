package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.ClassicHybridTypeAnalysis;

public class ClassicXTACallGraphSmartView extends CallGraphSmartView {
	
	@Override
	public String getTitle() {
		return "XTA (Classic) Call Graph";
	}

	@Override
	protected Q getCallGraph(boolean enableCallGraphConstruction) {
		ClassicHybridTypeAnalysis cxta = ClassicHybridTypeAnalysis.getInstance(enableCallGraphConstruction);
		if(!cxta.hasRun()){
			cxta.run();
		}
		return cxta.getCallGraph();
	}

}
