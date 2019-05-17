/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2013,2014,2017  Michael Kolling and John Rosenberg 
 
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
 LICENSE.txt
 */
package bluej.editor.moe;

import bluej.Config;
import bluej.prefmgr.PrefMgr;
import bluej.utility.javafx.FXPlatformRunnable;
import bluej.utility.javafx.JavaFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Process the document re-parse queue.
 * 
 * <p>This is a Runnable which runs on the Swing/AWT event queue. It performs
 * a small amount of re-parsing before re-queing itself, which allows input
 * to be processed in the meantime.
 * 
 * @author Davin McCall
 */
@OnThread(value = Tag.FXPlatform, ignoreParent = true)
public class ReparseRunner implements FXPlatformRunnable
{
    private MoeEditor editor;
    
    private int procTime; //the time allowed for the incremental parsing before re-queueing
    
    public ReparseRunner(MoeEditor editor)
    {
        this.editor = editor;
        this.procTime = 15;
    }
    
    public void run()
    {
        MoeSyntaxDocument document = editor.getSourceDocument();
        long begin = System.currentTimeMillis();
        if (PrefMgr.getScopeHighlightStrength().get() != 0 && document != null && document.pollReparseQueue()) {
            // Continue processing
            while (System.currentTimeMillis() - begin < this.procTime) {
                if (! document.pollReparseQueue()) {
                    break;
                }
            }
            editor.getSourceDocument().applyPendingScopeBackgrounds();
            JavaFXUtil.runPlatformLater(this);
        }
        else {
            // tell MoeEditor we are no longer scheduled.
            editor.reparseRunnerFinished();
        }
    }
}
