package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.cg.analysis.ClassHierarchyAnalysis;

public class CHACallGraphSmartView extends CallGraphSmartView {

	@Override
	public String getTitle() {
		return "CHA Call Graph";
	}

	@Override
	protected Q getCallEdges() {
		Q callEdges = Common.universe().edgesTaggedWithAny(ClassHierarchyAnalysis.CALL);
		if(callEdges.eval().edges().isEmpty()){
			ClassHierarchyAnalysis cha = new ClassHierarchyAnalysis();
			cha.run();
		}
		return callEdges;
	}

}
