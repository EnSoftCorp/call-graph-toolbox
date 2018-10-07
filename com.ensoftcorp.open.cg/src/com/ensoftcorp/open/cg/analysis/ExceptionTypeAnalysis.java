package com.ensoftcorp.open.cg.analysis;

import java.util.LinkedList;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.indexing.IndexingUtil;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.cg.log.Log;
import com.ensoftcorp.open.cg.preferences.CallGraphPreferences;
import com.ensoftcorp.open.commons.analysis.SetDefinitions;
import com.ensoftcorp.open.commons.utilities.CodeMapChangeListener;
import com.ensoftcorp.open.java.commons.analysis.ThrowableAnalysis;
import com.ensoftcorp.open.java.commons.analyzers.JavaProgramEntryPoints;

/**
 * Performs an Exception Type Analysis (ETA), which is a modification
 * to RTA that considers inter-procedural exceptional flows.
 * 
 * In terms of call graph construction precision this algorithm 
 * ranks better than RTA but worse than a 0-CFA.
 * 
 * Reference: http://web.cs.ucla.edu/~palsberg/paper/oopsla00.pdf
 * 
 * @author Ben Holland
 */
public class ExceptionTypeAnalysis extends CGAnalysis {

	public static final String CALL = "ETA-CALL";
	public static final String PER_CONTROL_FLOW = "ETA-PER-CONTROL-FLOW";
	
	private static final String TYPES_SET = "ETA-TYPES";
	
	private static ExceptionTypeAnalysis instance = null;
	
	protected ExceptionTypeAnalysis() {
		// exists only to defeat instantiation
	}
	
	private static CodeMapChangeListener codeMapChangeListener = null;
	
