package com.ensoftcorp.open.cg.analysis;

import java.util.LinkedList;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Attr.Node;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.cg.utils.DiscoverMainMethods;

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

	private static final String TYPES_SET = "RTA-TYPES";
	
	@Override
	protected void runAnalysis() {
		AtlasSet<GraphElement> mainMethods = DiscoverMainMethods.getMainMethods().eval().nodes();
		Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		Q declarations = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		
		// create a worklist and add the main methods
		LinkedList<GraphElement> worklist = new LinkedList<GraphElement>();
		for(GraphElement mainMethod : mainMethods){
			worklist.add(mainMethod);
		}
		
		// get the conservative call graph from CHA
		Q cgCHA = Common.universe().edgesTaggedWithAny(XCSG.Call);
		
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
					// a dispatch is possible if the type declaring the method is one of the allocated types
					// note: we have to consider the subtype hierarchy of the type declaring the method
					// because methods can be inherited from parent types
					Q typeDeclaringCalledMethod = declarations.predecessors(Common.toQ(calledMethod));
					Q typeDeclaringCalledMethodSubTypes = typeHierarchy.reverse(typeDeclaringCalledMethod);
					if(!Common.toQ(allocationTypes).intersection(typeDeclaringCalledMethodSubTypes).eval().nodes().isEmpty()){
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
		for(GraphElement rtaEdge : cgRTA){
			rtaEdge.tag(CALL);
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
	
}