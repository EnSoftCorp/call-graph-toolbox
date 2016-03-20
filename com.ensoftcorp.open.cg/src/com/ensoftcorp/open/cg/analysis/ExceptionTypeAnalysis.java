package com.ensoftcorp.open.cg.analysis;

import java.util.LinkedList;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.indexing.IndexingUtil;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Attr.Node;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.cg.utils.CodeMapChangeListener;
import com.ensoftcorp.open.cg.utils.ExceptionAnalysis;
import com.ensoftcorp.open.toolbox.commons.analysis.DiscoverMainMethods;

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
	private static CodeMapChangeListener codeMapChangeListener = null;
	
	protected ExceptionTypeAnalysis(boolean libraryCallGraphConstructionEnabled) {
		// exists only to defeat instantiation
		super(libraryCallGraphConstructionEnabled);
	}
	
	public static ExceptionTypeAnalysis getInstance(boolean enableLibraryCallGraphConstruction) {
		if (instance == null || (codeMapChangeListener != null && codeMapChangeListener.hasIndexChanged())) {
			instance = new ExceptionTypeAnalysis(enableLibraryCallGraphConstruction);
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
			if(libraryCallGraphConstructionEnabled && mainMethods.isEmpty()){
				Log.warning("Application does not contain a main method, building a call graph using library assumptions.");
			}
			// if we are building a call graph for a library there is no main method...
			// so we over approximate and consider every method as a valid program entry point
			AtlasSet<GraphElement> allMethods = Common.universe().nodesTaggedWithAny(XCSG.Method).eval().nodes();
			for(GraphElement method : allMethods){
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
		
		// initially the ETA based call graph is empty
		AtlasSet<GraphElement> cgETA = new AtlasHashSet<GraphElement>();
		
		// iterate until the worklist is empty (in ETA the worklist only contains methods)
		while(!worklist.isEmpty()){
			GraphElement method = worklist.removeFirst();
			
			// our goal is to first build a set of feasible allocation types that could reach this method
			AtlasSet<GraphElement> allocationTypes = new AtlasHashSet<GraphElement>();
			
			// we should consider the allocation types instantiated directly in the method
			AtlasSet<GraphElement> methodAllocationTypes = getAllocationTypesSet(method);
			if(methodAllocationTypes.isEmpty()){
				// allocations are contained (declared) within the methods in the method reverse call graph
				Q methodDeclarations = declarations.forward(Common.toQ(method));
				Q allocations = methodDeclarations.nodesTaggedWithAny(XCSG.Instantiation);
				// collect the types of each allocation
				methodAllocationTypes.addAll(typeOfEdges.successors(allocations).eval().nodes());
			}
			allocationTypes.addAll(methodAllocationTypes);
			
			// we should also include the allocation types of each parent method (in the current ETA call graph)
			// get compatible parent allocation types
			AtlasSet<GraphElement> parentMethods = Common.toQ(cgETA).reverse(Common.toQ(method)).difference(Common.toQ(method)).eval().nodes();
			for(GraphElement parentMethod : parentMethods){
				Q parentAllocationTypes = Common.toQ(getAllocationTypesSet(parentMethod));
				// add the parameter type compatible allocation types
				allocationTypes.addAll(parentAllocationTypes.eval().nodes());
			}

			// for ETA we should inherit all allocation types from methods and their parents that 
			// throw an exception that could be caught by this method
			Q potentialCatchBlocks = declarations.forward(Common.toQ(method)).nodesTaggedWithAny(XCSG.ControlFlow_Node);
			Q throwingMethods = declarations.reverse(ExceptionAnalysis.findThrowForCatch(potentialCatchBlocks)).nodesTaggedWithAny(XCSG.Method);
			throwingMethods = throwingMethods.difference(Common.toQ(method)); // only worried about exceptions that propagate back up the stack
			for(GraphElement throwingMethod : throwingMethods.eval().nodes()){
				Q parentAllocationTypes = Common.toQ(getAllocationTypesSet(throwingMethod));
				// add the parameter type compatible allocation types
				allocationTypes.addAll(parentAllocationTypes.eval().nodes());
			}
			
			// finally if this method throws an exception we should propagate those types to all
			// methods that could potentially catch it
			Q potentialThrowBlocks = declarations.forward(Common.toQ(method)).nodesTaggedWithAny(XCSG.ControlFlow_Node);
			Q catchingMethods = declarations.reverse(ExceptionAnalysis.findCatchForThrows(potentialThrowBlocks)).nodesTaggedWithAny(XCSG.Method);
			catchingMethods = catchingMethods.difference(Common.toQ(method)); // only worried about exceptions that propagate back up the stack
			for(GraphElement catchingMethod : catchingMethods.eval().nodes()){
				if(getAllocationTypesSet(catchingMethod).addAll(allocationTypes)){
					worklist.add(catchingMethod);
				}
			}
					
			// next get a set of all the CHA call edges from the method and create an ETA edge
			// from the method to the target method in the CHA call graph if the target methods
			// type is compatible with the feasibly allocated types that would reach this method
			AtlasSet<GraphElement> callEdges = cgCHA.forwardStep(Common.toQ(method)).eval().edges();
			for(GraphElement callEdge : callEdges){
				// add static dispatches to the eta call graph
				// includes called methods marked static and constructors
				GraphElement calledMethod = callEdge.getNode(EdgeDirection.TO);
				if(calledMethod.taggedWith(Node.IS_STATIC)){
					cgETA.add(callEdge);
					if(!worklist.contains(calledMethod)){
						worklist.add(calledMethod);
					}
				} else if(calledMethod.taggedWith(XCSG.Constructor) || calledMethod.getAttr(XCSG.name).equals("<init>")){
					cgETA.add(callEdge);
					if(!worklist.contains(calledMethod)){
						worklist.add(calledMethod);
					}
				} else {
					// the call edge is a dynamic dispatch, need to resolve possible dispatches
					// a dispatch is possible if the type declaring the method is one of the 
					// allocated types (or the parent of an allocated type)
					// note: we should consider the supertype hierarchy of the allocation types
					// because methods can be inherited from parent types
					Q typeDeclaringCalledMethod = declarations.predecessors(Common.toQ(calledMethod));
					if(!typeHierarchy.forward(Common.toQ(allocationTypes)).intersection(typeDeclaringCalledMethod).eval().nodes().isEmpty()){
						cgETA.add(callEdge);
						if(!worklist.contains(calledMethod)){
							worklist.add(calledMethod);
						}
					}
				}
			}
		}
		
		// just tag each edge in the ETA call graph with "ETA" to distinguish it
		// from the CHA call graph
		Q pcfCHA = cha.getPerControlFlowGraph();
		for(GraphElement xtaEdge : cgETA){
			xtaEdge.tag(CALL);
			GraphElement callingMethod = xtaEdge.getNode(EdgeDirection.FROM);
			GraphElement calledMethod = xtaEdge.getNode(EdgeDirection.TO);
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
		return new String[]{PER_CONTROL_FLOW, ClassHierarchyAnalysis.PER_CONTROL_FLOW};
	}
	
	@Override
	public boolean graphHasEvidenceOfPreviousRun(){
		return Common.universe().edgesTaggedWithAny(CALL).eval().edges().size() > 0;
	}
	
}