package com.ensoftcorp.open.cg.analysis;

import java.util.LinkedList;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.indexing.IndexingUtil;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.java.core.script.CommonQueries;
import com.ensoftcorp.open.cg.utils.CodeMapChangeListener;
import com.ensoftcorp.open.toolbox.commons.SetDefinitions;
import com.ensoftcorp.open.toolbox.commons.analysis.DiscoverMainMethods;

/**
 * Performs a Method Type Analysis (MTA), which is a modification
 * to RTA discussed in the paper:
 * Scalable Propagation-Based Call Graph Construction Algorithms
 * by Frank Tip and Jens Palsberg.
 * 
 * In terms of call graph construction precision this algorithm 
 * ranks better than RTA but worse than a 0-CFA.
 * 
 * Reference: http://web.cs.ucla.edu/~palsberg/paper/oopsla00.pdf
 * 
 * @author Ben Holland
 */
public class MethodTypeAnalysis extends CGAnalysis {

	public static final String CALL = "MTA-CALL";
	public static final String PER_CONTROL_FLOW = "MTA-PER-CONTROL-FLOW";
	
	private static final String TYPES_SET = "MTA-TYPES";
	
	private static MethodTypeAnalysis instance = null;
	private static CodeMapChangeListener codeMapChangeListener = null;
	
	protected MethodTypeAnalysis(boolean libraryCallGraphConstructionEnabled) {
		// exists only to defeat instantiation
		super(libraryCallGraphConstructionEnabled);
	}
	
	public static MethodTypeAnalysis getInstance(boolean enableLibraryCallGraphConstruction) {
		if (instance == null || (codeMapChangeListener != null && codeMapChangeListener.hasIndexChanged())) {
			instance = new MethodTypeAnalysis(enableLibraryCallGraphConstruction);
			if(codeMapChangeListener == null){
				codeMapChangeListener = new CodeMapChangeListener();
				IndexingUtil.addListener(codeMapChangeListener);
			} else {
				codeMapChangeListener.reset();
			}
		}
		return instance;
	}
	
