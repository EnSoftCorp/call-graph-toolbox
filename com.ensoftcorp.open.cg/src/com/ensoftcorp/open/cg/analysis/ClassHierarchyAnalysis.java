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
 * This analysis builds a call graph using Class Hierarchy Analysis (CHA).
 * 
 * In terms of precision, this is the least precise analysis compared to RTA, 
 * VTA, and 0-CFA, but is also the cheapest analysis of the available algorithms.
 * Several other algorithms (except 0-CFA) use CHA as a starting input for their analyses.
 * 
 * Note: Atlas already has an implementation of CHA baked-in, so its pointless
 * to do this analysis, except for the intellectual exercise of doing so.
 * 
 * @author Ben Holland
 */
public class ClassHierarchyAnalysis extends CGAnalysis {

	public static final String CALL = "CHA-CALL"; 
	
	@Override
	protected void runAnalysis() {
		Q declarations = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		Q invokedFunctionEdges = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction);
		Q identityPassedToEdges = Common.universe().edgesTaggedWithAny(XCSG.IdentityPassedTo);
		Q dataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge);
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
					// first get the declared type of the receiver object
					Q thisNode = identityPassedToEdges.predecessors(Common.toQ(callsite));
					Q receiverObjects = dataFlowEdges.predecessors(thisNode);
					Q receiverObjectTypes = typeOfEdges.successors(receiverObjects);
					// then extend those types to subtypes in the type hierarchy
					receiverObjectTypes = typeHierarchy.reverse(receiverObjectTypes);
					// finally match each method signature in the type hierarchy to called method
					GraphElement methodSignature = methodSignatureGraph.edges(callsite, NodeDirection.OUT).getFirst().getNode(EdgeDirection.TO);
					AtlasSet<GraphElement> resolvedDispatches = CommonQueries.dynamicDispatch(receiverObjectTypes, Common.toQ(methodSignature)).eval().nodes();
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