/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2010,2012,2013,2016  Michael Kolling and John Rosenberg
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.extmgr;

import java.awt.Graphics2D;
import java.io.File;
import java.util.*;
import java.util.List;

import bluej.*;
import bluej.debugmgr.ExecutionEvent;
import bluej.extensions.BClassTarget;
import bluej.extensions.event.*;
import bluej.extensions.painter.ExtensionClassTargetPainter;
import bluej.pkgmgr.*;
import bluej.pkgmgr.Package;
import bluej.pkgmgr.Layer;
import bluej.utility.Debug;
import bluej.utility.javafx.FXPlatformSupplier;

import javax.swing.*;
import javafx.application.Platform;
import javafx.stage.Window;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Manages extensions and provides the main interface to them. A
 * singleton.
 * 
 * @author Clive Miller, University of Kent at Canterbury, 2002
 * @author Damiano Bolla, University of Kent at Canterbury, 2003
 * @author Michael Kolling
 */
public class ExtensionsManager
    implements BlueJEventListener
{
    private static ExtensionsManager instance;

    /**
     * Singleton factory method.
     */
    public static synchronized ExtensionsManager getInstance()
    {
        if (instance == null) {
            instance = new ExtensionsManager();
            instance.loadExtensions();
        }
        return instance;
    }

    // ============== instance part ==============

    private List<ExtensionWrapper> extensions;
    private ExtensionPrefManager prefManager = null;

    /**
     * Constructor for the ExtensionsManager object. It is private, as the
     * ExtensionsManager is a singleton.
     */
    private ExtensionsManager()
    {
        // Sync issues should be clear...
        extensions = new ArrayList<ExtensionWrapper>();

        // This must be here, after all has been initialized.
        BlueJEvent.addListener(this);
    }

    /**
     * Loads extensions that are in system and user location.
     */
    private void loadExtensions()
    {
        // Most of the time the systemDirectory will be this.
        File systemDir = new File(Config.getBlueJLibDir(), "extensions");

        String dirPath = Config.getPropString("bluej.extensions.systempath", null);
        // But we allow one to override the default location of system
        // extension.
        if (dirPath != null) {
            systemDir = new File(dirPath);
        }

        // Now we try to load the extensions from the BlueJ system repository.
        loadDirectoryExtensions(systemDir, null);

        // Load extensions that are in a user space location.
        loadDirectoryExtensions(Config.getUserConfigFile("extensions"), null);
    }

    /**
     * Unloads all extensions that are loaded. Normally called just before BlueJ
     * is closing.
     */
    public void unloadExtensions()
    {
        synchronized(extensions) {
            for (Iterator<ExtensionWrapper> iter = extensions.iterator(); iter.hasNext();) {
                ExtensionWrapper aWrapper = iter.next();
                aWrapper.terminate();
                iter.remove();
            }
        }
    }

    /**
     * Searches through the given directory for jar files that contain a valid
     * extension. On finding a loadable extension, try to load it.
     * 
     * @param directory
     *            Where to look for extensions
     * @param project
     *            A project this extension is bound to, or null if
     *            the extension is not bound to a specific project.
     */
    private void loadDirectoryExtensions(File directory, Project project)
    {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (int index = 0; index < files.length; index++) {
            File thisFile = files[index];

            // We do not want to try to make sense of directories
            if (thisFile.isDirectory())
                continue;

            // Skip also files that do not end in .jar
            if (!thisFile.getName().endsWith(".jar"))
                continue;

            // Ok, lets try to get a wrapper up and running
            ExtensionWrapper aWrapper = new ExtensionWrapper(getPrefManager(), thisFile);

            // Loading this wrapper failed miserably, too bad...
            if (!aWrapper.isJarValid()) {
                continue;
            }

            // Let me see if I already have this extension loaded
            if (isWrapperAlreadyLoaded(aWrapper)) {
                continue;
            }

            // Now that all is nice and clean I can safely try to instantiate
            // the extension
            aWrapper.newExtension(project);

            if (aWrapper.isValid()) {
                synchronized (extensions) {
                    extensions.add(aWrapper);
                }
            }
        }
        
        // The last extension may have added a preference panel, but due to the way that is
        // implemented the panel won't be visible as the extension wasn't in the list of
        // valid extensions at that stage.
        getPrefManager().panelRevalidate();
    }

    /**
     * Checks if the loaded wrappers/extensions if is already loaded.
     */
    private boolean isWrapperAlreadyLoaded(ExtensionWrapper thisWrapper)
    {
        String thisClassName = thisWrapper.getExtensionClassName();
        String thisJarName = thisWrapper.getExtensionFileName();

        synchronized(extensions) {            
            for (ExtensionWrapper aWrapper : extensions) {
                String aClassName = aWrapper.getExtensionClassName();
                if (aClassName == null) {
                    continue;
                }
    
                // Found it, this wrapper is already loaded...
                if (thisClassName.equals(aClassName)) {
                    Debug.message("Extension is already loaded: " + thisClassName + " jarName=" + thisJarName);
                    return true;
                }
            }
        }

        // This wrapper is not already loaded in the list of wrappers/extensions
        return false;
    }

    /**
     * Return the preferences manager for extensions.
     */
    public ExtensionPrefManager getPrefManager()
    {
        if (prefManager == null) {
            prefManager = new ExtensionPrefManager(extensions);
        }
        return prefManager;
    }

    /**
     * Ask for extension manager to show the help dialog for extensions. This is
     * here to be sure that the help dialog is called when extension manager is
     * valid.
     */
    public void showHelp(FXPlatformSupplier<Window> parentFrame)
    {
        List<ExtensionWrapper> extensionsList = new ArrayList<ExtensionWrapper>();
        synchronized (extensions) {
            extensionsList.addAll(extensions);
        }
        ExtensionsDialog dialog = new ExtensionsDialog(extensionsList, parentFrame);
        Platform.runLater(dialog::showAndWait);
    }

    /**
     * Searches for and loads any new extensions found in the project.
     */
    public void projectOpening(Project project)
    {
        File exts = new File(project.getProjectDir(), "extensions");
        loadDirectoryExtensions(exts, project);
    }

    /**
     * Inform extensions that a package has been opened
     */
    public void packageOpened(Package pkg)
    {
        delegateEvent(new PackageEvent(PackageEvent.PACKAGE_OPENED, pkg));
    }

    /**
     * This package frame is about to be closed. The issue here is to remove the
     * extension if this is the right time to do it.
     */
    public void packageClosing(Package pkg)
    {
        // Before removing the extension, signal that this package is closing
        delegateEvent(new PackageEvent(PackageEvent.PACKAGE_CLOSING, pkg));

        // Let's assume we are NOT going to delete the extension...
        boolean invalidateExtension = false;

        // Here comes the hard part of deciding IF to release the given
        // wrapper/extension..
        Project thisProject = pkg.getProject();

        // Surely I cannot release anything if I don't know what I am talking
        // about...
        if (thisProject == null) {
            return;
        }

        // The following CAN return null....
        PkgMgrFrame[] frameArray = PkgMgrFrame.getAllProjectFrames(thisProject);
        if (frameArray == null) {
            invalidateExtension = true;
        }
        else {
            invalidateExtension = frameArray.length <= 1;
        }

        // Nothing to do....
        if (!invalidateExtension) {
            return;
        }

        synchronized(extensions) {            
            // I am closing the last frame of the project, time to invalidate the
            // right extensions
            for (Iterator<ExtensionWrapper> iter = extensions.iterator(); iter.hasNext();) {
                ExtensionWrapper aWrapper = iter.next();
    
                // If the extension did not got loaded with this project skip it...
                if (thisProject != aWrapper.getProject()) {
                    continue;
                }
    
                // The following terminated the Extension
                aWrapper.terminate();
                iter.remove();
            }
        }
    }

    /**
     * Check whether the menus provided by this extension should not be shown for the
     * given project.
     * 
     * @param onThisProject   the project to check whether menus should be shown (may be null)
     * @param extensionProject  the project which the extension was loaded for (may be null, for
     *                          an extension not bound to a particular project)
     */
    private boolean skipThisMenu(Project onThisProject, Project extensionProject)
    {
        // I want menus if nothing it is a sys loaded extension on an empty
        // frame
        if (onThisProject == null && extensionProject == null)
            return false;

        // Menu should not be generated if an extension belongs to a project
        if (onThisProject == null && extensionProject != null)
            return true;

        // Menu should be generated if we are openieng a project and an
        // extension is not boukd to any
        if (onThisProject != null && extensionProject == null)
            return false;

        // now both are not null, generate a menu if they are the same.
        if (onThisProject == extensionProject)
            return false;

        // None of the above cases, do not generate a menu
        return true;
    }

    /**
     * Returns a List of menus currently provided by extensions.
     */
    LinkedList<JMenuItem> getMenuItems(ExtensionMenu attachedObject, Project onThisProject)
    {
        LinkedList<JMenuItem> menuItems = new LinkedList<JMenuItem>();

        synchronized(extensions) {                
            for (ExtensionWrapper aWrapper : extensions) {
                if (!aWrapper.isValid()) {
                    continue;
                }
    
                if (skipThisMenu(onThisProject, aWrapper.getProject())) {
                    continue;
                }
    
                JMenuItem anItem = aWrapper.safeGetMenuItem(attachedObject);
                if (anItem == null) {
                    continue;
                }
    
                anItem.putClientProperty("bluej.extmgr.ExtensionWrapper", aWrapper);
    
                menuItems.add(anItem);
            }
        }

        return menuItems;
    }

    /**
     * Delegates an event to all known extensions.
     */
    public void delegateEvent(ExtensionEvent event)
    {
        synchronized(extensions) {            
            for (ExtensionWrapper wrapper : extensions) {
                wrapper.safeEventOccurred(event);
            }
        }
    }

    /**
     * This is called back when some sort of event occurs. Depending on the
     * event we will adapt it and send it up to the extension.
     * 
     * @param eventId
     *            The event id (see BlueJEvent)
     * @param arg
     *            This really depends on that event is given
     *            
     * @see BlueJEvent
     */
    @OnThread(Tag.SwingIsFX)
    public void blueJEvent(int eventId, Object arg)
    {
        if (eventId == BlueJEvent.EXECUTION_RESULT) {
            ExecutionEvent exevent = (ExecutionEvent) arg;
            delegateEvent(new InvocationEvent(exevent));
            return;
        }
    }
    
    /**
     * Calls the extension to draw its representation of a class target.
     * 
     * @param layer
     *            The layer of the drawing which causes the different methods of
     *            the {@link ExtensionClassTargetPainter} instance to be called.
     * @param bClassTarget
     *            The class target that will be painted.
     * @param graphics
     *            The {@link Graphics2D} instance to draw on.
     * @param width
     *            The width of the area to paint.
     * @param height
     *            The height of the area to paint.
     */
    public void drawExtensionClassTarget(Layer layer, BClassTarget bClassTarget,
            Graphics2D graphics, int width, int height)
    {
        synchronized (extensions) {
            for (ExtensionWrapper extension : extensions) {
                if (!extension.isValid()) {
                    continue;
                }

                extension.safeDrawExtensionClassTarget(layer, bClassTarget, graphics, width, height);
            }
        }
    }
    
    /**
     * Gets the loaded extensions, for data collection purposes.
     * 
     * @param proj Pass a project to get the project-specific extensions,
     *             pass null to get *only* extensions that are not project-specific
     */
    public List<ExtensionWrapper> getLoadedExtensions(Project proj)
    {
        ArrayList<ExtensionWrapper> r = new ArrayList<ExtensionWrapper>();
        
        for (ExtensionWrapper ext : extensions)
        {
            if (ext.getProject() == proj)
            {
                r.add(ext);
            }
        }
        
        return r;
    }
}
