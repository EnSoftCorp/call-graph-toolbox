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
import com.ensoftcorp.open.toolbox.commons.analysis.DiscoverMainMethods;

/**
 * Performs a Rapid Type Analysis (RTA)
 * 
 * In terms of call graph construction precision this algorithm 
 * ranks better than CHA.
 * 
 * Reference: https://courses.cs.washington.edu/courses/cse501/04wi/papers/bacon-oopsla96.pdf
 * 
 * @author Ben Holland
 */
public class RapidTypeAnalysis extends CGAnalysis {

	public static final String CALL = "RTA-CALL";
	public static final String PER_CONTROL_FLOW = "RTA-PER-CONTROL-FLOW";
	
	private static final String TYPES_SET = "RTA-TYPES";
	
	private static RapidTypeAnalysis instance = null;
	private static CodeMapChangeListener codeMapChangeListener = null;
	
	protected RapidTypeAnalysis(boolean libraryCallGraphConstructionEnabled) {
		// exists only to defeat instantiation
		super(libraryCallGraphConstructionEnabled);
	}
	
	public static RapidTypeAnalysis getInstance(boolean enableLibraryCallGraphConstruction) {
		if (instance == null || (codeMapChangeListener != null && codeMapChangeListener.hasIndexChanged())) {
			instance = new RapidTypeAnalysis(enableLibraryCallGraphConstruction);
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
		
		// initially the RTA based call graph is empty
		AtlasSet<GraphElement> cgRTA = new AtlasHashSet<GraphElement>();
		
		// iterate until the worklist is empty (in RTA the worklist only contains methods)
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
			
			// we should also include the allocation types of each parent method (in the current RTA call graph)
			// note: parent methods does not include the origin method
			AtlasSet<GraphElement> parentMethods = Common.toQ(cgRTA).reverse(Common.toQ(method)).difference(Common.toQ(method)).eval().nodes();
			for(GraphElement parentMethod : parentMethods){
				AtlasSet<GraphElement> parentAllocationTypes = getAllocationTypesSet(parentMethod);
				allocationTypes.addAll(parentAllocationTypes);
			}

			// next get a set of all the CHA call edges from the method and create an RTA edge
			// from the method to the target method in the CHA call graph if the target methods
			// type is compatible with the feasibly allocated types that would reach this method
			AtlasSet<GraphElement> callEdges = cgCHA.forwardStep(Common.toQ(method)).eval().edges();
			for(GraphElement callEdge : callEdges){
				// add static dispatches to the rta call graph
				// includes called methods marked static and constructors
				GraphElement calledMethod = callEdge.getNode(EdgeDirection.TO);
				if(calledMethod.taggedWith(Node.IS_STATIC)){
					cgRTA.add(callEdge);
					if(!worklist.contains(calledMethod)){
						worklist.add(calledMethod);
					}
				} else if(calledMethod.taggedWith(XCSG.Constructor) || calledMethod.getAttr(XCSG.name).equals("<init>")){
					cgRTA.add(callEdge);
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
						cgRTA.add(callEdge);
						if(!worklist.contains(calledMethod)){
							worklist.add(calledMethod);
						}
					}
				}
			}
		}
		
		// just tag each edge in the RTA call graph with "RTA" to distinguish it
		// from the CHA call graph
		Q pcfCHA = cha.getPerControlFlowGraph();
		for(GraphElement rtaEdge : cgRTA){
			rtaEdge.tag(CALL);
			GraphElement callingMethod = rtaEdge.getNode(EdgeDirection.FROM);
			GraphElement calledMethod = rtaEdge.getNode(EdgeDirection.TO);
			Q controlFlowNodes = declarations.forward(Common.toQ(callingMethod)).nodesTaggedWithAny(XCSG.ControlFlow_Node);
			for(GraphElement perControlFlowEdge : pcfCHA.betweenStep(controlFlowNodes, Common.toQ(calledMethod)).eval().edges()){
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
	
}