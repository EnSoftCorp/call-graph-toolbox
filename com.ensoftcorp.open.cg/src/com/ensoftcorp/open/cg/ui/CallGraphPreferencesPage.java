package com.ensoftcorp.open.cg.ui;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.ensoftcorp.open.cg.Activator;
import com.ensoftcorp.open.cg.log.Log;
import com.ensoftcorp.open.cg.preferences.CallGraphPreferences;
import com.ensoftcorp.open.commons.utils.MappingUtils;

/**
 * UI for setting call graph construction preferences
 * 
 * @author Ben Holland
 */
public class CallGraphPreferencesPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	private static final String GENERAL_LOGGING_DESCRIPTION = "Enable General Logging";
	private static final String LIBRARY_CALL_GRAPH_CONSTRUCTION_DESCRIPTION = "Enable Library Call Graph Construction";

	private static boolean changeListenerAdded = false;

	public CallGraphPreferencesPage() {
		super(GRID);
	}

	@Override
	public void init(IWorkbench workbench) {
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		setPreferenceStore(preferences);
		setDescription("Configure preferences for the Call Graph Toolbox plugin.");

		// use to update cached values if user edits a preference
		if (!changeListenerAdded) {
			getPreferenceStore().addPropertyChangeListener(new IPropertyChangeListener() {
				@Override
				public void propertyChange(org.eclipse.jface.util.PropertyChangeEvent event) {
					CallGraphPreferences.loadPreferences();
					if (event.getProperty() == CallGraphPreferences.LIBRARY_CALL_GRAPH_CONSTRUCTION) {
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
		addField(new BooleanFieldEditor(CallGraphPreferences.GENERAL_LOGGING, "&" + GENERAL_LOGGING_DESCRIPTION, getFieldEditorParent()));
		addField(new BooleanFieldEditor(CallGraphPreferences.LIBRARY_CALL_GRAPH_CONSTRUCTION, "&" + LIBRARY_CALL_GRAPH_CONSTRUCTION_DESCRIPTION, getFieldEditorParent()));
	}

}
