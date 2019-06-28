/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2015,2016,2017,2018  Michael Kolling and John Rosenberg
 
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
package bluej.editor.moe;

import bluej.Config;
import bluej.editor.Editor;
import bluej.editor.EditorWatcher;
import bluej.editor.stride.FXTabbedEditor;
import bluej.parser.entity.EntityResolver;
import bluej.pkgmgr.JavadocResolver;
import bluej.utility.javafx.FXPlatformRunnable;
import bluej.utility.javafx.FXSupplier;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Implementation of EditorManager for the Moe editor.
 * 
 * @author Michael Kolling
 */

public final class MoeEditorManager extends bluej.editor.EditorManager
{
    private final Properties resources;
    private final List<MoeEditor> editors; // open editors

    // user preferences

    private final boolean showLineNum;
    private final boolean showToolBar;

    // =========================== PUBLIC METHODS ===========================

    public MoeEditorManager()
    {
        editors = new ArrayList<MoeEditor>(4);
               
        showToolBar = true;
        showLineNum = false;

        resources = Config.moeUserProps;
    }


    // ------------------------------------------------------------------------
    
    /*
     * Open an editor to display a class. The filename may be "null"
     * to open an empty editor (e.g. for displaying a view). The editor
     * is initially hidden. A call to "Editor::show" is needed to make
     * is visible after opening it.
     *
     * @param filename     name of the source file to open (may be null)
     * @param docFilename  name of the corresponding javadoc file 
     * @param windowTitle  title of window (usually class name)
     * @param watcher      an watcher to be notified of edit events
     * @param compiled     true, if the class has been compiled
     * @param projectResolver   A resolver for external symbols
     * 
     * @return          the new editor, or null if there was a problem
     */
    @Override
    public Editor openClass(String filename, 
                String docFilename,
                Charset charset,
                String windowTitle,
                FXSupplier<FXTabbedEditor> fxTabbedEditor,
                EditorWatcher watcher, 
                boolean compiled,
                EntityResolver projectResolver,
                JavadocResolver javadocResolver,
                FXPlatformRunnable callbackOnOpen)
    {
        return openEditor (filename, docFilename, charset, true, windowTitle, fxTabbedEditor, watcher, compiled,
                           projectResolver, javadocResolver, callbackOnOpen);
    }

    // ------------------------------------------------------------------------
    
    /*
     * Open an editor to display a text document. The difference to
     * "openClass" is that code specific functions (such as compile,
     * debug, view) are disabled in the editor. The filename may be
     * "null" to open an empty editor. The editor is initially hidden.
     * A call to "Editor.show" is needed to make is visible after
     * opening it.
     *
     * @param filename          name of the source file to open (may be null)
     * @param windowTitle       title of window (usually class name)
     * @returns                 the new editor, or null if there was a problem
     */
    @Override
    public Editor openText(String filename, Charset charset, String windowTitle, FXSupplier<FXTabbedEditor> fxTabbedEditor)
    {
        return openEditor(filename, null, charset, false, windowTitle, fxTabbedEditor, null, false, null, null, null);
    }

    @Override
    public void refreshAll()
    {
        Iterator<MoeEditor> e = editors.iterator();

        while(e.hasNext()) {
            Editor ed = e.next();
            
            if(ed.isOpen()) {
                ed.refresh();
            }
       }
    }

    // ------------------------------------------------------------------------
    
    /**
     * Discard the given editor and leave it to be collected by the garbage
     * collector.
     */
    @Override
    public void discardEditor(Editor ed)
    {
        ed.close();
        editors.remove(ed);
    }

    // ========================== PACKAGE METHODS ===========================


    // ------------------------------------------------------------------------
 
    
    // ========================== PRIVATE METHODS ===========================

    // ------------------------------------------------------------------------
    
    /**
     * Open an editor to display a class. The filename may be "null"
     * to open an empty editor (e.g. for displaying a view). The editor
     * is initially hidden. A call to "Editor::show" is needed to make
     * is visible after opening it.
     *
     * @param filename     name of the source file to open (may be null)
     * @param docFilename  name of the documentation based on filename
     * @param charset      the character set of the file contents
     * @param isCode       true if the file represents code, or false if it is plain text
     * @param windowTitle  title of window (usually class name)
     * @param watcher      an object interested in editing events
     * @param compiled     true, if the class has been compiled
     * @param projectResolver   a resolver for external symbols
     * @returns       the new editor, or null if there was a problem
     */
    private Editor openEditor(String filename, String docFilename,
            Charset charset,
            boolean isCode, String windowTitle,
            FXSupplier<FXTabbedEditor> fxTabbedEditor,
            EditorWatcher watcher, boolean compiled,
            EntityResolver projectResolver,
            JavadocResolver javadocResolver,
            FXPlatformRunnable callbackOnOpen)
    {
        MoeEditor editor;

        MoeEditorParameters mep = new MoeEditorParameters(windowTitle, watcher,
                resources, projectResolver, javadocResolver);
        mep.setCode(isCode);
        mep.setShowToolbar(showToolBar);
        mep.setShowLineNum(showLineNum);
        mep.setCallbackOnOpen(callbackOnOpen);
        mep.setCompiled(compiled);
        editor = new MoeEditor(mep, fxTabbedEditor);
        editors.add(editor);
        if (editor.showFile(filename, charset, compiled, docFilename))
        {
            return editor;
        }
        editor.doClose();           // editor will remove itself
        return null;
    }
}
