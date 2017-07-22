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
	
	/**
	 * Configures general logging
	 */
	public static void enableGeneralLogging(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(GENERAL_LOGGING, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable RA
	 */
	public static final String RA_ANALYSIS = "RA_ANALYSIS";
	public static final Boolean RA_ANALYSIS_DEFAULT = true;
	private static boolean raAnalysisValue = RA_ANALYSIS_DEFAULT;
	
	public static boolean isReachabilityAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return raAnalysisValue;
	}
	
	/**
	 * Configures Reachability Analysis
	 */
	public static void enableReachabilityAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(RA_ANALYSIS, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable CHA
	 */
	public static final String CHA_ANALYSIS = "CHA_ANALYSIS";
	public static final Boolean CHA_ANALYSIS_DEFAULT = true;
	private static boolean chaAnalysisValue = CHA_ANALYSIS_DEFAULT;
	
	public static boolean isClassHierarchyAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return chaAnalysisValue;
	}
	
	/**
	 * Configures Class Hierarchy Analysis
	 */
	public static void enableClassHierarchyAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(CHA_ANALYSIS, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable RTA
	 */
	public static final String RTA_ANALYSIS = "RTA_ANALYSIS";
	public static final Boolean RTA_ANALYSIS_DEFAULT = true;
	private static boolean rtaAnalysisValue = RTA_ANALYSIS_DEFAULT;

	public static boolean isRapidTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return rtaAnalysisValue;
	}
	
	/**
	 * Configures Rapid Type Analysis
	 */
	public static void enableRapidTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(RTA_ANALYSIS, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable MTA
	 */
	public static final String MTA_ANALYSIS = "MTA_ANALYSIS";
	public static final Boolean MTA_ANALYSIS_DEFAULT = true;
	private static boolean mtaAnalysisValue = MTA_ANALYSIS_DEFAULT;

	public static boolean isMethodTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return mtaAnalysisValue;
	}
	
	/**
	 * Configures Method Type Analysis
	 */
	public static void enableMethodTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(MTA_ANALYSIS, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable FTA
	 */
	public static final String FTA_ANALYSIS = "FTA_ANALYSIS";
	public static final Boolean FTA_ANALYSIS_DEFAULT = true;
	private static boolean ftaAnalysisValue = FTA_ANALYSIS_DEFAULT;

	public static boolean isFieldTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return ftaAnalysisValue;
	}
	
	/**
	 * Configures Field Type Analysis
	 */
	public static void enableFieldTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(FTA_ANALYSIS, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable ETA
	 */
	public static final String ETA_ANALYSIS = "ETA_ANALYSIS";
	public static final Boolean ETA_ANALYSIS_DEFAULT = true;
	private static boolean etaAnalysisValue = ETA_ANALYSIS_DEFAULT;

	public static boolean isExceptionTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return etaAnalysisValue;
	}
	
	/**
	 * Configures Exception Type Analysis
	 */
	public static void enableExceptionTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(ETA_ANALYSIS, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable XTA
	 */
	public static final String XTA_ANALYSIS = "XTA_ANALYSIS";
	public static final Boolean XTA_ANALYSIS_DEFAULT = true;
	private static boolean xtaAnalysisValue = XTA_ANALYSIS_DEFAULT;

	public static boolean isClassicHybridTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return xtaAnalysisValue;
	}
	
	/**
	 * Configures Classic Hybrid Type Analysis
	 */
	public static void enableClassicHybridTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(XTA_ANALYSIS, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable XTA2
	 */
	public static final String XTA2_ANALYSIS = "XTA2_ANALYSIS";
	public static final Boolean XTA2_ANALYSIS_DEFAULT = true;
	private static boolean xta2AnalysisValue = XTA2_ANALYSIS_DEFAULT;

	public static boolean isHybridTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return xta2AnalysisValue;
	}
	
	/**
	 * Configures Hybrid Type Analysis
	 */
	public static void enableHybridTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(XTA2_ANALYSIS, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable ZeroCFA
	 */
	public static final String ZEROCFA_ANALYSIS = "ZeroCFA_ANALYSIS";
	public static final Boolean ZEROCFA_ANALYSIS_DEFAULT = true;
	private static boolean zerocfaAnalysisValue = ZEROCFA_ANALYSIS_DEFAULT;

	public static boolean isZeroCFAEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return zerocfaAnalysisValue;
	}
	
	/**
	 * Configures 0-CFA Analysis
	 */
	public static void enableZeroCFAAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(ZEROCFA_ANALYSIS, enabled);
		loadPreferences();
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
	
	/**
	 * Configures library call graph construction
	 */
	public static void enableLibraryCallGraphConstruction(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(LIBRARY_CALL_GRAPH_CONSTRUCTION, enabled);
		loadPreferences();
	}
	
	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setDefault(GENERAL_LOGGING, GENERAL_LOGGING_DEFAULT);
		preferences.setDefault(LIBRARY_CALL_GRAPH_CONSTRUCTION, LIBRARY_CALL_GRAPH_CONSTRUCTION_DEFAULT);
		preferences.setDefault(CHA_ANALYSIS, CHA_ANALYSIS_DEFAULT);
		preferences.setDefault(RTA_ANALYSIS, RTA_ANALYSIS_DEFAULT);
		preferences.setDefault(MTA_ANALYSIS, MTA_ANALYSIS_DEFAULT);
		preferences.setDefault(FTA_ANALYSIS, FTA_ANALYSIS_DEFAULT);
		preferences.setDefault(ETA_ANALYSIS, ETA_ANALYSIS_DEFAULT);
		preferences.setDefault(XTA_ANALYSIS, XTA_ANALYSIS_DEFAULT);
		preferences.setDefault(XTA2_ANALYSIS, XTA2_ANALYSIS_DEFAULT);
		preferences.setDefault(ZEROCFA_ANALYSIS, ZEROCFA_ANALYSIS_DEFAULT);
	}
	
	/**
	 * Restores the default preferences
	 */
	public static void restoreDefaults() {
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(GENERAL_LOGGING, GENERAL_LOGGING_DEFAULT);
		preferences.setValue(LIBRARY_CALL_GRAPH_CONSTRUCTION, LIBRARY_CALL_GRAPH_CONSTRUCTION_DEFAULT);
		preferences.setValue(CHA_ANALYSIS, CHA_ANALYSIS_DEFAULT);
		preferences.setValue(RTA_ANALYSIS, RTA_ANALYSIS_DEFAULT);
		preferences.setValue(MTA_ANALYSIS, MTA_ANALYSIS_DEFAULT);
		preferences.setValue(FTA_ANALYSIS, FTA_ANALYSIS_DEFAULT);
		preferences.setValue(ETA_ANALYSIS, ETA_ANALYSIS_DEFAULT);
		preferences.setValue(XTA_ANALYSIS, XTA_ANALYSIS_DEFAULT);
		preferences.setValue(XTA2_ANALYSIS, XTA2_ANALYSIS_DEFAULT);
		preferences.setValue(ZEROCFA_ANALYSIS, ZEROCFA_ANALYSIS_DEFAULT);
		loadPreferences();
	}
	
	/**
	 * Loads or refreshes current preference values
	 */
	public static void loadPreferences() {
		try {
			IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
			generalLoggingValue = preferences.getBoolean(GENERAL_LOGGING);
			libraryCallGraphConstructionValue = preferences.getBoolean(LIBRARY_CALL_GRAPH_CONSTRUCTION);
			chaAnalysisValue = preferences.getBoolean(CHA_ANALYSIS);
			rtaAnalysisValue = preferences.getBoolean(RTA_ANALYSIS);
			mtaAnalysisValue = preferences.getBoolean(MTA_ANALYSIS);
			ftaAnalysisValue = preferences.getBoolean(FTA_ANALYSIS);
			etaAnalysisValue = preferences.getBoolean(ETA_ANALYSIS);
			xtaAnalysisValue = preferences.getBoolean(XTA_ANALYSIS);
			xta2AnalysisValue = preferences.getBoolean(XTA2_ANALYSIS);
			zerocfaAnalysisValue = preferences.getBoolean(ZEROCFA_ANALYSIS);
		} catch (Exception e){
			Log.warning("Error accessing call graph preferences, using defaults...", e);
		}
		initialized = true;
	}

}
