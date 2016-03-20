package com.ensoftcorp.open.cg.common;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.java.core.script.Common;
import com.ensoftcorp.open.cg.analysis.CGAnalysis;
import com.ensoftcorp.open.cg.analysis.ClassHierarchyAnalysis;
import com.ensoftcorp.open.cg.analysis.ClassicHybridTypeAnalysis;
import com.ensoftcorp.open.cg.analysis.ExceptionTypeAnalysis;
import com.ensoftcorp.open.cg.analysis.FieldTypeAnalysis;
import com.ensoftcorp.open.cg.analysis.HybridTypeAnalysis;
import com.ensoftcorp.open.cg.analysis.MethodTypeAnalysis;
import com.ensoftcorp.open.cg.analysis.RapidTypeAnalysis;
import com.ensoftcorp.open.cg.analysis.ReachabilityAnalysis;
import com.ensoftcorp.open.cg.analysis.ZeroControlFlowAnalysis;
import com.ensoftcorp.open.cg.ui.CallGraphPreferences;

public class Stats {

	public static void dumpStats(File outputFile) throws IOException {
		FileWriter fw = new FileWriter(outputFile);
		fw.write("Algorithm,Nodes,Edges,# Callsites,# Static Dispatches,# Dynamic Dispatches,Max Dynamic Dispatch Targets Per Callsite,Min Dynamic Dispatch Targets Per Callsite,Average Dynamic Dispatch Targets Per Callsite\n");
		
		boolean enableCallGraphConstruction = CallGraphPreferences.isLibraryCallGraphConstructionEnabled();
		
		ReachabilityAnalysis ra = ReachabilityAnalysis.getInstance(enableCallGraphConstruction);
		dumpStats(ra, fw);
		
		ClassHierarchyAnalysis cha = ClassHierarchyAnalysis.getInstance(enableCallGraphConstruction);
		dumpStats(cha, fw);
		
		RapidTypeAnalysis rta = RapidTypeAnalysis.getInstance(enableCallGraphConstruction);
		dumpStats(rta, fw);
		
		FieldTypeAnalysis fta = FieldTypeAnalysis.getInstance(enableCallGraphConstruction);
		dumpStats(fta, fw);
		
		MethodTypeAnalysis mta = MethodTypeAnalysis.getInstance(enableCallGraphConstruction);
		dumpStats(mta, fw);
		
		ExceptionTypeAnalysis eta = ExceptionTypeAnalysis.getInstance(enableCallGraphConstruction);
		dumpStats(eta, fw);
		
		ClassicHybridTypeAnalysis cxta = ClassicHybridTypeAnalysis.getInstance(enableCallGraphConstruction);
		dumpStats(cxta, fw);
		
		HybridTypeAnalysis xta = HybridTypeAnalysis.getInstance(enableCallGraphConstruction);
		dumpStats(xta, fw);
		
		ZeroControlFlowAnalysis zcfa = ZeroControlFlowAnalysis.getInstance(enableCallGraphConstruction);
		dumpStats(zcfa, fw);
		
		fw.close();
	}
	
	// how many nodes/edges in call graph 
	// number of virtual callsites (list their locations)
	// max/average/min number of callees linked to each virtual call site
	private static void dumpStats(CGAnalysis cga, FileWriter fw) throws IOException {
		Q cg = cga.getCallGraph();
		Graph cgGraph = cg.eval();
		AtlasSet<GraphElement> callsites = Common.universe().nodesTaggedWithAny(XCSG.CallSite).eval().nodes();
		
		fw.write(cga.getClass().getSimpleName()
				+ "," + cgGraph.nodes().size() + "," + cgGraph.edges().size() 
				+ "," + callsites.size() + "," + getStaticDispatches(callsites).size() + "," + getDynamicDispatches(callsites).size()
				+ "," + getMaxDynamicDispatchesPerCallsite(callsites, cga) 
				+ "," + getMinDynamicDispatchesPerCallsite(callsites, cga) 
				+ "," + getAverageDynamicDispatchesPerCallsite(callsites, cga) + "\n");
	}

	private static AtlasSet<GraphElement> getStaticDispatches(AtlasSet<GraphElement> callsites){
		return Common.toQ(callsites).nodesTaggedWithAny(XCSG.StaticDispatchCallSite).eval().nodes();
	}
	
	private static AtlasSet<GraphElement> getDynamicDispatches(AtlasSet<GraphElement> callsites){
		return Common.toQ(callsites).nodesTaggedWithAny(XCSG.DynamicDispatchCallSite).eval().nodes();
	}
	
	private static Long getMaxDynamicDispatchesPerCallsite(AtlasSet<GraphElement> callsites, CGAnalysis cga){
		long max = Long.MIN_VALUE;
		for(GraphElement callsite : getDynamicDispatches(callsites)){
			long dispatches = cga.getPerControlFlowGraph().successors(Common.toQ(callsite)).eval().nodes().size();
			if(dispatches > max){
				max = dispatches;
			}
		}
		return max;
	}
	
	private static Long getMinDynamicDispatchesPerCallsite(AtlasSet<GraphElement> callsites, CGAnalysis cga){
		long min = Long.MAX_VALUE;
		for(GraphElement callsite : getDynamicDispatches(callsites)){
			long dispatches = cga.getPerControlFlowGraph().successors(Common.toQ(callsite)).eval().nodes().size();
			if(dispatches < min){
				min = dispatches;
			}
		}
		return min;
	}
	
	private static Double getAverageDynamicDispatchesPerCallsite(AtlasSet<GraphElement> callsites, CGAnalysis cga){
		double average = 0;
		callsites = getDynamicDispatches(callsites);
		for(GraphElement callsite : callsites){
			long dispatches = cga.getPerControlFlowGraph().successors(Common.toQ(callsite)).eval().nodes().size();
			average += dispatches;
		}
		return ((double) average / (double) callsites.size());
	}
}
