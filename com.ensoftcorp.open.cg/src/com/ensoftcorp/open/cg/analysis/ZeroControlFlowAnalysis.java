package com.ensoftcorp.open.cg.analysis;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.indexing.IndexingUtil;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Attr.Edge;
import com.ensoftcorp.atlas.core.query.Attr.Node;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.cg.utils.CodeMapChangeListener;
import com.ensoftcorp.open.pointsto.common.PointsToResults;

/**
 * Performs a 0-CFA Andersen style points-to analysis
 * to resolve runtime types and infer dynamic dispatches
 * on the fly. Of CHA, RTA, and VTA this is the most precise
 * analysis for call graph construction.
 * 
 * Note: The CFA expands to Control Flow Analysis, but the
 * name no longer really captures what the algorithm is
 * doing under the hood.  
 * 
 * Reference: http://matt.might.net/articles/implementation-of-kcfa-and-0cfa/
 * 
 * @author Ben Holland
 */
public class ZeroControlFlowAnalysis extends CGAnalysis {

	public static final String CALL = "0-CFA-CALL";
	public static final String PER_CONTROL_FLOW = "0-CFA-PER-CONTROL-FLOW";
	
	private static ZeroControlFlowAnalysis instance = null;
	private static CodeMapChangeListener codeMapChangeListener = null;
	
	protected ZeroControlFlowAnalysis(boolean libraryCallGraphConstructionEnabled) {
		// exists only to defeat instantiation
		super(libraryCallGraphConstructionEnabled);
	}
	
	public static ZeroControlFlowAnalysis getInstance(boolean enableLibraryCallGraphConstruction) {
		if (instance == null || (codeMapChangeListener != null && codeMapChangeListener.hasIndexChanged())) {
			instance = new ZeroControlFlowAnalysis(enableLibraryCallGraphConstruction);
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
		// class loader issues are preventing us from calling this directly
//		if(!PointsToPreferences.isJimplePointsToAnalysisEnabled()){
//			throw new RuntimeException("Points-to analysis has not been run!");
//		}
		
		// so just looking for know tags instead...
		if(!Common.universe().edgesTaggedWithAny("INFERRED").eval().edges().isEmpty()){
			throw new RuntimeException("Points-to analysis has not been run!");
		}
		
		// library call edges are conservatively covered by a CHA, otherwise CHA is not used by k-CFA
		if(libraryCallGraphConstructionEnabled){
			ClassHierarchyAnalysis cha = ClassHierarchyAnalysis.getInstance(libraryCallGraphConstructionEnabled);
			if(cha.isLibraryCallGraphConstructionEnabled() != libraryCallGraphConstructionEnabled){
				Log.warning("ClassHierarchyAnalysis was run without library call edges enabled, "
						+ "the resulting call graph will be missing the LIBRARY-CALL edges.");
			} else {
				if(!cha.hasRun()){
					cha.run();
				}
			}
		}
		
		// import the statically resolved methods from CHA
		AtlasSet<GraphElement> callEdges = Common.universe().edgesTaggedWithAny(XCSG.Call).eval().edges();
		Q perControlFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.ControlFlow_Edge);
		Q declarations = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		for(GraphElement callEdge : callEdges){
			// add static dispatches to the call graph
			// includes called methods marked static and constructors
			GraphElement calledMethod = callEdge.getNode(EdgeDirection.TO);
			if(calledMethod.taggedWith(Node.IS_STATIC) || calledMethod.taggedWith(XCSG.Constructor) || calledMethod.getAttr(XCSG.name).equals("<init>")){
				callEdge.tag(CALL);
				
				GraphElement callingMethod = callEdge.getNode(EdgeDirection.FROM);
				Q callsites = declarations.forward(Common.toQ(callingMethod)).nodesTaggedWithAny(XCSG.CallSite);
				for(GraphElement perControlFlowEdge : perControlFlowEdges.betweenStep(callsites, Common.toQ(calledMethod)).eval().edges()){
					perControlFlowEdge.tag(PER_CONTROL_FLOW);
				}
			}
		}

		// the points-to analysis just infers data flow edges
		// but the call edges are really just a summary of the 
		// data flow edges, so we can extract the call relationships
		// retroactively out of the data flow graph
		Graph dfGraph = PointsToResults.getInstance().inferredDataFlowGraph.eval();
		
		IProgressMonitor m = new org.eclipse.core.runtime.NullProgressMonitor();
		
		// for each interprocedural invocation data flow edges (ignoring the assignment data flow edges)
		AtlasSet<GraphElement> notInferredPerControlFlowCallEdges = new AtlasHashSet<GraphElement>();
		AtlasSet<GraphElement> notInferredPerMethodCallEdges = new AtlasHashSet<GraphElement>();
		
		AtlasSet<GraphElement> dfInterprocInvokeEdges = Common.resolve(m, Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge)).eval().edges();
		for(GraphElement dfInterprocInvokeEdge : dfInterprocInvokeEdges){
			if(dfGraph.edges().contains(dfInterprocInvokeEdge)) {
				// tag the inferred call summary, keep track of the edges that were not inferred
				Q identityPass = Common.toQ(dfInterprocInvokeEdge.getNode(EdgeDirection.FROM));
				Q callsite = Common.universe().edgesTaggedWithAny(XCSG.Contains).predecessors(identityPass);
				Q identity = Common.toQ(dfInterprocInvokeEdge.getNode(EdgeDirection.TO));
				Q target = Common.universe().edgesTaggedWithAny(XCSG.Contains).predecessors(identity);
				// infer per control flow call summary edges
				for(GraphElement perControlFlowEdge : Common.universe().edgesTaggedWithAll(Edge.CALL, Edge.PER_CONTROL_FLOW).betweenStep(callsite, target).eval().edges()){
					perControlFlowEdge.tag(PER_CONTROL_FLOW);
					notInferredPerControlFlowCallEdges.remove(perControlFlowEdge); // remove the edge if it was previously thought to have been not inferred
				}
				for(GraphElement callEdge : Common.universe().edgesTaggedWithAny(Edge.CALL, Edge.PER_CONTROL_FLOW).forwardStep(callsite)
						.differenceEdges(Common.universe().edgesTaggedWithAll(Edge.CALL, Edge.PER_CONTROL_FLOW, CALL)).eval().edges()){
					if(!callEdge.tags().contains(PER_CONTROL_FLOW)){
						notInferredPerControlFlowCallEdges.add(callEdge);
					}
				}
				// infer per method call summary edges
				Q caller = Common.universe().edgesTaggedWithAny(XCSG.Contains).predecessors(callsite);
				for(GraphElement callEdge : Common.universe().edgesTaggedWithAll(XCSG.Call).betweenStep(caller, target).eval().edges()){
					callEdge.tag(CALL);
					notInferredPerMethodCallEdges.remove(callEdge); // remove the edge if it was previously thought to have been not inferred
				}
				for(GraphElement callEdge : Common.universe().edgesTaggedWithAny(XCSG.Call).forwardStep(caller)
						.differenceEdges(Common.universe().edgesTaggedWithAll(XCSG.Call, CALL)).eval().edges()){
					if(!callEdge.tags().contains(CALL)){
						notInferredPerMethodCallEdges.add(callEdge);
					}
				}
			}
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

}
