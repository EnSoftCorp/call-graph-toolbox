package com.ensoftcorp.open.cg.analysis;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Attr.Node;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.cg.utils.CallGraphConstruction;

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
	public static final String LIBRARY_CALL = "RA-LIBRARY-CALL";
	
	@Override
	protected void runAnalysis(boolean libraryCallGraphConstructionEnabled) {
		Q declarations = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q invokedFunctionEdges = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction);
		
		// for each method
		AtlasSet<GraphElement> methods = Common.universe().nodesTaggedWithAny(XCSG.Method).eval().nodes();
		for(GraphElement method : methods){
			// for each callsite
			AtlasSet<GraphElement> callsites = declarations.forward(Common.toQ(method)).nodesTaggedWithAny(XCSG.CallSite).eval().nodes();
			for(GraphElement callsite : callsites){
				if(callsite.taggedWith(XCSG.StaticDispatchCallSite)){
					// static dispatches (calls to constructors or methods marked as static) can be resolved immediately
					GraphElement targetMethod = invokedFunctionEdges.successors(Common.toQ(callsite)).eval().nodes().getFirst();
					CallGraphConstruction.createCallEdge(method, targetMethod, CALL);
				} else if(callsite.taggedWith(XCSG.DynamicDispatchCallSite)){
					// dynamic dispatches require additional analysis to be resolved
					
					// in RA we just say if the method signature being called matches a method then add a call edge
					AtlasSet<GraphElement> reachableMethods = getReachableMethods(callsite).eval().nodes();
					
					// create a call edge from the method to each matching method
					for(GraphElement reachableMethod : reachableMethods){
						// dispatches cannot happen to abstract methods
						if(reachableMethod.taggedWith(XCSG.abstractMethod)){
							if(libraryCallGraphConstructionEnabled){
								// if library call graph construction is enabled then we will consider adding a special edge type
								// in the case that the method signature was abstract since we may not have been able to resolve any
								// dispatch targets (in the case the method is not implemented in the library) and since we cannot know 
								// that the application won't re-implement the method anyway (unless it was marked final)
								CallGraphConstruction.createLibraryCallEdge(method, reachableMethod, LIBRARY_CALL);
							}
						} else {
							CallGraphConstruction.createCallEdge(method, reachableMethod, CALL);
						}
					}
				}
			}
		}
	}
	
	@Override
	public boolean graphHasEvidenceOfPreviousRun() {
		return Common.universe().edgesTaggedWithAny(CALL, LIBRARY_CALL).eval().edges().size() > 0;
	}
	
	/**
	 * Returns a set of reachable methods (methods with the matching signature of the callsite)
	 * Note: This method specifically includes abstract methods
	 * @param callsite
	 * @return
	 */
	public static Q getReachableMethods(GraphElement callsite){
		// first create a set of candidate methods to select from
		// note: specifically including abstract methods so we can use them later for library construction
		Q candidateMethods = Common.universe().nodesTaggedWithAny(XCSG.Method)
				.difference(Common.universe().nodesTaggedWithAny(XCSG.Constructor, Node.IS_STATIC));
		// then match the method name
		String methodName = callsite.getAttr(XCSG.name).toString();
		methodName = methodName.substring(0, methodName.indexOf("("));
		Q matchingMethods = candidateMethods.selectNode(XCSG.name, methodName);
		
		// TODO: then match the parameter count
		
		// TODO: then match the parameter types
		
		// TODO: then match return type
		
		return matchingMethods;
	}

}
