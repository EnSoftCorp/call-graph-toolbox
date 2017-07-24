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
	
	private static RapidTypeAnalysis instance = null;
	
	protected RapidTypeAnalysis(boolean libraryCallGraphConstructionEnabled) {
		// exists only to defeat instantiation
		super(libraryCallGraphConstructionEnabled);
	}
	
	private static CodeMapChangeListener codeMapChangeListener = null;
	
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
		
		// RTA depends on CHA so run the analysis if it hasn't been run already
		if(!cha.hasRun()){
			cha.run();
		}
		
		if(libraryCallGraphConstructionEnabled && cha.isLibraryCallGraphConstructionEnabled() != libraryCallGraphConstructionEnabled){
			Log.warning("ClassHierarchyAnalysis was run without library call edges enabled, "
					+ "the resulting call graph will be missing the LIBRARY-CALL edges.");
		}
		AtlasSet<Edge> cgCHA = new AtlasHashSet<Edge>(cha.getCallGraph().eval().edges());
		
		// next create some subgraphs to work with
		Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		Q declarations = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		
		AtlasSet<Node> rootMethods = new AtlasHashSet<Node>();
		
		// add the main methods as root methods
		AtlasSet<Node> mainMethods = JavaProgramEntryPoints.findMainMethods().eval().nodes();
		if(libraryCallGraphConstructionEnabled || mainMethods.isEmpty()){
			if(!libraryCallGraphConstructionEnabled && mainMethods.isEmpty()){
				Log.warning("Application does not contain a main method, building a call graph using library assumptions.");
			}
			// if we are building a call graph for a library there is no main method...
			// a nice balance is to start with all public methods in the library
			rootMethods.addAll(SetDefinitions.app().nodesTaggedWithAll(XCSG.publicVisibility, XCSG.Method).eval().nodes());
		} else {
			// under normal circumstances this algorithm would be given a single main method
			// but end users don't tend to think about this so consider any valid main method
			// as a program entry point
			if(mainMethods.size() > 1){
				Log.warning("Application contains multiple main methods. The call graph may contain unexpected conservative edges as a result.");
			}
			rootMethods.addAll(mainMethods);
		}
		
		// infer the root methods that could result due to library callbacks
		if(CallGraphPreferences.isLibraryCallbackEntryPointsInferenceEnabled()){
			Q libraryTypes = SetDefinitions.libraries().nodes(XCSG.Type);
			Q libraryMethods = libraryTypes.children().nodes(XCSG.Method);
			Q overridesEdges = Common.universe().edges(XCSG.Overrides);
			Q appCallbackMethods = overridesEdges.predecessors(libraryMethods).intersection(SetDefinitions.app());
			rootMethods.addAll(appCallbackMethods.eval().nodes());
		}

		// when types are first loaded by the class loader, the static initializer of the class is run
		AtlasSet<Node> loadedTypes = new AtlasHashSet<Node>();
		
		// add the static initializers of the methods in the root methods
		AtlasSet<Node> rootTypes = Common.toQ(rootMethods).parent().eval().nodes();
		loadedTypes.addAll(rootTypes);
		
		// the root methods will include any static initializers of root types
		AtlasSet<Node> rootTypeStaticInitializers = Common.toQ(rootTypes).children().methods("<clinit>").eval().nodes();
		rootMethods.addAll(rootTypeStaticInitializers);
		
		// create a worklist and add the root method set
		AtlasSet<Node> reachableMethods = new AtlasHashSet<Node>();
		reachableMethods.addAll(rootMethods);
		
		if(CallGraphPreferences.isGeneralLoggingEnabled()) {
			Log.info("RapidTypeAnalysis initialized with " + rootMethods.size() + " roots.");
		}
		
		// initially the RTA based call graph is empty
		AtlasSet<Edge> cgRTA = new AtlasHashSet<Edge>();
		
		// RTA has a single global allocation types set
		AtlasSet<Node> allocationTypes = new AtlasHashSet<Node>();
		
		// iterate until a fixed point is reached in the call graph
		long cgSize = cgRTA.size();

		// iterate until the worklist is empty (in RTA the worklist only contains methods)
		do {
			cgSize = cgRTA.size();
			
			// for each of the reachable methods discover the new allocation types
			// it is best to do this step separate because discovering as many of
			// the allocation types up front gets us to a fixed point faster and
			// prevents us from redoing a bunch of work
			for(Node method : reachableMethods){
				// we should consider any new allocation types instantiated in the method
				// allocations are contained (declared) within the methods in the method reverse call graph
				Q methodDeclarations = CommonQueries.localDeclarations(Common.toQ(method));
				Q allocations = methodDeclarations.nodesTaggedWithAny(XCSG.Instantiation);
				
				// collect the types of each allocation
				AtlasSet<Node> methodAllocationTypes = typeOfEdges.successors(allocations).eval().nodes();
				allocationTypes.addAll(methodAllocationTypes);
			}
			
			// get a set of all the CHA call edges from the method and create an RTA edge
			// from the method to the target method in the CHA call graph if the target methods
			// type is compatible with the feasible allocated types that would reach this method
			Q feasibleMethods = Common.toQ(allocationTypes).children().nodes(XCSG.Method);
			Q constructors = Common.universe().nodesTaggedWithAny(XCSG.Constructor);
			Q methods = Common.universe().nodesTaggedWithAny(XCSG.Method);
			Q initializers = methods.methods("<init>");
			Q staticMethods = methods.nodes(Attr.Node.IS_STATIC);
			feasibleMethods = feasibleMethods.union(constructors, initializers, staticMethods);
			Q infeasibleMethods = Common.universe().nodes(XCSG.Method).difference(feasibleMethods);
			
			// for each of the reachable methods add the new feasible call edges
			AtlasSet<Node> callTargets = new AtlasHashSet<Node>();
			for(Node method : reachableMethods){
				AtlasSet<Edge> callEdges = Common.toQ(cgCHA).difference(infeasibleMethods).forwardStep(Common.toQ(method)).eval().edges();
				for(Edge callEdge : callEdges){
					// add static dispatches to the rta call graph
					// includes called methods marked static and constructors
					Node calledMethod = callEdge.getNode(EdgeDirection.TO);
					Node callingMethod = callEdge.getNode(EdgeDirection.FROM);
					Q callingStaticDispatches = CommonQueries.localDeclarations(Common.toQ(callingMethod)).nodesTaggedWithAny(XCSG.StaticDispatchCallSite);
					boolean isStaticDispatch = !cha.getPerControlFlowGraph().predecessors(Common.toQ(calledMethod)).intersection(callingStaticDispatches).eval().nodes().isEmpty();
					if(isStaticDispatch || calledMethod.taggedWith(XCSG.Constructor) || calledMethod.getAttr(XCSG.name).equals("<init>")){
						updateCallGraph(cgRTA, method, allocationTypes, callEdge, calledMethod);
						callTargets.add(calledMethod);
						// add the static initializers of newly loaded types as root methods
						for(Node callMethodType : Common.toQ(calledMethod).parent().eval().nodes()){
							if(loadedTypes.add(callMethodType)){
								Q staticInitializers = Common.toQ(callMethodType).children().methods("<clinit>");
								// ignoring initializers in libraries because they are just method stubs
								Q appStaticInitializers = staticInitializers.intersection(SetDefinitions.app());
								for(Node staticInitializer : appStaticInitializers.eval().nodes()){
									callTargets.add(staticInitializer);
								}
							}
						}
					} else {
						// the call edge is a dynamic dispatch, need to resolve possible dispatches
						// a dispatch is possible if the type declaring the method is one of the 
						// allocated types (or the parent of an allocated type)
						// note: we should consider the supertype hierarchy of the allocation types
						// because methods can be inherited from parent types
						Q typeDeclaringCalledMethod = declarations.predecessors(Common.toQ(calledMethod));
						if(!typeHierarchy.forward(Common.toQ(allocationTypes)).intersection(typeDeclaringCalledMethod).eval().nodes().isEmpty()){
							updateCallGraph(cgRTA, method, allocationTypes, callEdge, calledMethod);
							callTargets.add(calledMethod);
						}
					}
				}
			}
			
			if(CallGraphPreferences.isGeneralLoggingEnabled()) {
				Log.info("RapidTypeAnalysis discovered " + (cgRTA.size() - cgSize) + " new call edges."
					+ "\n" + cgRTA.size() + " call edges"
					+ "\n" + reachableMethods.size() + " reachable methods"
					+ "\n" + allocationTypes.size() + " allocation types"
					+ "\n" + loadedTypes.size() + " loaded types");
			}
			
			// update our set of reachable methods for the next iteration
			reachableMethods.addAll(callTargets);
			
			// remove the realized call edges so we don't repeat work
			for(Edge rtaEdge : cgRTA){
				cgCHA.remove(rtaEdge);
			}
		} while(cgRTA.size() > cgSize);
		
		if(CallGraphPreferences.isGeneralLoggingEnabled()) {
			Log.info("RapidTypeAnalysis reached fixed point.");
		}
		
		// just tag each edge in the RTA call graph with "RTA" to distinguish it
		// from the CHA call graph
		Q pcfCHA = cha.getPerControlFlowGraph();
		for(Edge rtaEdge : cgRTA){
			rtaEdge.tag(CALL);
			Node callingMethod = rtaEdge.getNode(EdgeDirection.FROM);
			Node calledMethod = rtaEdge.getNode(EdgeDirection.TO);
			Q callsites = declarations.forward(Common.toQ(callingMethod)).nodesTaggedWithAny(XCSG.CallSite);
			for(Edge perControlFlowEdge : pcfCHA.betweenStep(callsites, Common.toQ(calledMethod)).eval().edges()){
				perControlFlowEdge.tag(PER_CONTROL_FLOW);
			}
		}
	}

	/**
	 * Updates the call graph and worklist for methods
	 * @param worklist
	 * @param cgRTA
	 * @param method
	 * @param allocationTypes
	 * @param callEdge
	 * @param calledMethod
	 */
	private static boolean updateCallGraph(AtlasSet<Edge> cgRTA, Node method, AtlasSet<Node> allocationTypes, Edge callEdge, Node calledMethod) {
		AtlasSet<Node> calledMethods = new AtlasHashSet<Node>();
		if(Common.toQ(cgRTA).betweenStep(Common.toQ(method), Common.toQ(calledMethod)).eval().edges().isEmpty()){
			return cgRTA.add(callEdge);
		}
		return false;
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