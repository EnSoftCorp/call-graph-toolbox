package com.ensoftcorp.open.cg.analysis;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Attr;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.CommonQueries;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.cg.utils.CallGraphConstruction;
import com.ensoftcorp.open.java.commons.wishful.JavaStopGap;

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
	public static final String PER_CONTROL_FLOW = "RA-PER-CONTROL-FLOW"; 
	public static final String LIBRARY_CALL = "RA-LIBRARY-CALL";
	public static final String LIBRARY_PER_CONTROL_FLOW = "RA-LIBRARY-PER-CONTROL-FLOW"; 
	
	private static ReachabilityAnalysis instance = null;
	
	protected ReachabilityAnalysis(boolean libraryCallGraphConstructionEnabled) {
		// exists only to defeat instantiation
		super(libraryCallGraphConstructionEnabled);
	}
	
	public static ReachabilityAnalysis getInstance(boolean enableLibraryCallGraphConstruction) {
		if (instance == null) {
			instance = new ReachabilityAnalysis(enableLibraryCallGraphConstruction);
		}
		return instance;
	}
	
	@Override
	protected void runAnalysis() {
		Q declarations = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q invokedFunctionEdges = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction);
		Q allTypes = Common.universe().nodesTaggedWithAny(XCSG.Type);
		
		// for each method
		AtlasSet<Node> methods = Common.universe().nodesTaggedWithAny(XCSG.Method).eval().nodes();
		for(Node method : methods){
			// for each callsite
			AtlasSet<Node> callsites = declarations.forward(Common.toQ(method)).nodesTaggedWithAny(XCSG.CallSite).eval().nodes();
			for(Node callsite : callsites){
				if(callsite.taggedWith(XCSG.StaticDispatchCallSite)){
					// static dispatches (calls to constructors or methods marked as static) can be resolved immediately
					Node targetMethod = invokedFunctionEdges.successors(Common.toQ(callsite)).eval().nodes().getFirst();
					CallGraphConstruction.createCallEdge(callsite, method, targetMethod, CALL, PER_CONTROL_FLOW);
				} else if(callsite.taggedWith(XCSG.DynamicDispatchCallSite)){
					// dynamic dispatches require additional analysis to be resolved
					
					// in RA we just say if the method signature being called matches a method then add a call edge
					AtlasSet<Node> reachableMethods = getReachableMethods(callsite, allTypes);
					
					// create a call edge from the method to each matching method
					for(Node reachableMethod : reachableMethods){
						// dispatches cannot happen to abstract methods
						if(reachableMethod.taggedWith(XCSG.abstractMethod)){
							if(libraryCallGraphConstructionEnabled){
								// if library call graph construction is enabled then we will consider adding a special edge type
								// in the case that the method signature was abstract since we may not have been able to resolve any
								// dispatch targets (in the case the method is not implemented in the library) and since we cannot know 
								// that the application won't re-implement the method anyway (unless it was marked final)
								CallGraphConstruction.createCallEdge(callsite, method, reachableMethod, LIBRARY_CALL, LIBRARY_PER_CONTROL_FLOW);
							}
						} else {
							CallGraphConstruction.createCallEdge(callsite, method, reachableMethod, CALL, PER_CONTROL_FLOW);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Returns a set of reachable methods (methods with the matching signature of the callsite)
	 * Note: This method specifically includes abstract methods
	 * 
	 * @param callsite
	 * @return
	 */
	public static AtlasSet<Node> getReachableMethods(Node callsite, Q typesToSearch){
		Q methodSignatureEdges = Common.universe().edgesTaggedWithAny(XCSG.InvokedSignature);
		Node methodSignature = methodSignatureEdges.successors(Common.toQ(callsite)).eval().nodes().getFirst();
		String signature = methodSignature.getAttr(JavaStopGap.SIGNATURE).toString();
		
		Q matchingMethods = Common.universe().selectNode(JavaStopGap.SIGNATURE, signature);
		
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q candidateMethods = containsEdges.forwardStep(typesToSearch).nodesTaggedWithAny(XCSG.Method)
				.difference(Common.universe().nodesTaggedWithAny(XCSG.Constructor, Attr.Node.IS_STATIC));
		
		return new AtlasHashSet<Node>(matchingMethods.intersection(candidateMethods).eval().nodes());
	}
	
	/**
	 * Returns a set of reachable methods (methods with the matching signature of the callsite)
	 * Note: This method specifically includes abstract methods
	 * 
	 * Note: This implementation uses a very verbose (and expensive) implementation of signature matching in Atlas
	 * It is included here as an example of how it could be done. For this particular purpose it is better to use
	 * the Atlas signature attribute instead.
	 * 
	 * @param callsite
	 * @return
	 */
	public static AtlasSet<Node> getReachableMethods_AlternateImplementation(Node callsite, Q typesToSearch){
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
		Q returnsEdges = Common.universe().edgesTaggedWithAny(XCSG.Returns);
		Q methodSignatureEdges = Common.universe().edgesTaggedWithAny(XCSG.InvokedSignature);
		Q parameterPassToEdges = Common.universe().edgesTaggedWithAny(XCSG.ParameterPassedTo);
		
		// first create a set of candidate methods to select from
		// note: specifically including abstract methods so we can use them later for library construction
		Q candidateMethods = containsEdges.forwardStep(typesToSearch).nodesTaggedWithAny(XCSG.Method)
				.difference(Common.universe().nodesTaggedWithAny(XCSG.Constructor, Attr.Node.IS_STATIC));
		
		// match the method name
		String methodName = callsite.getAttr(XCSG.name).toString();
		methodName = methodName.substring(0, methodName.indexOf("("));
		Q matchingMethods = candidateMethods.selectNode(XCSG.name, methodName);
		
		// match the return type
		Q methodSignature = methodSignatureEdges.successors(Common.toQ(callsite));
		Q returnType = returnsEdges.successors(methodSignature);
		matchingMethods = returnsEdges.predecessors(returnType).intersection(matchingMethods);
		
		// get the callsite parameters to match
		AtlasSet<Node> passedParameters = parameterPassToEdges.predecessors(Common.toQ(callsite)).eval().nodes();

		AtlasSet<Node> result = new AtlasHashSet<Node>();
		
		// at least the method signature is reachable
		result.add(methodSignature.eval().nodes().getFirst()); 
		
		// filter out methods that do not take the exact same number and type of parameters
		for(Node matchingMethod : matchingMethods.eval().nodes()){
			// check if the number of parameters passed is the same size as the number of parameters expected
			AtlasSet<Node> candidateMethodParameters = CommonQueries.methodParameter(Common.toQ(matchingMethod)).eval().nodes();
			if(passedParameters.size() == candidateMethodParameters.size()){
				// check that each parameter passed type is compatible with the expected parameter type
				boolean paramsMatch = true;
				for(int i=0; i<passedParameters.size(); i++){
					Node passedParameter = Common.toQ(passedParameters).selectNode(XCSG.parameterIndex, i).eval().nodes().getFirst();
					Node candidateMethodParameter = Common.toQ(candidateMethodParameters).selectNode(XCSG.parameterIndex, i).eval().nodes().getFirst();
					Q passedParameterType = typeOfEdges.successors(Common.toQ(passedParameter));
					Q methodParameterType = typeOfEdges.successors(Common.toQ(candidateMethodParameter));
					// passed parameter types can be subtypes of the parameter's declared types
					Q methodParameterSubtypes = typeHierarchy.reverse(methodParameterType); 
					if(passedParameterType.intersection(methodParameterSubtypes).eval().nodes().isEmpty()){
						paramsMatch = false;
						break;
					}
				}
				if(paramsMatch){
					result.add(matchingMethod);
				}
			}
		}
		
		return result;
	}

	@Override
	public String[] getCallEdgeTags() {
		return new String[]{CALL, LIBRARY_CALL};
	}
	
	@Override
	public String[] getPerControlFlowEdgeTags() {
		return new String[]{PER_CONTROL_FLOW, LIBRARY_PER_CONTROL_FLOW};
	}
	
	@Override
	public boolean graphHasEvidenceOfPreviousRun(){
		return Common.universe().edgesTaggedWithAny(CALL).eval().edges().size() > 0;
	}
}
