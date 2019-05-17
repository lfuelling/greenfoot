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
package bluej.stride.framedjava.ast;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import bluej.stride.framedjava.elements.CodeElement;
import bluej.stride.framedjava.elements.LocatableElement.LocationMap;
import bluej.stride.framedjava.errors.DirectSlotError;
import bluej.stride.framedjava.errors.EmptyError;
import bluej.stride.framedjava.errors.SyntaxCodeError;
import bluej.stride.framedjava.errors.UnknownTypeError;
import bluej.stride.framedjava.errors.UnneededSemiColonError;
import bluej.stride.framedjava.slots.ExpressionSlot;
import bluej.stride.framedjava.slots.TypeSlot;
import bluej.stride.generic.InteractionManager;
import threadchecker.OnThread;
import threadchecker.Tag;

public class TypeSlotFragment extends StructuredSlotFragment
{
    private TypeSlot slot;
    private boolean hasEarlyErrors;

    public TypeSlotFragment(String content, String javaCode, TypeSlot slot)
    {
        super(content, javaCode);
        this.slot = slot;
    }
    
    public TypeSlotFragment(String content, String javaCode)
    {
        this(content, javaCode, null);
    }

    @Override
    public Map<String, CodeElement> getVars()
    {
        return Collections.emptyMap();
    }

    @Override
    public String getJavaCode(Destination dest, ExpressionSlot<?> completing, Parser.DummyNameGenerator dummyNameGenerator)
    {
        if (!dest.substitute() || (content != null && Parser.parseableAsType(content)))
            return content;
        else
            // Just need an undefined type name, so pick a random one:
            return dummyNameGenerator.generateNewDummyName();
    }

    @Override
    public Stream<SyntaxCodeError> findEarlyErrors()
    {
        // In all these cases, we will have an early error:
        hasEarlyErrors = true;
        if (content != null && content.isEmpty())
            return Stream.of(new EmptyError(this, "Type cannot be empty"));
        else if (content != null && content.endsWith(";"))
            // Must check this before general parse errors:
            return Stream.of(new UnneededSemiColonError(this, () -> getSlot().setText(content.substring(0, content.length() - 1))));
        else if (content == null || !Parser.parseableAsType(content))
            return Stream.of(new SyntaxCodeError(this, "Invalid type"));

        // If we reached here, no early error:
        hasEarlyErrors = false;
        return Stream.empty();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public Future<List<DirectSlotError>> findLateErrors(InteractionManager editor, CodeElement parent, LocationMap rootPathMap)
    {
        CompletableFuture<List<DirectSlotError>> f = new CompletableFuture<>();
        
        // No point looking for a type that isn't syntactically valid:
        // Also, don't mess with arrays or generics or qualified types:
        if (hasEarlyErrors || content.contains("[") || content.contains("<") || content.contains("."))
        {
            f.complete(Collections.emptyList());
            return f;
        }
        
        editor.withTypes(types -> {
            
            if (types.containsKey(content))
            {
                // Match -- no error
                f.complete(Collections.emptyList());
                return;
            }
            // Otherwise, give error and suggest corrections 
            final UnknownTypeError error = new UnknownTypeError(this, content, slot::setText, editor, types.values().stream(), editor.getImportSuggestions().values().stream().flatMap(Collection::stream)) {};
            error.recordPath(rootPathMap.locationFor(this));
            f.complete(Arrays.asList(error));
        });
        return f;
    }
    

    @Override
    public TypeSlot getSlot()
    {
        return slot;
    }

    public void registerSlot(TypeSlot slot)
    {
        if (this.slot == null)
            this.slot = slot;
    }
}
