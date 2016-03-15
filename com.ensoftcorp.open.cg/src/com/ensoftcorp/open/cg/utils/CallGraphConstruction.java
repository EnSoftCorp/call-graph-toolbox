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
	public static GraphElement createCallEdge(GraphElement method, GraphElement targetMethod, String relationship) {
		return createRelationship(method, targetMethod, relationship, "call");
	}
	
	/**
	 * Creates a LIBRARY-CALL relationship between the method and the target method if one does not already exist
	 * 
	 * @param method
	 * @param targetMethod
	 * @return
	 */
	public static GraphElement createLibraryCallEdge(GraphElement method, GraphElement targetMethod, String relationship) {
		return createRelationship(method, targetMethod, relationship, "library-call");
	}
	
	private static GraphElement createRelationship(GraphElement method, GraphElement targetMethod, String relationship, String displayName) {
		Q callEdgesQ = Common.universe().edgesTaggedWithAny(relationship);
		AtlasSet<GraphElement> callEdges = callEdgesQ.betweenStep(Common.toQ(method), Common.toQ(targetMethod)).eval().edges();
		if(callEdges.isEmpty()){
			GraphElement callEdge = Graph.U.createEdge(method, targetMethod);
			callEdge.tag(relationship);
			callEdge.attr().put(XCSG.name, displayName);
			return callEdge;
		} else {
			return callEdges.getFirst();
		}
	}
	
}
