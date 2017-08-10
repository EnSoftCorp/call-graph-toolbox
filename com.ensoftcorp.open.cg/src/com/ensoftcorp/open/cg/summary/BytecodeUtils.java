package com.ensoftcorp.open.cg.summary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public class BytecodeUtils {

	/**
	 * Reads a bytecode class file into a ClassNode object
	 * @param classFile
	 * @return
	 * @throws IOException
	 */
	public static ClassNode getClassNode(File classFile) throws IOException {
		byte[] bytes = Files.readAllBytes(classFile.toPath());
		return getClassNode(bytes);
	}

	/**
	 * Reads a bytecode class file into a ClassNode object
	 * @param classFile
	 * @return
	 * @throws IOException
	 */
	public static ClassNode getClassNode(byte[] bytes) {
		ClassReader classReader = new ClassReader(bytes);
		ClassNode classNode = new ClassNode();
		classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
		return classNode;
	}
	
}
