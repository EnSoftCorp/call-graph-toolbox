package com.ensoftcorp.open.cg.analysis;

import java.io.File;

import org.objectweb.asm.tree.ClassNode;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.indexing.IndexingUtil;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.cg.log.Log;
import com.ensoftcorp.open.cg.preferences.CallGraphPreferences;
import com.ensoftcorp.open.cg.summary.BytecodeUtils;
import com.ensoftcorp.open.cg.summary.JarInspector;
import com.ensoftcorp.open.cg.summary.MethodSummary;
import com.ensoftcorp.open.cg.utils.CallGraphConstruction;
import com.ensoftcorp.open.commons.utilities.CodeMapChangeListener;
import com.ensoftcorp.open.java.commons.analysis.CommonQueries;

/**
 * This analysis builds a call graph using Class Hierarchy Analysis (CHA).
 * 
 * In terms of precision, this is the least precise analysis compared to RTA, 
 * VTA, and 0-CFA, but is also the cheapest analysis of the available algorithms.
 * Several other algorithms (except 0-CFA) use CHA as a starting input for their analyses.
 * 
 * Note: Atlas already has an implementation of CHA baked-in, so its pointless
 * to do this analysis, except for the intellectual exercise of doing so.
 * 
 * @author Ben Holland
 */
public class ClassHierarchyAnalysis extends CGAnalysis {

	public static final String CALL = "CHA-CALL"; 
	public static final String PER_CONTROL_FLOW = "CHA-PER-CONTROL-FLOW"; 
	public static final String LIBRARY_CALL = "CHA-LIBRARY-CALL";
	public static final String LIBRARY_PER_CONTROL_FLOW = "CHA-LIBRARY-PER-CONTROL-FLOW"; 
	
	private static ClassHierarchyAnalysis instance = null;
	
	protected ClassHierarchyAnalysis() {
		// exists only to defeat instantiation
	}
	
	private static CodeMapChangeListener codeMapChangeListener = null;
	
	public static ClassHierarchyAnalysis getInstance() {
		if (instance == null || (codeMapChangeListener != null && codeMapChangeListener.hasIndexChanged())) {
			instance = new ClassHierarchyAnalysis();
			if(codeMapChangeListener == null){
				codeMapChangeListener = new CodeMapChangeListener();
				IndexingUtil.addListener(codeMapChangeListener);
			} else {
				codeMapChangeListener.reset();
			}
		}
		return instance;
	}
	
