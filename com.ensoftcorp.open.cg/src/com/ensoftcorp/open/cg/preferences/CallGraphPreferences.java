package com.ensoftcorp.open.cg.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.ensoftcorp.open.cg.Activator;
import com.ensoftcorp.open.cg.log.Log;

public class CallGraphPreferences extends AbstractPreferenceInitializer {

	private static boolean initialized = false;
	
	/**
	 * Enable/disable general logging
	 */
	public static final String GENERAL_LOGGING = "GENERAL_LOGGING";
	public static final Boolean GENERAL_LOGGING_DEFAULT = true;
	private static boolean generalLoggingValue = GENERAL_LOGGING_DEFAULT;
	
	public static boolean isGeneralLoggingEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return generalLoggingValue;
	}
	
	public static final String LIBRARY_CALL_GRAPH_CONSTRUCTION = "LIBRARY_CALL_GRAPH_CONSTRUCTION";
	public static final Boolean LIBRARY_CALL_GRAPH_CONSTRUCTION_DEFAULT = false;
	private static boolean libraryCallGraphConstructionValue = LIBRARY_CALL_GRAPH_CONSTRUCTION_DEFAULT;
	
	public static boolean isLibraryCallGraphConstructionEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return libraryCallGraphConstructionValue;
	}
	
	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setDefault(GENERAL_LOGGING, GENERAL_LOGGING_DEFAULT);
		preferences.setDefault(LIBRARY_CALL_GRAPH_CONSTRUCTION, LIBRARY_CALL_GRAPH_CONSTRUCTION_DEFAULT);
	}
	
	/**
	 * Loads or refreshes current preference values
	 */
	public static void loadPreferences() {
		try {
			IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
			generalLoggingValue = preferences.getBoolean(GENERAL_LOGGING);
			libraryCallGraphConstructionValue = preferences.getBoolean(LIBRARY_CALL_GRAPH_CONSTRUCTION);
		} catch (Exception e){
			Log.warning("Error accessing call graph preferences, using defaults...", e);
		}
		initialized = true;
	}

}
