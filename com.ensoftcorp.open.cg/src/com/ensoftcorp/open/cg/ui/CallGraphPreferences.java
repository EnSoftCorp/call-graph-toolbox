package com.ensoftcorp.open.cg.ui;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.open.cg.Activator;
import com.ensoftcorp.open.toolbox.commons.utils.MappingUtils;

/**
 * UI for setting call graph construction preferences
 * 
 * @author Ben Holland
 */
public class CallGraphPreferences extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	
	// enable/disable call graph construction for libraries
	public static final String ENABLE_LIBRARY_CALL_GRAPH_CONSTRUCTION_BOOLEAN = "ENABLE_LIBRARY_CALL_GRAPH_CONSTRUCTION_BOOLEAN";
	public static final String ENABLE_LIBRARY_CALL_GRAPH_CONSTRUCTION_DESCRIPTION = "Enable Library Call Graph Construction";
	
	public static boolean isLibraryCallGraphConstructionEnabled(){
		boolean result = false; // library call graph construction is disabled by default
		try {
			result = Activator.getDefault().getPreferenceStore().getBoolean(ENABLE_LIBRARY_CALL_GRAPH_CONSTRUCTION_BOOLEAN);
		} catch (Exception e){
			Log.error("Error accessing call graph construction preferences.", e);
		}
		return result;
	}
	
	private static boolean changeListenerAdded = false;
	
	public CallGraphPreferences() {
		super(GRID);
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Configure preferences for the Call Graph Toolbox plugin.");
		if(!changeListenerAdded){
			getPreferenceStore().addPropertyChangeListener(new IPropertyChangeListener() {
				@Override
				public void propertyChange(org.eclipse.jface.util.PropertyChangeEvent event) {
					if (event.getProperty() == CallGraphPreferences.ENABLE_LIBRARY_CALL_GRAPH_CONSTRUCTION_BOOLEAN) {
						Display.getDefault().asyncExec(new Runnable(){
							@Override
							public void run() {
								MessageBox dialog = new MessageBox(Display.getDefault().getActiveShell(), SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
								dialog.setText("Preference Change");
								dialog.setMessage("Changing call graph preferences requires rebuilding the Atlas code map. Would you like to re-map the workspace now?");
								int response = dialog.open(); 
								if(response == SWT.OK){
									try {
										MappingUtils.indexWorkspace();
									} catch (Throwable e) {
										Log.error("Mapping workspace failed.", e);
									}
								}
							}
						});
					}
				}
			});
			changeListenerAdded = true;
		}	
	}

	@Override
	protected void createFieldEditors() {
		addField(new BooleanFieldEditor(ENABLE_LIBRARY_CALL_GRAPH_CONSTRUCTION_BOOLEAN, "&" + ENABLE_LIBRARY_CALL_GRAPH_CONSTRUCTION_DESCRIPTION, getFieldEditorParent()));
	}

}
