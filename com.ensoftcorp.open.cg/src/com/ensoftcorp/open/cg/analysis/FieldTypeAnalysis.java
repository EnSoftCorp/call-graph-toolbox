package com.ensoftcorp.open.cg.analysis;

import java.util.LinkedList;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.indexing.IndexingUtil;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.cg.utils.CodeMapChangeListener;
import com.ensoftcorp.open.commons.analysis.DiscoverMainMethods;
import com.ensoftcorp.open.commons.analysis.SetDefinitions;
import com.ensoftcorp.open.commons.analysis.utils.StandardQueries;

/**
 * Performs a Field Type Analysis (FTA), which is a modification
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
public class FieldTypeAnalysis extends CGAnalysis {

	public static final String CALL = "FTA-CALL";
	public static final String PER_CONTROL_FLOW = "FTA-PER-CONTROL-FLOW";

	private static final String TYPES_SET = "FTA-TYPES";
	
	private static FieldTypeAnalysis instance = null;
	private static CodeMapChangeListener codeMapChangeListener = null;
	
	protected FieldTypeAnalysis(boolean libraryCallGraphConstructionEnabled) {
		// exists only to defeat instantiation
		super(libraryCallGraphConstructionEnabled);
	}
	
	public static FieldTypeAnalysis getInstance(boolean enableLibraryCallGraphConstruction) {
		if (instance == null || (codeMapChangeListener != null && codeMapChangeListener.hasIndexChanged())) {
			instance = new FieldTypeAnalysis(enableLibraryCallGraphConstruction);
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
		Q dataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge);
		
		// create a worklist and add the root method set
		LinkedList<Node> worklist = new LinkedList<Node>();

		AtlasSet<Node> mainMethods = DiscoverMainMethods.findMainMethods().eval().nodes();
		if(libraryCallGraphConstructionEnabled || mainMethods.isEmpty()){
			if(!libraryCallGraphConstructionEnabled && mainMethods.isEmpty()){
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
		
		// initially the FTA based call graph is empty
		AtlasSet<Node> cgFTA = new AtlasHashSet<Node>();
		
		// iterate until the worklist is empty
		// in FTA and its derivatives the worklist could contain methods or fields
		while(!worklist.isEmpty()){
			Node workitem = worklist.removeFirst();
			if(workitem.taggedWith(XCSG.Method)){
				Node method = workitem;
				
				// we should consider the allocation types instantiated directly in the method
				// note even if the allocation set is not empty here, this may be the first time
				// we've reached this method because information could have been propagated from
				// a field first
				AtlasSet<Node> allocationTypes = getAllocationTypesSet(method);
				if(allocationTypes.isEmpty()){
					// allocations are contained (declared) within the methods in the method reverse call graph
					Q methodDeclarations = declarations.forward(Common.toQ(method));
					Q allocations = methodDeclarations.nodesTaggedWithAny(XCSG.Instantiation);
					// collect the types of each allocation
					allocationTypes.addAll(typeOfEdges.successors(allocations).eval().nodes());
					allocationTypes.addAll(allocationTypes);
					
					// for RTA and RTA derivatives we should also include the allocation types of each parent method (in the current FTA call graph)
					// note: parent methods does not include the origin method
					AtlasSet<Node> parentMethods = Common.toQ(cgFTA).reverse(Common.toQ(method)).difference(Common.toQ(method)).eval().nodes();
					for(Node parentMethod : parentMethods){
						AtlasSet<Node> parentAllocationTypes = getAllocationTypesSet(parentMethod);
						allocationTypes.addAll(parentAllocationTypes);
					}
					
					// In FTA any method in the method or method's parents that reads from a field 
					// can have a reference to the allocations that occur in any another method that writes to that field
					Q reachableMethodDeclarations = declarations.forward(Common.toQ(method).union(Common.toQ(parentMethods)));
					AtlasSet<Node> readFields = dataFlowEdges.predecessors(reachableMethodDeclarations).nodesTaggedWithAny(XCSG.Field).eval().nodes();
					for(Node readField : readFields){
						AtlasSet<Node> fieldAllocationTypes = getAllocationTypesSet(readField);
						allocationTypes.addAll(fieldAllocationTypes);
					}
					
					// In FTA if the method writes to a field then all the compatible allocated types available to the method
					// can be propagated to the field
					AtlasSet<Node> writtenFields = dataFlowEdges.successors(reachableMethodDeclarations).nodesTaggedWithAny(XCSG.Field).eval().nodes();
					for(Node writtenField : writtenFields){
						AtlasSet<Node> fieldAllocationTypes = getAllocationTypesSet(writtenField);
						Q compatibleTypes = Common.toQ(allocationTypes).intersection(typeHierarchy.reverse(typeOfEdges.successors(Common.toQ(writtenField))));
						if(fieldAllocationTypes.addAll(compatibleTypes.eval().nodes())){
							if(!worklist.contains(writtenField)){
								worklist.add(writtenField);
							}	
						}
					}
				}

				// next get a set of all the CHA call edges from the method and create an FTA edge
				// from the method to the target method in the CHA call graph if the target methods
				// type is compatible with the feasibly allocated types that would reach this method
				AtlasSet<Edge> callEdges = cgCHA.forwardStep(Common.toQ(method)).eval().edges();
				for(Edge callEdge : callEdges){
					// add static dispatches to the fta call graph
					// includes called methods marked static and constructors
					Node calledMethod = callEdge.getNode(EdgeDirection.TO);
					Node callingMethod = callEdge.getNode(EdgeDirection.FROM);
					Q callingStaticDispatches = Common.toQ(callingMethod).contained().nodesTaggedWithAny(XCSG.StaticDispatchCallSite);
					boolean isStaticDispatch = !cha.getPerControlFlowGraph().predecessors(Common.toQ(calledMethod)).intersection(callingStaticDispatches).eval().nodes().isEmpty();
					if(isStaticDispatch || calledMethod.taggedWith(XCSG.Constructor) || calledMethod.getAttr(XCSG.name).equals("<init>")){
						updateCallGraph(worklist, cgFTA, method, allocationTypes, callEdge, calledMethod);
					} else {
						// the call edge is a dynamic dispatch, need to resolve possible dispatches
						// a dispatch is possible if the type declaring the method is one of the 
						// allocated types (or the parent of an allocated type)
						// note: we should consider the supertype hierarchy of the allocation types
						// because methods can be inherited from parent types
						Q typeDeclaringCalledMethod = declarations.predecessors(Common.toQ(calledMethod));
						if(!typeHierarchy.forward(Common.toQ(allocationTypes)).intersection(typeDeclaringCalledMethod).eval().nodes().isEmpty()){
							updateCallGraph(worklist, cgFTA, method, allocationTypes, callEdge, calledMethod);
						}
					}
				}
				
			} else {
				// new allocation types were propagated to a field, which means methods that read from the field may get new allocation types
				Node field = workitem;
				AtlasSet<Node> fieldAllocationTypes = getAllocationTypesSet(field);
				AtlasSet<Node> readingMethods = StandardQueries.getContainingMethods(dataFlowEdges.successors(Common.toQ(field))).eval().nodes();
				for(Node readingMethod : readingMethods){
					AtlasSet<Node> readingMethodAllocationTypes = getAllocationTypesSet(readingMethod);
					if(readingMethodAllocationTypes.addAll(fieldAllocationTypes)){
						if(!worklist.contains(readingMethod)){
							worklist.add(readingMethod);
						}
					}
				}
			}
		}
		
		// just tag each edge in the FTA call graph with "FTA" to distinguish it
		// from the CHA call graph
		Q pcfCHA = cha.getPerControlFlowGraph();
		for(Node xtaEdge : cgFTA){
			xtaEdge.tag(CALL);
			Node callingMethod = xtaEdge.getNode(EdgeDirection.FROM);
			Node calledMethod = xtaEdge.getNode(EdgeDirection.TO);
			Q callsites = declarations.forward(Common.toQ(callingMethod)).nodesTaggedWithAny(XCSG.CallSite);
			for(Edge perControlFlowEdge : pcfCHA.betweenStep(callsites, Common.toQ(calledMethod)).eval().edges()){
				perControlFlowEdge.tag(PER_CONTROL_FLOW);
			}
		}	
	}
	
	/**
	 * Updates the call graph and worklist for methods and fields
	 * @param worklist
	 * @param cgFTA
	 * @param method
	 * @param allocationTypes
	 * @param callEdge
	 * @param calledMethod
	 */
	public static void updateCallGraph(LinkedList<Node> worklist, AtlasSet<Node> cgFTA, Node method, AtlasSet<Node> allocationTypes, Edge callEdge, Node calledMethod) {
		if(Common.toQ(cgFTA).betweenStep(Common.toQ(method), Common.toQ(calledMethod)).eval().edges().isEmpty()){
			cgFTA.add(callEdge);
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
		
		// update fields types
		Q dataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge);
		Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		AtlasSet<Node> writtenFields = dataFlowEdges.successors(Common.toQ(calledMethod)).nodesTaggedWithAny(XCSG.Field).eval().nodes();
		for(Node writtenField : writtenFields){
			AtlasSet<Node> fieldAllocationTypes = getAllocationTypesSet(writtenField);
			Q compatibleTypes = Common.toQ(allocationTypes).intersection(typeHierarchy.reverse(typeOfEdges.successors(Common.toQ(writtenField))));
			if(fieldAllocationTypes.addAll(compatibleTypes.eval().nodes())){
				if(!worklist.contains(writtenField)){
					worklist.add(writtenField);
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
	public boolean graphHasEvidenceOfPreviousRun(){
		return Common.universe().edgesTaggedWithAny(CALL).eval().edges().size() > 0;
	}
}