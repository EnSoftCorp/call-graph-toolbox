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
			Node correspondingMethod = null;
			AtlasSet<Node> correspondingMethods = methods.selectNode(XCSG.name, methodNode.name).eval().nodes();
			if(correspondingMethods.size() == 1){
				correspondingMethod = correspondingMethods.one();
			} else {
				// TODO: implement
				// filter by number of parameters
				// filter by type of parameters
			}
			
			if(correspondingMethod != null){
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
							String targetMethodPackage = instruction.owner.replace("/", "."); // ex: java/util/AbstractCollection, java/util/Iterator
							String targetMethod = instruction.name; // ex: size, hasNext
							String targetMethodParameters = instruction.desc; // ex: ()I, ()Z
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
				Log.warning("Unable to located corresponding Atlas method for " + methodNode.name);
			}
    	}
    }
	
}
