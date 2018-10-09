package com.ensoftcorp.open.cg.analysis;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.indexing.IndexingUtil;
import com.ensoftcorp.atlas.core.query.Attr;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.utilities.CodeMapChangeListener;
import com.ensoftcorp.open.pointsto.common.PointsToAnalysis;
import com.ensoftcorp.open.pointsto.preferences.PointsToPreferences;

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
	
	protected ZeroControlFlowAnalysis() {
		// exists only to defeat instantiation
	}
	
	private static CodeMapChangeListener codeMapChangeListener = null;
	
	public static ZeroControlFlowAnalysis getInstance() {
		if (instance == null || (codeMapChangeListener != null && codeMapChangeListener.hasIndexChanged())) {
			instance = new ZeroControlFlowAnalysis();
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
		if(!PointsToPreferences.isPointsToAnalysisEnabled()){
			throw new RuntimeException("Points-to analysis has not been run!");
		}
		
//		// so just looking for know tags instead...
//		if(!Query.universe().edges("INFERRED").eval().edges().isEmpty()){
//			throw new RuntimeException("Points-to analysis has not been run!");
//		}
		
		// library call edges are conservatively covered by a CHA, otherwise CHA is not used by k-CFA
		ClassHierarchyAnalysis cha = ClassHierarchyAnalysis.getInstance();
		if(!cha.hasRun()){
			cha.run();
		}

		// the points-to analysis just infers data flow edges
		// but the call edges are really just a summary of the 
		// data flow edges, so we can extract the call relationships
		// retroactively out of the data flow graph
		Q inferredDF = Query.universe().edges(PointsToAnalysis.INFERRED_DATA_FLOW);
		
		IProgressMonitor m = new org.eclipse.core.runtime.NullProgressMonitor();
		
		AtlasSet<Edge> dfInterprocInvokeEdges = Common.resolve(m, Query.universe().edges(XCSG.DataFlow_Edge)).eval().edges();
		for(Edge dfInterprocInvokeEdge : dfInterprocInvokeEdges){
			if(inferredDF.eval().edges().contains(dfInterprocInvokeEdge)) {
				// tag the inferred call summary, keep track of the edges that were not inferred
				Q identityPass = Common.toQ(dfInterprocInvokeEdge.from());
				Q callsiteCFNode = Query.universe().edges(XCSG.Contains).predecessors(identityPass);
				Q identity = Common.toQ(dfInterprocInvokeEdge.to());
				Q target = Query.universe().edges(XCSG.Contains).predecessors(identity);
				Q callsite = Query.universe().edges(XCSG.Contains).successors(callsiteCFNode).nodes(XCSG.CallSite);
				// infer per control flow call summary edges
				for(@SuppressWarnings("unused") Edge perControlFlowEdge : Query.universe().edgesTaggedWithAll(Attr.Edge.PER_CONTROL_FLOW).betweenStep(callsiteCFNode, target).eval().edges()){
//					perControlFlowEdge.tag(PER_CONTROL_FLOW); // this is the Atlas way (from the control flow node)
					Node callsiteGE = callsite.eval().nodes().one();
					if(callsiteGE != null){
						Edge perCFEdge = Graph.U.createEdge(callsiteGE, target.eval().nodes().one());
						perCFEdge.tag(PER_CONTROL_FLOW); // this is an edge from the callsite to the target method
					}
				}
				// infer per method call summary edges
				Q caller = Query.universe().edges(XCSG.Contains).predecessors(callsiteCFNode);
				for(Edge callEdge : Query.universe().edgesTaggedWithAll(XCSG.Call).betweenStep(caller, target).eval().edges()){
					callEdge.tag(CALL);
				}
			}
		}
		
		// import the statically resolved methods from CHA
		AtlasSet<Edge> callEdges = Query.universe().edges(XCSG.Call).eval().edges();
		AtlasSet<Node> reachableMethods = Query.universe().edges(CALL).retainEdges().eval().nodes();
		Q perControlFlowEdges = Query.universe().edges(XCSG.ControlFlow_Edge);
		Q declarations = Query.universe().edges(XCSG.Contains);
		for(Edge callEdge : callEdges){
			// add static dispatches to the call graph
			// includes called methods marked static and constructors
			Node calledMethod = callEdge.to();
			Node callingMethod = callEdge.from();
			Q callingStaticDispatches = Common.toQ(callingMethod).contained().nodes(XCSG.StaticDispatchCallSite);
			boolean isStaticDispatch = !Query.universe().edges(PER_CONTROL_FLOW).predecessors(Common.toQ(calledMethod))
					.intersection(callingStaticDispatches).eval().nodes().isEmpty();
			if(isStaticDispatch || calledMethod.taggedWith(XCSG.Constructor) || calledMethod.getAttr(XCSG.name).equals("<init>")){
				if(reachableMethods.contains(callingMethod)){
					callEdge.tag(CALL);
					Q callsites = declarations.forward(Common.toQ(callingMethod)).nodes(XCSG.CallSite);
					Q cfNodes = Query.universe().edges(XCSG.Contains).predecessors(callsites);
					for(Edge perControlFlowEdge : perControlFlowEdges.betweenStep(cfNodes, Common.toQ(calledMethod)).eval().edges()){
						perControlFlowEdge.tag(PER_CONTROL_FLOW);
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
	
	@Override
	public String getName() {
		return "0-CFA";
	}

}
