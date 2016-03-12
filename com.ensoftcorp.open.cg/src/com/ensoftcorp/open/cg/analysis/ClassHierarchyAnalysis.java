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
	
	private Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
	private Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
	private Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
	private Q invokedFunctionEdges = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction);
	private Q identityPassedToEdges = Common.universe().edgesTaggedWithAny(XCSG.IdentityPassedTo);
	private Q dataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge);
	private Q allTypes = typeHierarchy.reverse(Common.typeSelect("java.lang", "Object"));
	private Graph methodSignatureGraph = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction, XCSG.InvokedSignature).eval();
	private AtlasSet<GraphElement> methods = Common.universe().nodesTaggedWithAny(XCSG.Method).eval().nodes();
	
	@Override
	protected void runAnalysis() {
		// for each method
		for(GraphElement method : methods){
			// for each callsite
			AtlasSet<GraphElement> callsites = containsEdges.forward(Common.toQ(method)).nodesTaggedWithAny(XCSG.CallSite).eval().nodes();
			for(GraphElement callsite : callsites){
				if(callsite.taggedWith(XCSG.StaticDispatchCallSite)){
					// static dispatches (calls to constructors or methods marked as static) can be resolved immediately
					GraphElement targetMethod = invokedFunctionEdges.successors(Common.toQ(callsite)).eval().nodes().getFirst();
					createCallEdge(method, targetMethod);
				} else if(callsite.taggedWith(XCSG.DynamicDispatchCallSite)){
					// dynamic dispatches require additional analysis to be resolved
					// first get a set of all the target method's that match the callsite signature (just like RA)
					GraphElement methodSignature = methodSignatureGraph.edges(callsite, NodeDirection.OUT).getFirst().getNode(EdgeDirection.TO);
					Q allTargetMethods = CommonQueries.dynamicDispatch(allTypes, Common.toQ(methodSignature));
	
					// then get the declared type of the receiver object
					Q thisNode = identityPassedToEdges.predecessors(Common.toQ(callsite));
					Q receiverObject = dataFlowEdges.predecessors(thisNode);
					Q declaredType = typeOfEdges.successors(receiverObject);
					
					// Since the dispatch was called on the "declared" type there must be at least one signature
					// (abstract or concrete) in the descendant path (the direct path from Object to the declared path)
					// the nearest method definition is the method definition closest to the declared type (including
					// the declared type itself) while traversing from declared type to Object on the descendant path
					Q nearestMatchingMethodDefinition = getNearestMatchingMethodDefinition(allTargetMethods, declaredType);
					Q resolvedDispatches = nearestMatchingMethodDefinition;
					
					// subtypes of the declared type can override the nearest target method definition, 
					// so make sure to include all the subtype method definitions
					Q declaredTypeChildren = typeHierarchy.reverse(typeHierarchy.predecessors(declaredType));
					resolvedDispatches = resolvedDispatches.union(containsEdges.forward(declaredTypeChildren).intersection(allTargetMethods));

					// finally if a method is abstract, then its children must override it, so we can just remove all 
					// abstract methods from the graph (this might come into play if nearest matching method definition was abstract)
					// note: its possible for a method to be re-abstracted by a subtype after its been made concrete
					resolvedDispatches = resolvedDispatches.difference(Common.universe().nodesTaggedWithAny(XCSG.abstractMethod));

					// finally match each method signature in the type hierarchy to called method
					for(GraphElement resolvedDispatch : resolvedDispatches.eval().nodes()){
						createCallEdge(method, resolvedDispatch);
					}
				}
			}
		}
	}

	public Q getNearestMatchingMethodDefinition(Q allTargetMethods, Q declaredType) {
		// it is possible that the subtypes of the declared type are inheriting the target method definition (not overriding 
		// the supertype's definition) so, starting at the declared type, walk backwards one step at a time along the direct 
		// descendant path (which runs from Object to the declared type) and search for the last (nearest) matching method  
		// target (since this is definition that would be inherited).
		Graph descendantPath = typeHierarchy.forward(declaredType).eval();
		GraphElement nearestMethodDefinition = null;
		GraphElement parentType = declaredType.eval().nodes().getFirst();
		while(parentType != null){
			AtlasSet<GraphElement> nearestMethodDefinitions = allTargetMethods.intersection(containsEdges.forward(Common.toQ(parentType))).eval().nodes();
			if(!nearestMethodDefinitions.isEmpty()){
				nearestMethodDefinition = nearestMethodDefinitions.getFirst();
				break;
			}
			// keep searching...
			parentType = descendantPath.edges(parentType, NodeDirection.OUT).getFirst().getNode(EdgeDirection.TO);
		}
		Q result = Common.empty();
		if(nearestMethodDefinition != null){
			result = Common.toQ(nearestMethodDefinition);
		}
		return result;
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