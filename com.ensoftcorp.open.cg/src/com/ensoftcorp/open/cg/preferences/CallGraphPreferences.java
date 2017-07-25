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
	private static boolean generalLoggingAlgorithmValue = GENERAL_LOGGING_DEFAULT;
	
	public static boolean isGeneralLoggingEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return generalLoggingAlgorithmValue;
	}
	
	/**
	 * Configures general logging
	 */
	public static void enableGeneralLogging(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(GENERAL_LOGGING, enabled);
		loadPreferences();
	}
	
	public static final String INFER_LIBRARY_CALLBACK_ENTRY_POINTS = "INFER_LIBRARY_CALLBACK_ENTRY_POINTS";
	public static final Boolean INFER_LIBRARY_CALLBACK_ENTRY_POINTS_DEFAULT = true;
	private static boolean inferLibraryCallbackEntryPointsValue = INFER_LIBRARY_CALLBACK_ENTRY_POINTS_DEFAULT;
	
	public static boolean isLibraryCallbackEntryPointsInferenceEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return inferLibraryCallbackEntryPointsValue;
	}
	
	/**
	 * Configures library callback entry point inference
	 */
	public static void enableLibraryCallbackEntryPointsInfererence(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(INFER_LIBRARY_CALLBACK_ENTRY_POINTS, enabled);
		loadPreferences();
	}
	
	public static final String LIBRARY_CALL_GRAPH_CONSTRUCTION = "LIBRARY_CALL_GRAPH_CONSTRUCTION";
	public static final Boolean LIBRARY_CALL_GRAPH_CONSTRUCTION_DEFAULT = false;
	private static boolean libraryCallGraphConstructionAlgorithmValue = LIBRARY_CALL_GRAPH_CONSTRUCTION_DEFAULT;
	
	public static boolean isLibraryCallGraphConstructionEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return libraryCallGraphConstructionAlgorithmValue;
	}
	
	/**
	 * Configures library call graph construction
	 */
	public static void enableLibraryCallGraphConstruction(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(LIBRARY_CALL_GRAPH_CONSTRUCTION, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable RA
	 */
	public static final String RA_ALGORITHM = "RA_ALGORITHM";
	public static final Boolean RA_ALGORITHM_DEFAULT = false;
	private static boolean raAlgorithmValue = RA_ALGORITHM_DEFAULT;
	
	public static boolean isReachabilityAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return raAlgorithmValue;
	}
	
	/**
	 * Configures Reachability Analysis
	 */
	public static void enableReachabilityAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(RA_ALGORITHM, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable CHA
	 */
	public static final String CHA_ALGORITHM = "CHA_ALGORITHM";
	public static final Boolean CHA_ALGORITHM_DEFAULT = false;
	private static boolean chaAlgorithmValue = CHA_ALGORITHM_DEFAULT;
	
	public static boolean isClassHierarchyAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return chaAlgorithmValue;
	}
	
	/**
	 * Configures Class Hierarchy Analysis
	 */
	public static void enableClassHierarchyAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(CHA_ALGORITHM, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable RRTA
	 */
	public static final String RRTA_ALGORITHM = "RRTA_ALGORITHM";
	public static final Boolean RRTA_ALGORITHM_DEFAULT = true;
	private static boolean rrtaAlgorithmValue = RRTA_ALGORITHM_DEFAULT;

	public static boolean isReallyRapidTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return rrtaAlgorithmValue;
	}
	
	/**
	 * Configures Rapid Type Analysis
	 */
	public static void enableReallyRapidTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(RRTA_ALGORITHM, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable RTA
	 */
	public static final String RTA_ALGORITHM = "RTA_ALGORITHM";
	public static final Boolean RTA_ALGORITHM_DEFAULT = false;
	private static boolean rtaAlgorithmValue = RTA_ALGORITHM_DEFAULT;

	public static boolean isRapidTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return rtaAlgorithmValue;
	}
	
	/**
	 * Configures Rapid Type Analysis
	 */
	public static void enableRapidTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(RTA_ALGORITHM, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable MTA
	 */
	public static final String MTA_ALGORITHM = "MTA_ALGORITHM";
	public static final Boolean MTA_ALGORITHM_DEFAULT = false;
	private static boolean mtaAlgorithmValue = MTA_ALGORITHM_DEFAULT;

	public static boolean isMethodTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return mtaAlgorithmValue;
	}
	
	/**
	 * Configures Method Type Analysis
	 */
	public static void enableMethodTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(MTA_ALGORITHM, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable FTA
	 */
	public static final String FTA_ALGORITHM = "FTA_ALGORITHM";
	public static final Boolean FTA_ALGORITHM_DEFAULT = false;
	private static boolean ftaAlgorithmValue = FTA_ALGORITHM_DEFAULT;

	public static boolean isFieldTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return ftaAlgorithmValue;
	}
	
	/**
	 * Configures Field Type Analysis
	 */
	public static void enableFieldTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(FTA_ALGORITHM, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable ETA
	 */
	public static final String ETA_ALGORITHM = "ETA_ALGORITHM";
	public static final Boolean ETA_ALGORITHM_DEFAULT = false;
	private static boolean etaAlgorithmValue = ETA_ALGORITHM_DEFAULT;

	public static boolean isExceptionTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return etaAlgorithmValue;
	}
	
	/**
	 * Configures Exception Type Analysis
	 */
	public static void enableExceptionTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(ETA_ALGORITHM, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable XTA
	 */
	public static final String XTA_ALGORITHM = "XTA_ALGORITHM";
	public static final Boolean XTA_ALGORITHM_DEFAULT = false;
	private static boolean xtaAlgorithmValue = XTA_ALGORITHM_DEFAULT;

	public static boolean isClassicHybridTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return xtaAlgorithmValue;
	}
	
	/**
	 * Configures Classic Hybrid Type Analysis
	 */
	public static void enableClassicHybridTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(XTA_ALGORITHM, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable XTA2
	 */
	public static final String XTA2_ALGORITHM = "XTA2_ALGORITHM";
	public static final Boolean XTA2_ALGORITHM_DEFAULT = false;
	private static boolean xta2AlgorithmValue = XTA2_ALGORITHM_DEFAULT;

	public static boolean isHybridTypeAnalysisEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return xta2AlgorithmValue;
	}
	
	/**
	 * Configures Hybrid Type Analysis
	 */
	public static void enableHybridTypeAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(XTA2_ALGORITHM, enabled);
		loadPreferences();
	}
	
	/**
	 * Enable/disable ZeroCFA
	 */
	public static final String ZEROCFA_ALGORITHM = "ZEROCFA_ALGORITHM";
	public static final Boolean ZEROCFA_ALGORITHM_DEFAULT = false;
	private static boolean zerocfaAlgorithmValue = ZEROCFA_ALGORITHM_DEFAULT;

	public static boolean isZeroCFAEnabled(){
		if(!initialized){
			loadPreferences();
		}
		return zerocfaAlgorithmValue;
	}
	
	/**
	 * Configures 0-CFA Analysis
	 */
	public static void enableZeroCFAAnalysis(boolean enabled){
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(ZEROCFA_ALGORITHM, enabled);
		loadPreferences();
	}
	
	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setDefault(GENERAL_LOGGING, GENERAL_LOGGING_DEFAULT);
		preferences.setDefault(INFER_LIBRARY_CALLBACK_ENTRY_POINTS, INFER_LIBRARY_CALLBACK_ENTRY_POINTS_DEFAULT);
		preferences.setDefault(LIBRARY_CALL_GRAPH_CONSTRUCTION, LIBRARY_CALL_GRAPH_CONSTRUCTION_DEFAULT);
		preferences.setDefault(RA_ALGORITHM, RA_ALGORITHM_DEFAULT);
		preferences.setDefault(CHA_ALGORITHM, CHA_ALGORITHM_DEFAULT);
		preferences.setDefault(RRTA_ALGORITHM, RRTA_ALGORITHM_DEFAULT);
		preferences.setDefault(RTA_ALGORITHM, RTA_ALGORITHM_DEFAULT);
		preferences.setDefault(MTA_ALGORITHM, MTA_ALGORITHM_DEFAULT);
		preferences.setDefault(FTA_ALGORITHM, FTA_ALGORITHM_DEFAULT);
		preferences.setDefault(ETA_ALGORITHM, ETA_ALGORITHM_DEFAULT);
		preferences.setDefault(XTA_ALGORITHM, XTA_ALGORITHM_DEFAULT);
		preferences.setDefault(XTA2_ALGORITHM, XTA2_ALGORITHM_DEFAULT);
		preferences.setDefault(ZEROCFA_ALGORITHM, ZEROCFA_ALGORITHM_DEFAULT);
	}
	
	/**
	 * Restores the default preferences
	 */
	public static void restoreDefaults() {
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		preferences.setValue(GENERAL_LOGGING, GENERAL_LOGGING_DEFAULT);
		preferences.setValue(INFER_LIBRARY_CALLBACK_ENTRY_POINTS, INFER_LIBRARY_CALLBACK_ENTRY_POINTS_DEFAULT);
		preferences.setValue(LIBRARY_CALL_GRAPH_CONSTRUCTION, LIBRARY_CALL_GRAPH_CONSTRUCTION_DEFAULT);
		preferences.setValue(RA_ALGORITHM, RA_ALGORITHM_DEFAULT);
		preferences.setValue(CHA_ALGORITHM, CHA_ALGORITHM_DEFAULT);
		preferences.setValue(RRTA_ALGORITHM, RRTA_ALGORITHM_DEFAULT);
		preferences.setValue(RTA_ALGORITHM, RTA_ALGORITHM_DEFAULT);
		preferences.setValue(MTA_ALGORITHM, MTA_ALGORITHM_DEFAULT);
		preferences.setValue(FTA_ALGORITHM, FTA_ALGORITHM_DEFAULT);
		preferences.setValue(ETA_ALGORITHM, ETA_ALGORITHM_DEFAULT);
		preferences.setValue(XTA_ALGORITHM, XTA_ALGORITHM_DEFAULT);
		preferences.setValue(XTA2_ALGORITHM, XTA2_ALGORITHM_DEFAULT);
		preferences.setValue(ZEROCFA_ALGORITHM, ZEROCFA_ALGORITHM_DEFAULT);
		loadPreferences();
	}
	
	/**
	 * Loads or refreshes current preference values
	 */
	public static void loadPreferences() {
		try {
			IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
			generalLoggingAlgorithmValue = preferences.getBoolean(GENERAL_LOGGING);
			inferLibraryCallbackEntryPointsValue = preferences.getBoolean(INFER_LIBRARY_CALLBACK_ENTRY_POINTS);
			libraryCallGraphConstructionAlgorithmValue = preferences.getBoolean(LIBRARY_CALL_GRAPH_CONSTRUCTION);
			raAlgorithmValue = preferences.getBoolean(RA_ALGORITHM);
			chaAlgorithmValue = preferences.getBoolean(CHA_ALGORITHM);
			rrtaAlgorithmValue = preferences.getBoolean(RRTA_ALGORITHM);
			rtaAlgorithmValue = preferences.getBoolean(RTA_ALGORITHM);
			mtaAlgorithmValue = preferences.getBoolean(MTA_ALGORITHM);
			ftaAlgorithmValue = preferences.getBoolean(FTA_ALGORITHM);
			etaAlgorithmValue = preferences.getBoolean(ETA_ALGORITHM);
			xtaAlgorithmValue = preferences.getBoolean(XTA_ALGORITHM);
			xta2AlgorithmValue = preferences.getBoolean(XTA2_ALGORITHM);
			zerocfaAlgorithmValue = preferences.getBoolean(ZEROCFA_ALGORITHM);
		} catch (Exception e){
			Log.warning("Error accessing call graph preferences, using defaults...", e);
		}
		initialized = true;
	}

}
