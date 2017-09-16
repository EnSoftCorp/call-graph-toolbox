package com.ensoftcorp.open.cg.summary;

import java.util.Iterator;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

public class MethodSummary {

	/**
	 * Summarizes (recovers) callsites in method bodies of library binaries
	 * @param classNode
	 * @param type
	 */
	@SuppressWarnings("unused")
	public static void summarizeCallsites(ClassNode classNode, Node type) {
		
		Q methods = Common.toQ(type).children().nodes(XCSG.Method);
		
		// visit each method in the class
    	for (Object o : classNode.methods) {
			MethodNode methodNode = (MethodNode) o;
			
			// get corresponding Atlas method
			Node atlasMethodNode = null;
			AtlasSet<Node> atlasMethodNodes = methods.selectNode(XCSG.name, methodNode.name).eval().nodes();
			if(atlasMethodNodes.size() == 1){
				atlasMethodNode = atlasMethodNodes.one();
			} else {
				// TODO: implement
				// filter by number of parameters
				// filter by type of parameters
			}
			
			if(atlasMethodNode != null){
				InsnList instructions = methodNode.instructions;
				Iterator<AbstractInsnNode> instructionIterator = instructions.iterator();
				while (instructionIterator.hasNext()) {
					AbstractInsnNode abstractInstruction = instructionIterator.next();
					if (abstractInstruction instanceof FieldInsnNode) {
						FieldInsnNode instruction = (FieldInsnNode) abstractInstruction;
					} else if (abstractInstruction instanceof FrameNode) {
						FrameNode instruction = (FrameNode) abstractInstruction;
					} else if (abstractInstruction instanceof IincInsnNode) {
						IincInsnNode instruction = (IincInsnNode) abstractInstruction;
					} else if (abstractInstruction instanceof InsnNode) {
						InsnNode instruction = (InsnNode) abstractInstruction;
					} else if (abstractInstruction instanceof IntInsnNode) {
						IntInsnNode instruction = (IntInsnNode) abstractInstruction;
					} else if (abstractInstruction instanceof InvokeDynamicInsnNode) {
						InvokeDynamicInsnNode instruction = (InvokeDynamicInsnNode) abstractInstruction;
						// https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokedynamic
					} else if (abstractInstruction instanceof JumpInsnNode) {
						JumpInsnNode instruction = (JumpInsnNode) abstractInstruction;
					} else if (abstractInstruction instanceof LabelNode) {
						LabelNode instruction = (LabelNode) abstractInstruction;
					} else if (abstractInstruction instanceof LdcInsnNode) {
						LdcInsnNode instruction = (LdcInsnNode) abstractInstruction;
					} else if (abstractInstruction instanceof LineNumberNode) {
						LineNumberNode instruction = (LineNumberNode) abstractInstruction;
					} else if (abstractInstruction instanceof LookupSwitchInsnNode) {
						LookupSwitchInsnNode instruction = (LookupSwitchInsnNode) abstractInstruction;
					} else if (abstractInstruction instanceof MethodInsnNode) {
						MethodInsnNode instruction = (MethodInsnNode) abstractInstruction;
						if(instruction.getOpcode() == Opcodes.INVOKESTATIC) {
							// https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokestatic
							String targetMethodPackage = instruction.owner.replace("/", "."); // ex: java/util/Objects
							String targetMethod = instruction.name; // ex: requireNonNull
							String targetMethodParameters = instruction.desc; // ex: (Ljava/lang/Object;)Ljava/lang/Object;
						} else if(instruction.getOpcode() == Opcodes.INVOKEVIRTUAL || instruction.getOpcode() == Opcodes.INVOKEINTERFACE) {
							// https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokevirtual
							// https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokeinterface
							String owner = instruction.owner.replace("/", "."); // ex: java/util/AbstractCollection, java/util/Iterator
							String targetMethodPackage = owner.substring(0, owner.lastIndexOf(".")); 
							String targetMethodType = owner.substring(owner.lastIndexOf(".")+1);
							
							String targetMethod = instruction.name; // ex: size, hasNext
							String targetMethodParameters = instruction.desc; // ex: ()I, ()Z
							
							// create a callsite node
							Node callsite = Graph.U.createNode();
							callsite.putAttr(XCSG.name, (targetMethod + "(...)"));
							callsite.tag(XCSG.DynamicDispatchCallSite);
							
							// place the callsite node inside the atlas method node
							Edge callsiteContainsEdge = Graph.U.createEdge(atlasMethodNode, callsite);
							callsiteContainsEdge.tag(XCSG.Contains);
							
							// create the method signature edge
							// TODO: this should be more precise and consider parameters (using signature)
							AtlasSet<Node> targetMethodNodes = Common.methodSelect(targetMethodPackage, targetMethodType, targetMethod).eval().nodes();
							for(Node targetMethodNode : targetMethodNodes){
								Edge invokedSignatureEdge = Graph.U.createEdge(callsite, targetMethodNode);
								invokedSignatureEdge.tag(XCSG.InvokedSignature);
							}
							
							// create the this node
							Node thisNode = Graph.U.createNode();
							thisNode.tag(XCSG.IdentityPass);
							thisNode.putAttr(XCSG.name, "this.");
	
							// place the this node inside the atlas method node
							Edge thisContainsEdge = Graph.U.createEdge(atlasMethodNode, thisNode);
							thisContainsEdge.tag(XCSG.Contains);
							
							// create the receiver object
							Node receiverObject = Graph.U.createNode();
							receiverObject.tag(XCSG.DataFlow_Node);
							String classPackage = classNode.name.replace("/", ".").substring(0, owner.lastIndexOf("."));
							if(classPackage.equals(targetMethodPackage)){
								receiverObject.putAttr(XCSG.name, "this");
							} else {
								receiverObject.putAttr(XCSG.name, "receiver");
							}
							
							// place the receiver object node inside the atlas method node
							Edge receiverContainsEdge = Graph.U.createEdge(atlasMethodNode, receiverObject);
							receiverContainsEdge.tag(XCSG.Contains);
							
							// create a data flow edge from the receiver object to the this node
							Edge dataFlowEdge = Graph.U.createEdge(receiverObject, thisNode);
							dataFlowEdge.tag(XCSG.LocalDataFlow);
							
							// pass the identity node
							Edge receiverIdentityPassedToEdge = Graph.U.createEdge(thisNode, callsite);
							receiverIdentityPassedToEdge.tag(XCSG.IdentityPassedTo);
							
							// add the receiver type of edge
							// should just be one, but being cautious just in case there are colliding types
							for(Node receiverType : Common.typeSelect(targetMethodPackage, targetMethodType).eval().nodes()){
								Edge receiverTypeOfEdge = Graph.U.createEdge(receiverObject, receiverType);
								receiverTypeOfEdge.tag(XCSG.TypeOf);
							}
						} else if(instruction.getOpcode() == Opcodes.INVOKESPECIAL) {
							//https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokespecial
							// new instantiation
							if(instruction.name.equals("<init>")){
								String instantiationType = instruction.owner.replace("/", "."); // ex: java/lang/UnsupportedOperationException
								String parameters = instruction.desc; // ex: (Ljava/lang/String;)V
							} else {
								// TODO: super method calls...
								String targetMethodPackage = instruction.owner.replace("/", "."); // ex: java/util/Locale
								String targetMethod = instruction.name; // ex: getDisplayString
								String targetMethodParameters = instruction.desc; // ex: (Ljava/lang/String;Ljava/util/Locale;I)Ljava/lang/String;
							}
						}
					} else if (abstractInstruction instanceof MultiANewArrayInsnNode) {
						MultiANewArrayInsnNode instruction = (MultiANewArrayInsnNode) abstractInstruction;
					} else if (abstractInstruction instanceof TableSwitchInsnNode) {
						TableSwitchInsnNode instruction = (TableSwitchInsnNode) abstractInstruction;
					} else if (abstractInstruction instanceof TypeInsnNode) {
						TypeInsnNode instruction = (TypeInsnNode) abstractInstruction;
					} else if (abstractInstruction instanceof VarInsnNode) {
						VarInsnNode instruction = (VarInsnNode) abstractInstruction;
					}
				}
			} else {
				Log.warning("Unable to locate corresponding Atlas method for " + methodNode.name);
			}
    	}
    }
	
}
