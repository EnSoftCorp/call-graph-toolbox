package com.ensoftcorp.open.cg.analysis;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.java.core.script.CommonQueries;

/**
 * This is about the simplest call graph we can make (dumber than a CHA).
 * 
 * It just matches the method signature of the callsite with all methods 
 * that have the same signature.  In terms of matching method signatures,
 * this at least makes sure the matched method is a virtual method and
 * the method name and parameter types/count match, but simpler implementations
 * exist that only match the name of the method (regardless if its static/dynamic
 * or has different parameter types/count).
 * 
 * @author Ben Holland
 */
public class ReachabilityAnalysis extends CGAnalysis {

	public static final String CALL = "RA-CALL"; 
	
	@Override
	protected void runAnalysis() {
		Q declarations = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
		Q allTypes = typeHierarchy.reverse(Common.typeSelect("java.lang", "Object"));
		Q invokedFunctionEdges = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction);
		Graph methodSignatureGraph = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction, XCSG.InvokedSignature).eval();
		AtlasSet<GraphElement> methods = Common.universe().nodesTaggedWithAny(XCSG.Method).eval().nodes();
		
		// for each method
		for(GraphElement method : methods){
			// for each callsite
			AtlasSet<GraphElement> callsites = declarations.forward(Common.toQ(method)).nodesTaggedWithAny(XCSG.CallSite).eval().nodes();
			for(GraphElement callsite : callsites){
				if(callsite.taggedWith(XCSG.StaticDispatchCallSite)){
					// static dispatches (calls to constructors or methods marked as static) can be resolved immediately
					GraphElement targetMethod = invokedFunctionEdges.successors(Common.toQ(callsite)).eval().nodes().getFirst();
					createCallEdge(method, targetMethod);
				} else if(callsite.taggedWith(XCSG.DynamicDispatchCallSite)){
					// dynamic dispatches require additional analysis to be resolved
					// in RA we just say if the method signature being called matches 
					// a method then add a call edge
					GraphElement methodSignature = methodSignatureGraph.edges(callsite, NodeDirection.OUT).getFirst().getNode(EdgeDirection.TO);
					AtlasSet<GraphElement> resolvedDispatches = CommonQueries.dynamicDispatch(allTypes, Common.toQ(methodSignature)).eval().nodes();
					for(GraphElement resolvedDispatch : resolvedDispatches){
						createCallEdge(method, resolvedDispatch);
					}
				}
			}
		}
	}

	/**
	 * Creates a CALL relationship between the method and the target method if one does not already exist
	 * 
	 * @param method
	 * @param targetMethod
	 * @return
	 */
	private void createCallEdge(GraphElement method, GraphElement targetMethod) {
		Q callEdges = Common.universe().edgesTaggedWithAny(CALL);
		if(callEdges.betweenStep(Common.toQ(method), Common.toQ(targetMethod)).eval().edges().isEmpty()){
			GraphElement callEdge = Graph.U.createEdge(method, targetMethod);
			callEdge.tag(CALL);
			callEdge.attr().put(XCSG.name, "call");
		}
	}

}
