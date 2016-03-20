package com.ensoftcorp.open.cg.utils;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

public class CallGraphConstruction {

	/**
	 * Creates a CALL relationship between the method and the target method if one does not already exist
	 * 
	 * @param method
	 * @param targetMethod
	 * @return
	 */
	public static void createCallEdge(GraphElement callsite, GraphElement method, GraphElement targetMethod, String methodRelationship, String callsiteRelationship) {
		createRelationship(callsite, method, targetMethod, methodRelationship, callsiteRelationship, "call");
	}
	
	/**
	 * Creates a LIBRARY-CALL relationship between the method and the target method if one does not already exist
	 * 
	 * @param method
	 * @param targetMethod
	 * @return
	 */
	public static void createLibraryCallEdge(GraphElement callsite, GraphElement method, GraphElement targetMethod, String methodRelationship, String callsiteRelationship) {
		createRelationship(callsite, method, targetMethod, methodRelationship, callsiteRelationship, "library-call");
	}
	
	private static void createRelationship(GraphElement callsite, GraphElement method, GraphElement targetMethod, String methodRelationship, String callsiteRelationship, String displayName) {
		Q callEdgesQ = Common.universe().edgesTaggedWithAny(methodRelationship);
		AtlasSet<GraphElement> callEdges = callEdgesQ.betweenStep(Common.toQ(method), Common.toQ(targetMethod)).eval().edges();
		if(callEdges.isEmpty()){
			GraphElement callEdge = Graph.U.createEdge(method, targetMethod);
			callEdge.tag(methodRelationship);
			callEdge.attr().put(XCSG.name, displayName);
		}
		
		Q perControlFlowEdgesQ = Common.universe().edgesTaggedWithAny(callsiteRelationship);
		AtlasSet<GraphElement> perControlFlowEdges = perControlFlowEdgesQ.betweenStep(Common.toQ(callsite), Common.toQ(targetMethod)).eval().edges();
		if(perControlFlowEdges.isEmpty()){
			GraphElement perCFEdge = Graph.U.createEdge(callsite, targetMethod);
			perCFEdge.tag(callsiteRelationship);
		}
	}
	
}
