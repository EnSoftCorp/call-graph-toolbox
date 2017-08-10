package com.ensoftcorp.open.cg.summary;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarException;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * A wrapper around the Java zip utilities to inspect Jar archives
 * 
 * @author Ben Holland
 */
public class JarInspector {

	/**
	 * The directory separator character for archive files as a string
	 */
	public static final String SEPERATOR = "/";
	
	/**
	 * The directory that stores the manifest and jar signatures
	 */
	public static final String META_INF = "META-INF";
	
	private HashMap<String,JarEntry> jarEntries = new HashMap<String,JarEntry>();
	private File jarFile;
	private Manifest manifest;
	
	/**
	 * Creates a new JarModifier with the given archive to be modified
	 * 
	 * @param jarFile The archive to be modified.
	 * 
	 * @throws JarException
	 * @throws IOException
	 */
	public JarInspector(File jarFile) throws JarException, IOException {
		this.jarFile = jarFile;
		JarFile jar = new JarFile(jarFile);
		// get references to all the archive file entries
		Enumeration<? extends JarEntry> enumerator = jar.entries();
		while(enumerator.hasMoreElements()){
			JarEntry currentEntry = (JarEntry) enumerator.nextElement();
			// need to create a new entry to reset properties that will need to be recomputed automatically
//			JarEntry resetEntry = resetEntry(currentEntry); // TODO: Fix
			JarEntry resetEntry = new JarEntry(currentEntry.getName());
			jarEntries.put(currentEntry.getName(), resetEntry);
		}
		
		String manifestPath = META_INF + SEPERATOR + "MANIFEST.MF";
		JarEntry jarManifestEntry = jar.getJarEntry(manifestPath);
		// if manifest not found then search manually
		if (jarManifestEntry == null) {
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				jarManifestEntry = (JarEntry) entries.nextElement();
				if (manifestPath.equalsIgnoreCase(jarManifestEntry.getName())){
					break;
				} else {
					jarManifestEntry = null;
				}
			}
		}
		
		// if we've found a manifest then parse it
		if(jarManifestEntry != null){
			Manifest manifest = new Manifest();
			if (jarManifestEntry != null){
				manifest.read(jar.getInputStream(jarManifestEntry));
			}
			this.manifest = manifest;
		}
		
		jar.close();
	}
	
	public File getJarFile(){
		return jarFile;
	}
	
	public byte[] extractEntry(String entry) throws IOException {
		JarInputStream zin = new JarInputStream(new BufferedInputStream(new FileInputStream(jarFile)));
		JarEntry currentEntry = null;
		while ((currentEntry = zin.getNextJarEntry()) != null) {
			if (currentEntry.getName().equals(entry)) {
				// currentEntry.getSize() may not be accurate, so read bytes into a stream first
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				byte[] buf = new byte[4096];
				while (true) {
					int n = zin.read(buf);
					if (n < 0){
						break;
					}
					baos.write(buf, 0, n);
				}
				zin.close();
				return baos.toByteArray();
			}
		}
		zin.close();
		return null;
	}
	
	/**
	 * Returns the parsed manifest or null if there is no manifest
	 * @return
	 */
	public Manifest getManifest(){
		return manifest;
	}
	
	public HashSet<String> getJarEntrySet(){
		HashSet<String> entryList = new HashSet<String>();
		entryList.addAll(jarEntries.keySet());
		return entryList;
	}
	
}
