package com.ensoftcorp.open.cg.analysis;

import java.util.LinkedList;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.cg.log.Log;
import com.ensoftcorp.open.commons.analysis.SetDefinitions;
import com.ensoftcorp.open.java.commons.analysis.CommonQueries;
import com.ensoftcorp.open.java.commons.analysis.ThrowableAnalysis;
import com.ensoftcorp.open.java.commons.analyzers.JavaProgramEntryPoints;

/**
 * Performs a Hybrid Type Analysis (XTA), which is a modification
 * to RTA that combines XTA and MTA as discussed in the paper:
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
public class HybridTypeAnalysis extends CGAnalysis {

	public static final String CALL = "XTA-CALL";
	public static final String PER_CONTROL_FLOW = "XTA-PER-CONTROL-FLOW";

	private static final String TYPES_SET = "XTA-TYPES";
	
	private static HybridTypeAnalysis instance = null;
	
	protected HybridTypeAnalysis(boolean libraryCallGraphConstructionEnabled) {
		// exists only to defeat instantiation
		super(libraryCallGraphConstructionEnabled);
	}
	
	public static HybridTypeAnalysis getInstance(boolean enableLibraryCallGraphConstruction) {
		if (instance == null) {
			instance = new HybridTypeAnalysis(enableLibraryCallGraphConstruction);
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

		AtlasSet<Node> mainMethods = JavaProgramEntryPoints.findMainMethods().eval().nodes();
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
		
		// initially the XTA based call graph is empty
		AtlasSet<Edge> cgXTA = new AtlasHashSet<Edge>();
		
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
					
					// we should also include the allocation types of each parent method (in the current MTA call graph)
					// but we should only allow compatible parent allocation types which could be passed through the method's parameter types or subtypes
					// restrict allocation types declared in parents to only the types that are compatible 
					// with the type or subtype of each of the method's parameters
					Q parameters = CommonQueries.methodParameter(Common.toQ(method));
					Q parameterTypes = typeOfEdges.successors(parameters);
					Q parameterTypeHierarchy = typeHierarchy.reverse(parameterTypes);
					// get compatible parent allocation types
					AtlasSet<Node> parentMethods = Common.toQ(cgXTA).reverse(Common.toQ(method)).difference(Common.toQ(method)).eval().nodes();
					for(Node parentMethod : parentMethods){
						Q parentAllocationTypes = Common.toQ(getAllocationTypesSet(parentMethod));
						// remove the parent allocation types that could not be passed through the method's parameters
						parentAllocationTypes = parameterTypeHierarchy.intersection(parentAllocationTypes);
						// add the parameter type compatible allocation types
						allocationTypes.addAll(parentAllocationTypes.eval().nodes());
					}
					
					// MTA also considers the return types of methods that are called from the given method
					// add allocations that are made by calling a method (static or virtual) that return an allocation
					// note that the declared return type does not involve resolving dynamic dispatches (so this could be the
					// return type of any method resolved by a CHA analysis since all are statically typed to the same type)
					Q returnTypes = typeOfEdges.successors(CommonQueries.methodReturn(cgCHA.successors(Common.toQ(method))));
					allocationTypes.addAll(returnTypes.eval().nodes());
					
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
				
				// for ETA we should inherit all allocation types from methods and their parents that 
				// throw an exception that could be caught by this method
				Q potentialCatchBlocks = declarations.forward(Common.toQ(method)).nodesTaggedWithAny(XCSG.ControlFlow_Node);
				Q throwingMethods = declarations.reverse(ThrowableAnalysis.findThrowForCatch(potentialCatchBlocks)).nodesTaggedWithAny(XCSG.Method);
				throwingMethods = throwingMethods.difference(Common.toQ(method)); // only worried about exceptions that propagate back up the stack
				for(Node throwingMethod : throwingMethods.eval().nodes()){
					Q throwerAllocationTypes = Common.toQ(getAllocationTypesSet(throwingMethod));
					// add the parameter type compatible allocation types
					allocationTypes.addAll(throwerAllocationTypes.eval().nodes());
				}
				
				// finally if this method throws an exception we should propagate those types to all
				// methods that could potentially catch it
				Q potentialThrowBlocks = declarations.forward(Common.toQ(method)).nodesTaggedWithAny(XCSG.ControlFlow_Node);
				Q catchingMethods = declarations.reverse(ThrowableAnalysis.findCatchForThrows(potentialThrowBlocks)).nodesTaggedWithAny(XCSG.Method);
				catchingMethods = catchingMethods.difference(Common.toQ(method)); // only worried about exceptions that propagate back up the stack
				for(Node catchingMethod : catchingMethods.eval().nodes()){
					if(getAllocationTypesSet(catchingMethod).addAll(allocationTypes)){
						if(!worklist.contains(catchingMethod)){
							worklist.add(catchingMethod);
						}
					}
				}

				// next get a set of all the CHA call edges from the method and create an XTA edge
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
						FieldTypeAnalysis.updateCallGraph(worklist, cgXTA, method, allocationTypes, callEdge, calledMethod);
					} else {
						// the call edge is a dynamic dispatch, need to resolve possible dispatches
						// a dispatch is possible if the type declaring the method is one of the 
						// allocated types (or the parent of an allocated type)
						// note: we should consider the supertype hierarchy of the allocation types
						// because methods can be inherited from parent types
						Q typeDeclaringCalledMethod = declarations.predecessors(Common.toQ(calledMethod));
						if(!typeHierarchy.forward(Common.toQ(allocationTypes)).intersection(typeDeclaringCalledMethod).eval().nodes().isEmpty()){
							FieldTypeAnalysis.updateCallGraph(worklist, cgXTA, method, allocationTypes, callEdge, calledMethod);
						}
					}
				}
				
			} else {
				// new allocation types were propagated to a field, which means methods that read from the field may get new allocation types
				Node field = workitem;
				AtlasSet<Node> fieldAllocationTypes = getAllocationTypesSet(field);
				AtlasSet<Node> readingMethods = CommonQueries.getContainingMethods(dataFlowEdges.successors(Common.toQ(field))).eval().nodes();
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
		
		// just tag each edge in the XTA call graph with "XTA" to distinguish it
		// from the CHA call graph
		Q pcfCHA = cha.getPerControlFlowGraph();
		for(Edge xtaEdge : cgXTA){
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
	 * Gets or creates the types set for a graph element
	 * Returns a reference to the types set so that updates to the 
	 * set will also update the set on the graph element.
	 * @param ge
	 * @return 
	 */
	@SuppressWarnings("unchecked")
	private static AtlasSet<Node> getAllocationTypesSet(Node node){
		if(node.hasAttr(TYPES_SET)){
			return (AtlasSet<Node>) node.getAttr(TYPES_SET);
		} else {
			AtlasSet<Node> types = new AtlasHashSet<Node>();
			node.putAttr(TYPES_SET, types);
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