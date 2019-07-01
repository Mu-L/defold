package com.dynamo.cr.sceneed.handlers;

import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;

import com.dynamo.cr.sceneed.Activator;

public class ShowGridHandler extends AbstractHandler implements IElementUpdater {
    public static final String PREFERENCE_PREFIX = "showGrid";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        String prefsName = getPreferenceName(HandlerUtil.getActiveEditorId(event));
        boolean enabled = store.getBoolean(prefsName);
        store.setValue(prefsName, !enabled);
        return null;
    }

    @Override
    public void updateElement(UIElement element, @SuppressWarnings("rawtypes") Map parameters) {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        boolean enabled = store.getBoolean(getPreferenceName(Activator.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor().getEditorSite().getId()));
        element.setChecked(!enabled);
    }

    public static String getPreferenceName(String editorId) {
        return PREFERENCE_PREFIX + "_" + editorId;
    }
}