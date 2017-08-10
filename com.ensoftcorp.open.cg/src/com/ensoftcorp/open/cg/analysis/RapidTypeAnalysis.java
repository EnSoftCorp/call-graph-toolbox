package com.ensoftcorp.open.cg.analysis;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
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
import com.ensoftcorp.open.cg.utils.CallGraphConstruction;
import com.ensoftcorp.open.commons.utilities.CodeMapChangeListener;
import com.ensoftcorp.open.java.commons.analysis.CommonQueries;
import com.ensoftcorp.open.java.commons.analysis.SetDefinitions;
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
	
	protected RapidTypeAnalysis() {
		// exists only to defeat instantiation
	}
	
	private static CodeMapChangeListener codeMapChangeListener = null;
	
	public static RapidTypeAnalysis getInstance() {
		if (instance == null || (codeMapChangeListener != null && codeMapChangeListener.hasIndexChanged())) {
			instance = new RapidTypeAnalysis();
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
		ClassHierarchyAnalysis cha = ClassHierarchyAnalysis.getInstance();
		
		// RTA depends on CHA so run the analysis if it hasn't been run already
		if(!cha.hasRun()){
			cha.run();
		}

		Q cgCHA = cha.getCallGraph();
		Q pcfCHA = cha.getPerControlFlowGraph();
		
		// next create some subgraphs to work with
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		
		// locate all the entry point methods
		Q rootMethods = Common.empty();
		
		// add the main methods as root methods
		// note that we only need to locate these methods in order to determine the 
		// allocation types that were initialized outside of the application
		AtlasSet<Node> mainMethods = JavaProgramEntryPoints.findMainMethods().eval().nodes();
		if(CallGraphPreferences.isLibraryCallGraphConstructionEnabled() || mainMethods.isEmpty()){
			if(!CallGraphPreferences.isLibraryCallGraphConstructionEnabled() && mainMethods.isEmpty()){
				Log.warning("Application does not contain a main method, building a call graph using library assumptions.");
			}
			// if we are building a call graph for a library there is no main method...
			// a nice balance is to start with all public methods in the library
			rootMethods = rootMethods.union(Common.toQ(mainMethods), SetDefinitions.app().nodesTaggedWithAll(XCSG.publicVisibility, XCSG.Method));
		} else {
			// under normal circumstances this algorithm would be given a single main method
			// but end users don't tend to think about this so consider any valid main method
			// as a program entry point
			if(mainMethods.size() > 1){
				Log.warning("Application contains multiple main methods. The call graph may contain unexpected conservative edges as a result.");
			}
			rootMethods = rootMethods.union(rootMethods, Common.toQ(mainMethods));
		}
		
		// infer the root methods that could result due to library callbacks
		if(CallGraphPreferences.isLibraryCallbackEntryPointsInferenceEnabled()){
			Q libraryTypes = SetDefinitions.libraries().nodes(XCSG.Type);
			Q libraryMethods = libraryTypes.children().nodes(XCSG.Method);
			Q overridesEdges = Common.universe().edges(XCSG.Overrides);
			Q appCallbackMethods = overridesEdges.predecessors(libraryMethods).intersection(SetDefinitions.app());
			rootMethods = rootMethods.union(rootMethods, appCallbackMethods);
		}
		
		// recover the types of entry point methods
		Q rootMethodParameterTypes = typeOfEdges.successors(rootMethods.children().nodes(XCSG.Parameter));
		
		// RTA starts by assuming all new allocations in the program are reachable
		// which is to say we assume all application methods are entry points
		Q allocations = SetDefinitions.app().nodesTaggedWithAny(XCSG.Instantiation);
		Q allocationTypes = rootMethodParameterTypes.union(typeOfEdges.successors(allocations));

		Q feasibleMethods = allocationTypes.children().nodes(XCSG.Method);
		Q constructors = Common.universe().nodesTaggedWithAny(XCSG.Constructor);
		Q methods = Common.universe().nodesTaggedWithAny(XCSG.Method);
		Q initializers = methods.methods("<init>");
		Q staticMethods = methods.nodes(Attr.Node.IS_STATIC);
		feasibleMethods = feasibleMethods.union(constructors, initializers, staticMethods);
		Q infeasibleMethods = Common.universe().nodes(XCSG.Method).difference(feasibleMethods);
		
		if(CallGraphPreferences.isReachabilityEnabled()){
			Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
			Q invokedFunctionEdges = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction);
			Q identityPassedToEdges = Common.universe().edgesTaggedWithAny(XCSG.IdentityPassedTo);
			Q dataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge);
			Graph methodSignatureGraph = Common.universe().edgesTaggedWithAny(XCSG.InvokedSignature).eval();
			
			// iteratively build the call graph one method at a time (visiting each method once)
			// starting from the entry point methods (this adds a restriction of reachability to the
			// final call graph)
			AtlasSet<Node> processedMethods = new AtlasHashSet<Node>();
			AtlasSet<Node> methodsToProcess = new AtlasHashSet<Node>();
			methodsToProcess.addAll(rootMethods.eval().nodes());
			while(!methodsToProcess.isEmpty()){
				Node methodToProcess = methodsToProcess.one();
				methodsToProcess.remove(methodToProcess);
				processedMethods.add(methodToProcess);

				// process each callsite in the method
				AtlasSet<Node> callsites = CommonQueries.localDeclarations(Common.toQ(methodToProcess)).nodes(XCSG.CallSite).eval().nodes();
				for(Node callsite : callsites){
					if(callsite.taggedWith(XCSG.StaticDispatchCallSite)){
						// static dispatches (calls to constructors or methods marked as static) can be resolved immediately
						Node targetMethod = invokedFunctionEdges.successors(Common.toQ(callsite)).eval().nodes().one();
						CallGraphConstruction.tagExistingCallEdge(cgCHA, pcfCHA, callsite, methodToProcess, targetMethod, CALL, PER_CONTROL_FLOW);
						
						// add the called method to the list of methods process
						if(!processedMethods.contains(targetMethod)){
							methodsToProcess.add(targetMethod);
						}
						
						// add the called method types static initializer to the methods to process
						Node staticInitializer = Common.toQ(targetMethod).parent().children().nodes(XCSG.Method).method("<clinit>").eval().nodes().one();
						if(staticInitializer != null && !processedMethods.contains(staticInitializer)){
							methodsToProcess.add(staticInitializer);
						}
					} else if(callsite.taggedWith(XCSG.DynamicDispatchCallSite)){
						// dynamic dispatches require additional analysis to be resolved
						// first get the declared type of the receiver object
						Q thisNode = identityPassedToEdges.predecessors(Common.toQ(callsite));
						Q receiverObject = dataFlowEdges.predecessors(thisNode);
						Q declaredType = typeOfEdges.successors(receiverObject);
						
						// since the dispatch was called on the "declared" type there must be at least one signature
						// (abstract or concrete) in the descendant path (the direct path from Object to the declared path)
						// the nearest method definition is the method definition closest to the declared type (including
						// the declared type itself) while traversing from declared type to Object on the descendant path
						// but an easier way to get this is to use Atlas' InvokedSignature edge to get the nearest method definition
						Node methodSignature = methodSignatureGraph.edges(callsite, NodeDirection.OUT).one().getNode(EdgeDirection.TO);
						Q resolvedDispatches = Common.toQ(methodSignature);
						
						// subtypes of the declared type can override the nearest target method definition, 
						// so make sure to include all the subtype method definitions
						// futher restrict those types by the allocated types
						Q restrictedTypes = typeHierarchy.reverse(declaredType).intersection(allocationTypes);
						
						// next perform a reachability analysis (RA) within the set of subtypes
						Q reachableMethods = Common.toQ(ReachabilityAnalysis.getReachableMethods(callsite, restrictedTypes));
						resolvedDispatches = resolvedDispatches.union(reachableMethods);

						// if a method is abstract, then its children must override it, so we can just remove all abstract
						// methods from the graph (this might come into play if nearest matching method definition was abstract)
						// note: its possible for a method to be re-abstracted by a subtype after its been made concrete
						resolvedDispatches = resolvedDispatches.difference(Common.universe().nodes(XCSG.abstractMethod));
						
						// lastly, if the method signature is concrete and the type of the method signature is abstract 
						// and all subtypes override the method signature then the method signature can never be called
						// directly, so remove it from the result
						boolean abstractMethodSignature = methodSignature.taggedWith(XCSG.abstractMethod);
						if(!abstractMethodSignature){
							Q methodSignatureType = Common.toQ(methodSignature).parent();
							boolean abstractMethodSignatureType = methodSignatureType.eval().nodes().one().taggedWith(XCSG.Java.AbstractClass);
							if(abstractMethodSignatureType){
								Q resolvedDispatchConcreteSubTypes = resolvedDispatches.difference(Common.toQ(methodSignature)).parent()
										.difference(Common.universe().nodes(XCSG.Java.AbstractClass));
								if(!resolvedDispatchConcreteSubTypes.eval().nodes().isEmpty()){
									// there are concrete subtypes
									if(restrictedTypes.difference(methodSignatureType, resolvedDispatchConcreteSubTypes).eval().nodes().isEmpty()){
										// all subtypes override method signature, method signature implementation can never be called
										resolvedDispatches = resolvedDispatches.difference(Common.toQ(methodSignature));
									}
								}
							}
						}
						
						// add a call edge to each resolved concrete dispatch
						for(Node resolvedDispatch : resolvedDispatches.eval().nodes()){
							CallGraphConstruction.tagExistingCallEdge(cgCHA, pcfCHA, callsite, methodToProcess, resolvedDispatch, CALL, PER_CONTROL_FLOW);
							
							// add the called method to the list of methods process
							if(!processedMethods.contains(resolvedDispatch)){
								methodsToProcess.add(resolvedDispatch);
							}
							
							// add the called method types static initializer to the methods to process
							Node staticInitializer = Common.toQ(resolvedDispatch).parent().children().nodes(XCSG.Method).method("<clinit>").eval().nodes().one();
							if(staticInitializer != null && !processedMethods.contains(staticInitializer)){
								methodsToProcess.add(staticInitializer);
							}
						}
					}
				}
			}
		} else {
			// if we are not considering reachability from an entry point method, we can assume
			// any method in the application is reachable and just remove CHA edges that are not
			// feasible based on observed allocation types
			
			// remove the infeasible methods
			Q rta = cgCHA.difference(infeasibleMethods);
			Q pcfRTA = pcfCHA.difference(infeasibleMethods);
			
			// just tag each edge in the RTA call graph with "RTA" to distinguish it
			// from the CHA call graph
			for(Edge rtaEdge : rta.eval().edges()){
				rtaEdge.tag(CALL);
			}
			
			// tag each per control flow edge
			for(Edge pcfRTAEdge : pcfRTA.eval().edges()){
				pcfRTAEdge.tag(PER_CONTROL_FLOW);
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
		return "Rapid Type Analysis";
	}
}