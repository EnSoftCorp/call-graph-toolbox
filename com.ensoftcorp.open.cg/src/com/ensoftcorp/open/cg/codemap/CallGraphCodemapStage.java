package com.ensoftcorp.open.cg.codemap;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ensoftcorp.open.commons.codemap.PrioritizedCodemapStage;
import com.ensoftcorp.open.pointsto.codemap.PointsToCodemapStage;

public class CallGraphCodemapStage extends PrioritizedCodemapStage {

	public static final String IDENTIFIER = "com.ensoftcorp.open.cg";
	
	@Override
	public String getDisplayName() {
		return "Call Graph Generation Analysis";
	}

	@Override
	public String getIdentifier() {
		return IDENTIFIER;
	}

	@Override
	public String[] getCodemapStageDependencies() {
		return new String[]{ PointsToCodemapStage.IDENTIFIER };
	}

	@Override
	public void performIndexing(IProgressMonitor monitor) {
		// TODO Auto-generated method stub
		
	}

}
