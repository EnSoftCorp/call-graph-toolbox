package com.ensoftcorp.open.cg.utils;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
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
		AtlasSet<Edge> callEdges = callEdgesQ.betweenStep(Common.toQ(method), Common.toQ(targetMethod)).eval().edges();
		if(callEdges.isEmpty()){
			GraphElement callEdge = Graph.U.createEdge(method, targetMethod);
			callEdge.tag(methodRelationship);
			callEdge.attr().put(XCSG.name, displayName);
		}
		
		Q perControlFlowEdgesQ = Common.universe().edgesTaggedWithAny(callsiteRelationship);
		AtlasSet<Edge> perControlFlowEdges = perControlFlowEdgesQ.betweenStep(Common.toQ(callsite), Common.toQ(targetMethod)).eval().edges();
		if(perControlFlowEdges.isEmpty()){
			GraphElement perCFEdge = Graph.U.createEdge(callsite, targetMethod);
			perCFEdge.tag(callsiteRelationship);
		}
	}
	
	/**
	 * Tags and existing call edge and per control flow edge
	 * @param cgCHA
	 * @param pcfCHA
	 * @param callsite
	 * @param method
	 * @param targetMethod
	 * @param CALL
	 * @param PER_CONTROL_FLOW
	 */
	public static void tagExistingCallEdge(Q cgCHA, Q pcfCHA, Node callsite, Node method, Node targetMethod, String CALL, String PER_CONTROL_FLOW) {
		for(Edge callEdge : cgCHA.between(Common.toQ(method), Common.toQ(targetMethod)).eval().edges()){
			callEdge.tag(CALL);
		}
		for(Edge perControlFlowEdge : cgCHA.between(Common.toQ(callsite), Common.toQ(targetMethod)).eval().edges()){
			perControlFlowEdge.tag(PER_CONTROL_FLOW);
		}
	}
	
}
