package com.dynamo.cr.editor.handlers;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.handlers.HandlerUtil;

import com.dynamo.cr.editor.Activator;
import com.dynamo.cr.editor.core.EditorUtil;
import com.dynamo.cr.editor.preferences.PreferenceConstants;

public class ReloadResourceHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        final IEditorPart editor = HandlerUtil.getActiveEditor(event);
        if (editor != null)
        {
            IEditorInput input = editor.getEditorInput();
            if (input instanceof IFileEditorInput) {
                final IFileEditorInput fileInput = (IFileEditorInput) input;
                IPath path = fileInput.getFile().getFullPath();

                IContainer contentRoot = EditorUtil.findContentRoot(fileInput.getFile());
                path = path.makeRelativeTo(contentRoot.getFullPath());

                final String stringPath = path.toPortableString() + "c";
                editor.getSite().getPage().saveEditor(editor, false);

                Job job = new Job("reload") {
                    @Override
                    protected IStatus run(IProgressMonitor monitor) {
                        try {
                            IProject project = fileInput.getFile().getProject();
                            Map<String, String> args = new HashMap<String, String>();
                            final IPreferenceStore store = Activator.getDefault().getPreferenceStore();
                            final boolean localBranch = store.getBoolean(PreferenceConstants.P_USE_LOCAL_BRANCHES);
                            if (localBranch)
                                args.put("location", "local");
                            else
                                args.put("location", "remote");

                            project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD,  "com.dynamo.cr.editor.builders.contentbuilder", args, monitor);
                            URL url = new URL("http://localhost:8001/reload/" + stringPath);
                            URLConnection c = url.openConnection();
                            InputStream is = new BufferedInputStream(c.getInputStream());
                            int n = is.read();
                            while (n != -1)
                                n = is.read();
                            is.close();

                        } catch (Throwable e) {
                            Activator.getDefault().logger.warning(e.getMessage());
                        }
                        return Status.OK_STATUS;
                    }
                };

                job.schedule();
            }
        }

        return null;
    }
}
