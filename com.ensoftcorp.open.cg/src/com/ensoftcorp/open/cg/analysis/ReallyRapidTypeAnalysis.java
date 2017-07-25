package com.ensoftcorp.open.cg.analysis;

import java.util.LinkedList;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.indexing.IndexingUtil;
import com.ensoftcorp.atlas.core.query.Attr;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.cg.log.Log;
import com.ensoftcorp.open.cg.preferences.CallGraphPreferences;
import com.ensoftcorp.open.commons.utilities.CodeMapChangeListener;
import com.ensoftcorp.open.java.commons.analysis.SetDefinitions;
import com.ensoftcorp.open.java.commons.analysis.CommonQueries;
import com.ensoftcorp.open.java.commons.analyzers.JavaProgramEntryPoints;

/**
 * Performs a Really Rapid Type Analysis (RRTA).
 * 
 * This algorithm is a bastardized version of RRTA that assumes all application
 * methods are entry points methods. It gathers all the new allocations types up
 * front and then creates the call graph in a single pass.
 * 
 * In terms of call graph construction precision this algorithm ranks better
 * than CHA, but worse than RRTA.
 * 
 * @author Ben Holland
 */
public class ReallyRapidTypeAnalysis extends CGAnalysis {

	public static final String CALL = "RRTA-CALL";
	public static final String PER_CONTROL_FLOW = "RRTA-PER-CONTROL-FLOW";
	
	private static ReallyRapidTypeAnalysis instance = null;
	
	protected ReallyRapidTypeAnalysis(boolean libraryCallGraphConstructionEnabled) {
		// exists only to defeat instantiation
		super(libraryCallGraphConstructionEnabled);
	}
	
	private static CodeMapChangeListener codeMapChangeListener = null;
	
	public static ReallyRapidTypeAnalysis getInstance(boolean enableLibraryCallGraphConstruction) {
		if (instance == null || (codeMapChangeListener != null && codeMapChangeListener.hasIndexChanged())) {
			instance = new ReallyRapidTypeAnalysis(enableLibraryCallGraphConstruction);
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
		// for library calls, RRTA uses CHA library call edges because assuming every that every type could be allocated
		// outside of the method and passed into the library is just an expensive way to end back up at CHA
		ClassHierarchyAnalysis cha = ClassHierarchyAnalysis.getInstance(libraryCallGraphConstructionEnabled);
		
		// RRTA depends on CHA so run the analysis if it hasn't been run already
		if(!cha.hasRun()){
			cha.run();
		}
		
		if(libraryCallGraphConstructionEnabled && cha.isLibraryCallGraphConstructionEnabled() != libraryCallGraphConstructionEnabled){
			Log.warning("ClassHierarchyAnalysis was run without library call edges enabled, "
					+ "the resulting call graph will be missing the LIBRARY-CALL edges.");
		}
		Q cgCHA = cha.getCallGraph();
		
		// next create some subgraphs to work with
		Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		
		// locate all the entry point methods
		Q rootMethods = Common.empty();
		
		// add the main methods as root methods
		// note that we only need to locate these methods in order to determine the 
		// allocation types that were initialized outside of teh application
		AtlasSet<Node> mainMethods = JavaProgramEntryPoints.findMainMethods().eval().nodes();
		if(libraryCallGraphConstructionEnabled || mainMethods.isEmpty()){
			if(!libraryCallGraphConstructionEnabled && mainMethods.isEmpty()){
				Log.warning("Application does not contain a main method, building a call graph using library assumptions.");
			}
			// if we are building a call graph for a library there is no main method...
			// a nice balance is to start with all public methods in the library
			rootMethods.union(rootMethods, SetDefinitions.app().nodesTaggedWithAll(XCSG.publicVisibility, XCSG.Method));
		} else {
			// under normal circumstances this algorithm would be given a single main method
			// but end users don't tend to think about this so consider any valid main method
			// as a program entry point
			if(mainMethods.size() > 1){
				Log.warning("Application contains multiple main methods. The call graph may contain unexpected conservative edges as a result.");
			}
			rootMethods.union(rootMethods, Common.toQ(mainMethods));
		}
		
		// infer the root methods that could result due to library callbacks
		if(CallGraphPreferences.isLibraryCallbackEntryPointsInferenceEnabled()){
			Q libraryTypes = SetDefinitions.libraries().nodes(XCSG.Type);
			Q libraryMethods = libraryTypes.children().nodes(XCSG.Method);
			Q overridesEdges = Common.universe().edges(XCSG.Overrides);
			Q appCallbackMethods = overridesEdges.predecessors(libraryMethods).intersection(SetDefinitions.app());
			rootMethods.union(rootMethods, appCallbackMethods);
		}
		
		// recover the types of entry point methods
		Q rootMethodParameterTypes = typeOfEdges.successors(rootMethods.children().nodes(XCSG.Parameter));
		
		// RRTA starts by assuming all new allocations in the program are reachable
		// which is to say we assume all application methods are entry points
		Q allocations = SetDefinitions.app().nodesTaggedWithAny(XCSG.Instantiation);
		Q allocationTypes = rootMethodParameterTypes.union(typeOfEdges.successors(allocations));

		// get a set of all the CHA call edges from the method and create an RRTA edge
		// from the method to the target method in the CHA call graph if the target methods
		// type is compatible with the feasible allocated types that would reach this method
		Q feasibleMethods = allocationTypes.children().nodes(XCSG.Method);
		Q constructors = Common.universe().nodesTaggedWithAny(XCSG.Constructor);
		Q methods = Common.universe().nodesTaggedWithAny(XCSG.Method);
		Q initializers = methods.methods("<init>");
		Q staticMethods = methods.nodes(Attr.Node.IS_STATIC);
		feasibleMethods = feasibleMethods.union(constructors, initializers, staticMethods);
		Q infeasibleMethods = Common.universe().nodes(XCSG.Method).difference(feasibleMethods);
		
		// remove the infeasible methods
		Q rrta = cgCHA.difference(infeasibleMethods);
		Q pcfCHA = cha.getPerControlFlowGraph();
		Q pcfRRTA = pcfCHA.difference(infeasibleMethods);
		
		// just tag each edge in the RRTA call graph with "RRTA" to distinguish it
		// from the CHA call graph
		if(CallGraphPreferences.isGeneralLoggingEnabled()){
			Log.info("Tagging RRTA Call Edges");
		}
		for(Edge rrtaEdge : rrta.eval().edges()){
			rrtaEdge.tag(CALL);
		}
		
		// tag each per control flow edge
		if(CallGraphPreferences.isGeneralLoggingEnabled()){
			Log.info("Tagging RRTA Per Control Flow Edges");
		}
		for(Edge pcfRRTAEdge : pcfRRTA.eval().edges()){
			pcfRRTAEdge.tag(PER_CONTROL_FLOW);
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