	@Override
	protected void runAnalysis() {
		// first get the conservative call graph from CHA
		// for library calls, RTA uses CHA library call edges because assuming every that every type could be allocated
		// outside of the method and passed into the library is just an expensive way to end back up at CHA
		ClassHierarchyAnalysis cha = ClassHierarchyAnalysis.getInstance(libraryCallGraphConstructionEnabled);
		if(libraryCallGraphConstructionEnabled && cha.isLibraryCallGraphConstructionEnabled() != libraryCallGraphConstructionEnabled){
			Log.warning("ClassHierarchyAnalysis was run without library call edges enabled, "
					+ "the resulting call graph will be missing the LIBRARY-CALL edges.");
		}
		Q cgCHA = cha.getCallGraph();
		
		// next create some subgraphs to work with
		Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		Q declarations = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		
		// create a worklist and add the root method set
		LinkedList<GraphElement> worklist = new LinkedList<GraphElement>();

		AtlasSet<GraphElement> mainMethods = DiscoverMainMethods.findMainMethods().eval().nodes();
		if(libraryCallGraphConstructionEnabled || mainMethods.isEmpty()){
			if(!libraryCallGraphConstructionEnabled && mainMethods.isEmpty()){
				Log.warning("Application does not contain a main method, building a call graph using library assumptions.");
			}
			// if we are building a call graph for a library there is no main method...
			// a nice balance is to start with all public methods in the library
			AtlasSet<GraphElement> rootMethods = SetDefinitions.app().nodesTaggedWithAll(XCSG.publicVisibility, XCSG.Method).eval().nodes();
			for(GraphElement method : rootMethods){
				worklist.add(method);
			}
		} else {
			// under normal circumstances this algorithm would be given a single main method
			// but end users don't tend to think about this so consider any valid main method
			// as a program entry point
			if(mainMethods.size() > 1){
				Log.warning("Application contains multiple main methods. The call graph may contain unexpected conservative edges as a result.");
			}
			for(GraphElement mainMethod : mainMethods){
				worklist.add(mainMethod);
			}
		}
		
		// initially the MTA based call graph is empty
		AtlasSet<GraphElement> cgMTA = new AtlasHashSet<GraphElement>();
		
		// iterate until the worklist is empty (in MTA the worklist only contains methods)
		while(!worklist.isEmpty()){
			GraphElement method = worklist.removeFirst();
			
			// our goal is to first build a set of feasible allocation types that could reach this method
			AtlasSet<GraphElement> allocationTypes = new AtlasHashSet<GraphElement>();
			
			// we should consider the allocation types instantiated directly in the method
			if(allocationTypes.isEmpty()){
				// allocations are contained (declared) within the methods in the method reverse call graph
				Q methodDeclarations = declarations.forward(Common.toQ(method));
				Q allocations = methodDeclarations.nodesTaggedWithAny(XCSG.Instantiation);
				// collect the types of each allocation
				allocationTypes.addAll(typeOfEdges.successors(allocations).eval().nodes());
				
				// we should also include the allocation types of each parent method (in the current MTA call graph)
				// but we should only allow compatible parent allocation types which could be passed through the method's parameter types or subtypes
				// restrict allocation types declared in parents to only the types that are compatible 
				// with the type or subtype of each of the method's parameters
				Q parameters = CommonQueries.methodParameter(Common.toQ(method));
				Q parameterTypes = typeOfEdges.successors(parameters);
				Q parameterTypeHierarchy = typeHierarchy.reverse(parameterTypes);
				// get compatible parent allocation types
				AtlasSet<GraphElement> parentMethods = Common.toQ(cgMTA).reverse(Common.toQ(method)).difference(Common.toQ(method)).eval().nodes();
				for(GraphElement parentMethod : parentMethods){
					Q parentAllocationTypes = Common.toQ(getAllocationTypesSet(parentMethod));
					// remove the parent allocation types that could not be passed through the method's parameters
					parentAllocationTypes = parameterTypeHierarchy.intersection(parentAllocationTypes);
					// add the parameter type compatible allocation types
					allocationTypes.addAll(parentAllocationTypes.eval().nodes());
				}
				
				// finally MTA considers the return types of methods that are called from the given method
				// add allocations that are made by calling a method (static or virtual) that return an allocation
				// note that the declared return type does not involve resolving dynamic dispatches (so this could be the
				// return type of any method resolved by a CHA analysis since all are statically typed to the same type)
				Q returnTypes = typeOfEdges.successors(CommonQueries.methodReturn(cgCHA.successors(Common.toQ(method))));
				allocationTypes.addAll(returnTypes.eval().nodes());
			}

			// next get a set of all the CHA call edges from the method and create an MTA edge
			// from the method to the target method in the CHA call graph if the target methods
			// type is compatible with the feasibly allocated types that would reach this method
			AtlasSet<GraphElement> callEdges = cgCHA.forwardStep(Common.toQ(method)).eval().edges();
			for(GraphElement callEdge : callEdges){
				// add static dispatches to the mta call graph
				// includes called methods marked static and constructors
				GraphElement calledMethod = callEdge.getNode(EdgeDirection.TO);
				boolean isStaticDispatch = !cha.getPerControlFlowGraph().predecessors(Common.toQ(calledMethod)).nodesTaggedWithAny(XCSG.StaticDispatchCallSite).eval().nodes().isEmpty();
				if(isStaticDispatch || calledMethod.taggedWith(XCSG.Constructor) || calledMethod.getAttr(XCSG.name).equals("<init>")){
					RapidTypeAnalysis.updateCallGraph(worklist, cgMTA, method, allocationTypes, callEdge, calledMethod);
				} else {
					// the call edge is a dynamic dispatch, need to resolve possible dispatches
					// a dispatch is possible if the type declaring the method is one of the 
					// allocated types (or the parent of an allocated type)
					// note: we should consider the supertype hierarchy of the allocation types
					// because methods can be inherited from parent types
					Q typeDeclaringCalledMethod = declarations.predecessors(Common.toQ(calledMethod));
					if(!typeHierarchy.forward(Common.toQ(allocationTypes)).intersection(typeDeclaringCalledMethod).eval().nodes().isEmpty()){
						RapidTypeAnalysis.updateCallGraph(worklist, cgMTA, method, allocationTypes, callEdge, calledMethod);
					}
				}
			}
		}
		
		// just tag each edge in the MTA call graph with "MTA" to distinguish it
		// from the CHA call graph
		Q pcfCHA = cha.getPerControlFlowGraph();
		for(GraphElement mtaEdge : cgMTA){
			mtaEdge.tag(CALL);
			GraphElement callingMethod = mtaEdge.getNode(EdgeDirection.FROM);
			GraphElement calledMethod = mtaEdge.getNode(EdgeDirection.TO);
			Q callsites = declarations.forward(Common.toQ(callingMethod)).nodesTaggedWithAny(XCSG.CallSite);
			for(GraphElement perControlFlowEdge : pcfCHA.betweenStep(callsites, Common.toQ(calledMethod)).eval().edges()){
				perControlFlowEdge.tag(PER_CONTROL_FLOW);
			}
		}	
	}
	
	/**
	 * Gets or creates the types set for a graph element
	 * Returns a reference to the types set so that updates to the 
	 * set will also update the set on the graph element.
	 * @param ge
	 * @return 
	 */
	@SuppressWarnings("unchecked")
	private static AtlasSet<GraphElement> getAllocationTypesSet(GraphElement ge){
		if(ge.hasAttr(TYPES_SET)){
			return (AtlasSet<GraphElement>) ge.getAttr(TYPES_SET);
		} else {
			AtlasSet<GraphElement> types = new AtlasHashSet<GraphElement>();
			ge.putAttr(TYPES_SET, types);
			return types;
		}
	}
	
	@Override
	public String[] getCallEdgeTags() {
		return new String[]{CALL, ClassHierarchyAnalysis.LIBRARY_CALL};
	}
	
	@Override
	public String[] getPerControlFlowEdgeTags() {
		return new String[]{PER_CONTROL_FLOW, ClassHierarchyAnalysis.LIBRARY_PER_CONTROL_FLOW};
	}
	
	@Override
	public boolean graphHasEvidenceOfPreviousRun(){
		return Common.universe().edgesTaggedWithAny(CALL).eval().edges().size() > 0;
	}
}