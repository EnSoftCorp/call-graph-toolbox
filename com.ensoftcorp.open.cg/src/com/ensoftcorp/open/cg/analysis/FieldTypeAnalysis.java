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
 * Performs a Field Type Analysis (FTA), which is a modification
 * to FTA discussed in the paper:
 * Scalable Propagation-Based Call Graph Construction Algorithms
 * by Frank Tip and Jens Palsberg.
 * 
 * In terms of call graph construction precision this algorithm 
 * ranks better than FTA but worse than a 0-CFA.
 * 
 * Reference: http://web.cs.ucla.edu/~palsberg/paper/oopsla00.pdf
 * 
 * @author Ben Holland
 */
public class FieldTypeAnalysis extends CGAnalysis {

	public static final String CALL = "FTA-CALL";
	
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
		
		// initially the FTA based call graph is empty
		AtlasSet<GraphElement> cgFTA = new AtlasHashSet<GraphElement>();
		
		// iterate until the worklist is empty
		while(!worklist.isEmpty()){
			GraphElement method = worklist.removeFirst();
			
			// for the given method gather a list of all new allocation types
			// in the given method and its FTA based reverse call graph
			// note: since FTA call graph is empty to start be sure to include the origin method! 
			Q methodRCG = Common.toQ(cgFTA).reverse(Common.toQ(method)).union(Common.toQ(method));
			
			// allocations are contained (declared) within the methods in the method reverse call graph
			Q allocations = declarations.forward(methodRCG).nodesTaggedWithAny(XCSG.Instantiation);
			
			// collect the types of each allocation
			Q allocationTypes = typeOfEdges.successors(allocations);
			
			// get a set of all the CHA call edges from the method
			AtlasSet<GraphElement> callEdges = cgCHA.forwardStep(Common.toQ(method)).eval().edges();
			for(GraphElement callEdge : callEdges){
				// add static dispatches to the fta call graph
				// includes called methods marked static and constructors
				GraphElement calledMethod = callEdge.getNode(EdgeDirection.TO);
				if(calledMethod.taggedWith(Node.IS_STATIC)){
					cgFTA.add(callEdge);
					if(!worklist.contains(calledMethod)){
						worklist.add(calledMethod);
					}
				} else if(calledMethod.taggedWith(XCSG.Constructor)){
					cgFTA.add(callEdge);
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
					if(!allocationTypes.intersection(typeDeclaringCalledMethodSubTypes).eval().nodes().isEmpty()){
						cgFTA.add(callEdge);
						if(!worklist.contains(calledMethod)){
							worklist.add(calledMethod);
						}
					}
				}
			}
		}
		
		// just tag each edge in the FTA call graph with "FTA" to distinguish it
		// from the CHA call graph
		for(GraphElement ftaEdge : cgFTA){
			ftaEdge.tag(CALL);
		}	
	}
	
}