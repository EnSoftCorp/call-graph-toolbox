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
import com.ensoftcorp.atlas.java.core.script.CommonQueries;
import com.ensoftcorp.open.cg.utils.DiscoverMainMethods;

/**
 * Performs a Method Type Analysis (MTA), which is a modification
 * to MTA discussed in the paper:
 * Scalable Propagation-Based Call Graph Construction Algorithms
 * by Frank Tip and Jens Palsberg.
 * 
 * In terms of call graph construction precision this algorithm 
 * ranks better than MTA but worse than a 0-CFA.
 * 
 * Reference: http://web.cs.ucla.edu/~palsberg/paper/oopsla00.pdf
 * 
 * @author Ben Holland
 */
public class MethodTypeAnalysis extends CGAnalysis {

	public static final String CALL = "MTA-CALL";
	
	@Override
	protected void runAnalysis() {
		AtlasSet<GraphElement> mainMethods = DiscoverMainMethods.getMainMethods().eval().nodes();
		Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		
		// create a worklist and add the main methods
		LinkedList<GraphElement> worklist = new LinkedList<GraphElement>();
		for(GraphElement mainMethod : mainMethods){
			worklist.add(mainMethod);
		}
		
		// get the conservative call graph from CHA
		Q cgCHA = Common.universe().edgesTaggedWithAny(XCSG.Call);
		
		// initially the MTA based call graph is empty
		AtlasSet<GraphElement> cgMTA = new AtlasHashSet<GraphElement>();
		
		// iterate until the worklist is empty
		while(!worklist.isEmpty()){
			GraphElement method = worklist.removeFirst();
			
			// get the current reverse call graph of the given method
			// note: since MTA call graph is empty to start be sure to include the origin method! 
			Q methodRCG = Common.toQ(cgMTA).reverse(Common.toQ(method)).union(Common.toQ(method));
			Q parentMethods = methodRCG.difference(Common.toQ(method));
			
			// restrict allocation types declared in parents to only the types that are compatible 
			// with the type or subtype of each of the method's parameters
			Q parameters = CommonQueries.methodParameter(Common.toQ(method));
			Q parameterTypes = typeOfEdges.successors(parameters);
			Q parameterTypeHierarchy = typeHierarchy.reverse(parameterTypes);
			
			// collect the types of each parent allocation
			Q parentAllocations = containsEdges.forward(parentMethods).nodesTaggedWithAny(XCSG.Instantiation);
			Q parentAllocationTypes = typeOfEdges.successors(parentAllocations);
			
			// remove the parent allocation types that could not be passed through the method's parameters
			parentAllocationTypes = parameterTypeHierarchy.intersection(parentAllocationTypes);
			
			// get local allocations that are made by directly instantiating something in the given method
			Q localAllocations = containsEdges.forward(Common.toQ(method)).nodesTaggedWithAny(XCSG.Instantiation);

			// add allocations that are made by calling a method (static or virtual) that return an allocation
			// note that the declared return type does not involve resolving dynamic dispatches (so this could be the
			// return type of any method resolved by a CHA analysis since all are statically typed to the same type)
			localAllocations = localAllocations.union(CommonQueries.methodReturn(cgCHA.successors(Common.toQ(method))));
			
			// get the local allocation types
			Q localAllocationTypes = typeOfEdges.successors(localAllocations);
			
			// finally the set of all allocation types that could reach this method 
			// is the set of parent allocations that can be passed through the methods parameters
			// unioned with the local allocations types (types initialized locally or returned via a callsite return value)
			Q allocationTypes = localAllocationTypes.union(parentAllocationTypes);
			
			// get a set of all the CHA call edges from the method
			AtlasSet<GraphElement> callEdges = cgCHA.forwardStep(Common.toQ(method)).eval().edges();
			for(GraphElement callEdge : callEdges){
				// add static dispatches to the mta call graph
				// includes called methods marked static and constructors
				GraphElement calledMethod = callEdge.getNode(EdgeDirection.TO);
				if(calledMethod.taggedWith(Node.IS_STATIC)){
					cgMTA.add(callEdge);
					if(!worklist.contains(calledMethod)){
						worklist.add(calledMethod);
					}
				} else if(calledMethod.taggedWith(XCSG.Constructor)){
					cgMTA.add(callEdge);
					if(!worklist.contains(calledMethod)){
						worklist.add(calledMethod);
					}
				} else {
					// the call edge is a dynamic dispatch, need to resolve possible dispatches
					// a dispatch is possible if the type declaring the method is one of the allocated types
					// note: we have to consider the subtype hierarchy of the type declaring the method
					// because methods can be inherited from parent types
					Q typeDeclaringCalledMethod = containsEdges.predecessors(Common.toQ(calledMethod));
					Q typeDeclaringCalledMethodSubTypes = typeHierarchy.reverse(typeDeclaringCalledMethod);
					if(!allocationTypes.intersection(typeDeclaringCalledMethodSubTypes).eval().nodes().isEmpty()){
						cgMTA.add(callEdge);
						if(!worklist.contains(calledMethod)){
							worklist.add(calledMethod);
						}
					}
				}
			}
		}
		
		// just tag each edge in the MTA call graph with "MTA" to distinguish it
		// from the CHA call graph
		for(GraphElement mtaEdge : cgMTA){
			mtaEdge.tag(CALL);
		}	
	}
	
}