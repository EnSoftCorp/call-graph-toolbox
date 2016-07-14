package com.ensoftcorp.open.cg.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.PreferencesUtil;

/**
 * A menu handler for configuring the call graph toolbox preferences
 * 
 * @author Ben Holland
 */
public class CallGraphPreferencesHandler extends AbstractHandler {
	public CallGraphPreferencesHandler() {}

	/**
	 * Opens the call graph construction preferences
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		String id = "com.ensoftcorp.open.cg.ui.preferences";
		return PreferencesUtil.createPreferenceDialogOn(Display.getDefault().getActiveShell(), id, new String[] {id}, null).open();
	}
	
}
