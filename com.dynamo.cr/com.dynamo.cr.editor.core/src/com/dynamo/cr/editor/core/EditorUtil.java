package com.dynamo.cr.editor.core;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditorUtil {
    private static Logger logger = LoggerFactory.getLogger(EditorUtil.class);

    /**
     * Get the game.project file by searching downwards in the file system.
     * @param container the container to start searching
     * @return game.project file. null if the directory structure does not contain it
     */
    public static IFile findGameProjectFile(IContainer container) {
        IContainer c = container;
        while (c != null) {
            IFile f = c.getFile(new Path("game.project"));
            if (f.exists()) {
                return f;
            }
            c = c.getParent();
        }
        return null;
    }

    /**
     * Get the content-root, ie the container that contains the file game.project given a file within the content directory structure.
     * @param file the file to the content root for
     * @return content-root container. null of game.project doesn't exists within the directory structure.
     */
    public static IContainer findContentRoot(IResource file) {
        IContainer c = file.getParent();
        IFile f = findGameProjectFile(c);
        if (f != null) {
            return f.getParent();
        }
        return null;
    }

    /**
     * Get content root given a project
     * @param project project to get content root for
     * @return content root
     */
    public static IFolder getContentRoot(IProject project) {
        // NOTE: In the "future" we might change "content". Thats why we have this method.
        return project.getFolder("content");
    }

    /**
     * Get current cr project or actually the first project with nature
     * "com.dynamo.cr.editor.core.crnature" set. We assume a single project.
     * @return project. null if project can be found.
     */
    public static IProject getProject() {
        try {
            IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
            for (IProject project : projects) {
                if (project.isOpen() && project.hasNature("com.dynamo.cr.editor.core.crnature")) {
                    return project;
                }
            }
        } catch (CoreException e) {
            logger.error("Unable to locate project", e);
            return null;
        }
        return null;
    }

    /**
     * Create an absolute resource path, from content root, to a resource
     * @param resource resource to create resource path for
     * @return Absolute path to file
     */
    public static String makeResourcePath(IResource resource) {
        return makeResourcePath(findContentRoot(resource), resource);
    }

    /**
     * Create an absolute resource path, from content root, to a resource
     *
     * @param contentRoot
     *            root the path will be relative to
     * @param resource
     *            resource to create resource path for
     * @return Absolute path to file
     */
    public static String makeResourcePath(IContainer contentRoot, IResource resource) {
        if (contentRoot == null) {
            throw new RuntimeException(String.format("Content root for file '%s' not found", resource.getFullPath()
                    .toPortableString()));
        }
        String path = "/" + resource.getFullPath().makeRelativeTo(contentRoot.getFullPath()).toPortableString();
        return path;
    }

    /**
     * Return whether the editor is currently running in development mode or
     * not. Based on if the "osgi.dev" system property is set or not.
     */
    public static boolean isDev() {
        String dev = System.getProperty("osgi.dev");
        return dev != null;
    }

    private static Boolean isMac = null;

    /**
     * Return whether the editor is currently running in OS X or not. Based on
     * if the "os.name" system property has "mac" in it.
     */
    public static boolean isMac() {
        if (isMac == null) {
            isMac = System.getProperty("os.name").toLowerCase().indexOf("mac") >= 0;
        }
        return isMac;
    }
}