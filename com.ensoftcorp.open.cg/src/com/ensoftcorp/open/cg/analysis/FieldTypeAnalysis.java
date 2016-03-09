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
	
	@Override
	protected void runAnalysis() {
		AtlasSet<GraphElement> mainMethods = DiscoverMainMethods.getMainMethods().eval().nodes();
		
		// create a worklist and add the main methods
		LinkedList<GraphElement> worklist = new LinkedList<GraphElement>();
		for(GraphElement mainMethod : mainMethods){
			worklist.add(mainMethod);
		}
		
		// get the conservative call graph from CHA
		Q cgCHA = Common.universe().edgesTaggedWithAny(XCSG.Call);
		
		// initially the RTA based call graph is empty
		AtlasSet<GraphElement> cgRTA = new AtlasHashSet<GraphElement>();
		
		// iterate until the worklist is empty
		while(!worklist.isEmpty()){
			GraphElement method = worklist.removeFirst();
			
			// for the given method gather a list of all new allocation types
			// in the given method and its RTA based reverse call graph
			Q methodRCG = Common.toQ(cgRTA).reverse(Common.toQ(method));
			
			// allocations are contained (declared) within the methods in the method reverse call graph
			Q declarations = Common.universe().edgesTaggedWithAny(XCSG.Contains);
			Q allocations = declarations.forward(methodRCG).nodesTaggedWithAny(XCSG.Instantiation);
			
			// collect the types of each allocation
			Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
			AtlasSet<GraphElement> allocationTypes = typeOfEdges.successors(allocations).eval().nodes();
			
			// get a set of all the CHA call edges from the method
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
				} else if(calledMethod.taggedWith(XCSG.Constructor)){
					cgRTA.add(callEdge);
					if(!worklist.contains(calledMethod)){
						worklist.add(calledMethod);
					}
				} else {
					// the call edge is a dynamic dispatch, add it if the called
					// method's declared type is one of the allocated types
					GraphElement declaredMethodType = declarations.predecessors(Common.toQ(calledMethod)).eval().nodes().getFirst();
					if(allocationTypes.contains(declaredMethodType)){
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
	
}