	private Q typeHierarchy = Common.universe().edgesTaggedWithAny(XCSG.Supertype);
	private Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
	private Q invokedFunctionEdges = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction);
	private Q identityPassedToEdges = Common.universe().edgesTaggedWithAny(XCSG.IdentityPassedTo);
	private Q dataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge);
	private Graph methodSignatureGraph = Common.universe().edgesTaggedWithAny(XCSG.InvokedSignature).eval();
	private AtlasSet<Node> methods = Common.universe().nodesTaggedWithAny(XCSG.Method).eval().nodes();
	
	@Override
	protected void runAnalysis() {
//		// add callsite summaries for each library method
//		for(Node library : Common.universe().nodes(XCSG.Library).eval().nodes()){
//			try {
//				// TODO: how SHOULD I be getting the path to the library???
//				String id = library.getAttr(XCSG.id).toString();
//				id = id.substring(1, id.length()-1);
//				String[] idParts = id.split("#");
//				File libraryFile = null; 
//				for(int i=0; i<idParts.length; i++){
//					String libraryPath = idParts[i];
//					libraryFile = new File(libraryPath);
//					if(libraryFile.exists()){
//						break;
//					}
//				}
//				if(libraryFile == null || !libraryFile.exists()){
//					throw new RuntimeException("Could not locate library file");
//				}
//				JarInspector jarInspector = new JarInspector(libraryFile);
//				for(String entry : jarInspector.getJarEntrySet()){
//					if(entry.endsWith(".class")){
//						String typeString = entry.replace(".class", "").replace("/", ".");
//						if(typeString.contains("$")){
////							Log.warning("Skipping summaries of inner classes");
//						} else {
//							String pkgName = typeString.substring(0,typeString.lastIndexOf("."));
//							String typeName = typeString.substring(pkgName.length()+1, typeString.length());
//							Node type = Common.typeSelect(pkgName, typeName).eval().nodes().one();
//							if(type != null){
//								ClassNode classNode = BytecodeUtils.getClassNode(jarInspector.extractEntry(entry));
//								MethodSummary.summarizeCallsites(classNode, type);
//							}
//						}
//					}
//				}
//			} catch (Exception e){
//				Log.warning("Could not summarize callsites in library: " + library.getAttr(XCSG.name) + "\n" + library.toString(), e);
//			}
//		}
		
		// for each method
		for(Node method : methods){
			// for each callsite
			AtlasSet<Node> callsites = CommonQueries.localDeclarations(Common.toQ(method)).nodesTaggedWithAny(XCSG.CallSite).eval().nodes();
			for(Node callsite : callsites){
				if(callsite.taggedWith(XCSG.StaticDispatchCallSite)){
					// static dispatches (calls to constructors or methods marked as static) can be resolved immediately
					Node targetMethod = invokedFunctionEdges.successors(Common.toQ(callsite)).eval().nodes().one();
					CallGraphConstruction.createCallEdge(callsite, method, targetMethod, CALL, PER_CONTROL_FLOW);
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
					Q declaredSubtypeHierarchy = typeHierarchy.reverse(declaredType);
					
					// next perform a reachability analysis (RA) withing the set of subtypes
					Q reachableMethods = Common.toQ(ReachabilityAnalysis.getReachableMethods(callsite, declaredSubtypeHierarchy));
					resolvedDispatches = resolvedDispatches.union(reachableMethods);

					// if a method is abstract, then its children must override it, so we can just remove all abstract
					// methods from the graph (this might come into play if nearest matching method definition was abstract)
					// note: its possible for a method to be re-abstracted by a subtype after its been made concrete
					resolvedDispatches = resolvedDispatches.difference(Common.universe().nodesTaggedWithAny(XCSG.abstractMethod));
					
					// lastly, if the method signature is concrete and the type of the method signature is abstract 
					// and all subtypes override the method signature then the method signature can never be called
					// directly, so remove it from the result
					boolean abstractMethodSignature = methodSignature.taggedWith(XCSG.abstractMethod);
					if(!abstractMethodSignature){
						Q methodSignatureType = Common.toQ(methodSignature).parent();
						boolean abstractMethodSignatureType = methodSignatureType.eval().nodes().one().taggedWith(XCSG.Java.AbstractClass);
						if(abstractMethodSignatureType){
							Q resolvedDispatchConcreteSubTypes = resolvedDispatches.difference(Common.toQ(methodSignature)).parent()
									.difference(Common.universe().nodesTaggedWithAny(XCSG.Java.AbstractClass));
							if(!resolvedDispatchConcreteSubTypes.eval().nodes().isEmpty()){
								// there are concrete subtypes
								if(declaredSubtypeHierarchy.difference(methodSignatureType, resolvedDispatchConcreteSubTypes).eval().nodes().isEmpty()){
									// all subtypes override method signature, method signature implementation can never be called
									resolvedDispatches = resolvedDispatches.difference(Common.toQ(methodSignature));
								}
							}
						}
					}
					
					// add a call edge to each resolved concrete dispatch
					for(Node resolvedDispatch : resolvedDispatches.eval().nodes()){
						CallGraphConstruction.createCallEdge(callsite, method, resolvedDispatch, CALL, PER_CONTROL_FLOW);
					}
					
					// if library call graph construction is enabled then we will consider adding a special edge type
					// in the case that the method signature was abstract since we may not have been able to resolve any
					// dispatch targets (in the case the method is not implemented in the library)
					// of course the application could override any non-final methods anyway but we can't say anything
					// about that at this time
					if(CallGraphPreferences.isLibraryCallGraphConstructionEnabled()){
						if(methodSignature.taggedWith(XCSG.abstractMethod)){
							CallGraphConstruction.createCallEdge(callsite, method, methodSignature, LIBRARY_CALL, LIBRARY_PER_CONTROL_FLOW);
						}
					}
				}
			}
		}
	}
	
	@Override
	public String[] getCallEdgeTags() {
		return new String[]{CALL, LIBRARY_CALL};
	}
	
	@Override
	public String[] getPerControlFlowEdgeTags() {
		return new String[]{PER_CONTROL_FLOW, LIBRARY_PER_CONTROL_FLOW};
	}

	@Override
	public String getName() {
		return "Class Hierarchy Analysis";
	}

}