/*
 This file is part of the BlueJ program. 
 Copyright (C) 2014,2015,2016 Michael Kölling and John Rosenberg
 
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
package bluej.stride.framedjava.errors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import bluej.compiler.Diagnostic.DiagnosticOrigin;
import bluej.stride.framedjava.ast.ASTUtility;
import bluej.stride.framedjava.ast.FilledExpressionSlotFragment;
import bluej.stride.framedjava.ast.NameDefSlotFragment;
import bluej.stride.framedjava.ast.StringSlotFragment;
import bluej.stride.framedjava.ast.TypeSlotFragment;
import bluej.stride.framedjava.errors.Correction.SimpleCorrectionInfo;
import bluej.stride.framedjava.frames.AssignFrame;
import bluej.stride.framedjava.frames.ClassFrame;
import bluej.stride.framedjava.frames.VarFrame;
import bluej.stride.generic.Frame;
import bluej.stride.generic.FrameCursor;
import threadchecker.OnThread;
import threadchecker.Tag;

public class UndeclaredVariableLvalueError extends DirectSlotError
{
    private final AssignFrame assignFrame;
    private final List<Correction> corrections = new ArrayList<>();
    private final String varName;

    /**
     * Creates an error about an undeclared variable being used on the left-hand side of an assignment.
     * The variable will occupy the entire left-hand side of an assignment frame, and will thus be
     * a candidate for a quick fix which turns the assignment into a declaration+initialisation of that variable.
     *
     * @param slotFragment The fragment with the error.
     * @param assignFrame The assignment frame with the error.
     * @param possibleCorrections The possible other variable names (unfiltered: all variable names which are in scope)
     */
    public UndeclaredVariableLvalueError(StringSlotFragment slotFragment, AssignFrame assignFrame, Set<String> possibleCorrections)
    {
        super(slotFragment, DiagnosticOrigin.STRIDE_LATE);
        this.assignFrame = assignFrame;
        this.corrections.addAll(Correction.winnowAndCreateCorrections(assignFrame.getLHS().getText(), possibleCorrections.stream().map(SimpleCorrectionInfo::new), s -> assignFrame.getLHS().setText(s)));
        this.varName = assignFrame.getLHS().getText();
    }
    
    
    @Override
    @OnThread(Tag.Any)
    public String getMessage()
    {
        return "Undeclared variable: " + varName;
    }

    @Override
    public List<FixSuggestion> getFixSuggestions()
    {
        ArrayList<FixSuggestion> fixes = new ArrayList<>();
        
        fixes.add(new ChangeAssignmentToDeclarationAndInitialisation());
        fixes.add(new AddFieldDeclaration());
        fixes.addAll(corrections);
        
        return fixes;
    }
    
    private class ChangeAssignmentToDeclarationAndInitialisation extends FixSuggestion
    {
        @Override
        public String getDescription()
        {
            return "Declare variable here";
        }

        @Override
        public void execute()
        {
            // TODO does access have to be null here?
            assignFrame.getParentCanvas().replaceBlock(assignFrame, new VarFrame(assignFrame.getEditor(), null, false, false, new TypeSlotFragment("", ""), 
                        new NameDefSlotFragment(assignFrame.getLHS().getText()), new FilledExpressionSlotFragment(assignFrame.getRHS().getSlotElement()), true));
        }
    }
    
    private class AddFieldDeclaration extends FixSuggestion
    {

        @Override
        public String getDescription()
        {
            return "Declare field in class";
        }

        @Override
        public void execute()
        {
            // Add the field before the first non-field in the class:
            ClassFrame classFrame = (ClassFrame)ASTUtility.getTopLevelElement(assignFrame.getCode()).getFrame();
            
            List<Frame> members = classFrame.getfieldsCanvas().getBlockContents();
            
            Optional<Frame> firstNonField = members.stream().filter(f -> !(f instanceof VarFrame)).findFirst();
            
            FrameCursor cursorAfter = (firstNonField.isPresent()) ? cursorAfter = classFrame.getfieldsCanvas().getCursorBefore(firstNonField.get()) : classFrame.getfieldsCanvas().getLastCursor();
            classFrame.getfieldsCanvas().insertBlockBefore(new VarFrame(assignFrame.getEditor(), null, false, false, 
                    new TypeSlotFragment("", ""), new NameDefSlotFragment(assignFrame.getLHS().getText()), null, true), cursorAfter);
        }
    }

    @Override
    public boolean isJavaPos()
    {
        return false;
    }
}