	public static ExceptionTypeAnalysis getInstance() {
		if (instance == null || (codeMapChangeListener != null && codeMapChangeListener.hasIndexChanged())) {
			instance = new ExceptionTypeAnalysis();
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
		ClassHierarchyAnalysis cha = ClassHierarchyAnalysis.getInstance();
		Q cgCHA = cha.getCallGraph();
		
		// next create some subgraphs to work with
		Q typeHierarchy = Query.universe().edges(XCSG.Supertype);
		Q typeOfEdges = Query.universe().edges(XCSG.TypeOf);
		Q declarations = Query.universe().edges(XCSG.Contains);
		
		// create a worklist and add the root method set
		LinkedList<Node> worklist = new LinkedList<Node>();

		AtlasSet<Node> mainMethods = JavaProgramEntryPoints.findMainMethods().eval().nodes();
		if(CallGraphPreferences.isLibraryCallGraphConstructionEnabled() || mainMethods.isEmpty()){
			if(!CallGraphPreferences.isLibraryCallGraphConstructionEnabled() && mainMethods.isEmpty()){
				Log.warning("Application does not contain a main method, building a call graph using library assumptions.");
			}
			// if we are building a call graph for a library there is no main method...
			// a nice balance is to start with all public methods in the library
			AtlasSet<Node> rootMethods = SetDefinitions.app().nodesTaggedWithAll(XCSG.publicVisibility, XCSG.Method).eval().nodes();
			for(Node method : rootMethods){
				worklist.add(method);
			}
		} else {
			// under normal circumstances this algorithm would be given a single main method
			// but end users don't tend to think about this so consider any valid main method
			// as a program entry point
			if(mainMethods.size() > 1){
				Log.warning("Application contains multiple main methods. The call graph may contain unexpected conservative edges as a result.");
			}
			for(Node mainMethod : mainMethods){
				worklist.add(mainMethod);
			}
		}
		
		// initially the ETA based call graph is empty
		AtlasSet<Edge> cgETA = new AtlasHashSet<Edge>();
		
		// iterate until the worklist is empty (in ETA the worklist only contains methods)
		while(!worklist.isEmpty()){
			Node method = worklist.removeFirst();
			
			// we should consider the allocation types instantiated directly in the method
			AtlasSet<Node> allocationTypes = getAllocationTypesSet(method);
			if(allocationTypes.isEmpty()){
				// allocations are contained (declared) within the methods in the method reverse call graph
				Q methodDeclarations = declarations.forward(Common.toQ(method));
				Q allocations = methodDeclarations.nodes(XCSG.Instantiation);
				// collect the types of each allocation
				allocationTypes.addAll(typeOfEdges.successors(allocations).eval().nodes());
				
				// we should also include the allocation types of each parent method (in the current ETA call graph)
				// get compatible parent allocation types
				AtlasSet<Node> parentMethods = Common.toQ(cgETA).reverse(Common.toQ(method)).difference(Common.toQ(method)).eval().nodes();
				for(Node parentMethod : parentMethods){
					Q parentAllocationTypes = Common.toQ(getAllocationTypesSet(parentMethod));
					// add the parameter type compatible allocation types
					allocationTypes.addAll(parentAllocationTypes.eval().nodes());
				}
			}
			
			// for ETA we should inherit all allocation types from methods and their parents that 
			// throw an exception that could be caught by this method
			Q potentialCatchBlocks = declarations.forward(Common.toQ(method)).nodes(XCSG.ControlFlow_Node);
			Q throwingMethods = declarations.reverse(ThrowableAnalysis.findThrowForCatch(potentialCatchBlocks)).nodes(XCSG.Method);
			throwingMethods = throwingMethods.difference(Common.toQ(method)); // only worried about exceptions that propagate back up the stack
			for(Node throwingMethod : throwingMethods.eval().nodes()){
				Q throwerAllocationTypes = Common.toQ(getAllocationTypesSet(throwingMethod));
				// add the parameter type compatible allocation types
				allocationTypes.addAll(throwerAllocationTypes.eval().nodes());
			}
			
			// finally if this method throws an exception we should propagate those types to all
			// methods that could potentially catch it
			Q potentialThrowBlocks = declarations.forward(Common.toQ(method)).nodes(XCSG.ControlFlow_Node);
			Q catchingMethods = declarations.reverse(ThrowableAnalysis.findCatchForThrows(potentialThrowBlocks)).nodes(XCSG.Method);
			catchingMethods = catchingMethods.difference(Common.toQ(method)); // only worried about exceptions that propagate back up the stack
			for(Node catchingMethod : catchingMethods.eval().nodes()){
				if(getAllocationTypesSet(catchingMethod).addAll(allocationTypes)){
					if(!worklist.contains(catchingMethod)){
						worklist.add(catchingMethod);
					}
				}
			}
			
			// next get a set of all the CHA call edges from the method and create an ETA edge
			// from the method to the target method in the CHA call graph if the target methods
			// type is compatible with the feasibly allocated types that would reach this method
			AtlasSet<Edge> callEdges = cgCHA.forwardStep(Common.toQ(method)).eval().edges();
			for(Edge callEdge : callEdges){
				// add static dispatches to the eta call graph
				// includes called methods marked static and constructors
				Node calledMethod = callEdge.getNode(EdgeDirection.TO);
				Node callingMethod = callEdge.getNode(EdgeDirection.FROM);
				Q callingStaticDispatches = Common.toQ(callingMethod).contained().nodes(XCSG.StaticDispatchCallSite);
				boolean isStaticDispatch = !cha.getPerControlFlowGraph().predecessors(Common.toQ(calledMethod)).intersection(callingStaticDispatches).eval().nodes().isEmpty();
				if(isStaticDispatch || calledMethod.taggedWith(XCSG.Constructor) || calledMethod.getAttr(XCSG.name).equals("<init>")){
					updateCallGraph(worklist, cgETA, method, allocationTypes, callEdge, calledMethod);
				} else {
					// the call edge is a dynamic dispatch, need to resolve possible dispatches
					// a dispatch is possible if the type declaring the method is one of the 
					// allocated types (or the parent of an allocated type)
					// note: we should consider the supertype hierarchy of the allocation types
					// because methods can be inherited from parent types
					Q typeDeclaringCalledMethod = declarations.predecessors(Common.toQ(calledMethod));
					if(!typeHierarchy.forward(Common.toQ(allocationTypes)).intersection(typeDeclaringCalledMethod).eval().nodes().isEmpty()){
						updateCallGraph(worklist, cgETA, method, allocationTypes, callEdge, calledMethod);
					}
				}
			}
		}
		
		// just tag each edge in the ETA call graph with "ETA" to distinguish it
		// from the CHA call graph
		Q pcfCHA = cha.getPerControlFlowGraph();
		for(Edge xtaEdge : cgETA){
			xtaEdge.tag(CALL);
			Node callingMethod = xtaEdge.getNode(EdgeDirection.FROM);
			Node calledMethod = xtaEdge.getNode(EdgeDirection.TO);
			Q callsites = declarations.forward(Common.toQ(callingMethod)).nodes(XCSG.CallSite);
			for(Edge perControlFlowEdge : pcfCHA.betweenStep(callsites, Common.toQ(calledMethod)).eval().edges()){
				perControlFlowEdge.tag(PER_CONTROL_FLOW);
			}
		}	
	}
	
	/**
	 * Updates the call graph and worklist for methods
	 * @param worklist
	 * @param cgRTA
	 * @param method
	 * @param allocationTypes
	 * @param callEdge
	 * @param calledMethod
	 */
	private static void updateCallGraph(LinkedList<Node> worklist, AtlasSet<Edge> cgRTA, Node method, AtlasSet<Node> allocationTypes, Edge callEdge, Node calledMethod) {
		if(Common.toQ(cgRTA).betweenStep(Common.toQ(method), Common.toQ(calledMethod)).eval().edges().isEmpty()){
			cgRTA.add(callEdge);
			if(!worklist.contains(calledMethod)){
				worklist.add(calledMethod);
			}
		} else {
			AtlasSet<Node> toAllocationTypes = getAllocationTypesSet(calledMethod);
			if(toAllocationTypes.addAll(allocationTypes)){
				if(!worklist.contains(calledMethod)){
					worklist.add(calledMethod);
				}
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
	private static AtlasSet<Node> getAllocationTypesSet(Node ge){
		if(ge.hasAttr(TYPES_SET)){
			return (AtlasSet<Node>) ge.getAttr(TYPES_SET);
		} else {
			AtlasSet<Node> types = new AtlasHashSet<Node>();
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
	public String getName() {
		return "Exception Type Analysis";
	}
}