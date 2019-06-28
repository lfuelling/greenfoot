/*
 This file is part of the BlueJ program. 
 Copyright (C) 2012,2013  Michael Kolling and John Rosenberg 
 
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

import javax.swing.JMenuItem;

import bluej.extensions.BPackage;
import bluej.extensions.ExtensionBridge;
import bluej.extensions.MenuGenerator;
import bluej.pkgmgr.Package;

/**
 * Implementation of the {@link ExtensionMenu} interface for the View
 * menu.
 * 
 * @author Simon Gerlach
 */
public class ViewExtensionMenu implements ExtensionMenu
{
    private Package bluejPackage;

    /**
     * Constructor. Creates a new {@link ViewExtensionMenu}.
     * 
     * @param bluejPackage
     *            The current package opened in BlueJ.
     */
    public ViewExtensionMenu(Package bluejPackage)
    {
        this.bluejPackage = bluejPackage;
    }

    @Override
    public JMenuItem getMenuItem(MenuGenerator menuGenerator)
    {
        if (bluejPackage == null) {
            return menuGenerator.getViewMenuItem(null);
        }

        BPackage bPackage = ExtensionBridge.newBPackage(bluejPackage);
        return menuGenerator.getViewMenuItem(bPackage);
    }

    @Override
    public void postMenuItem(MenuGenerator menuGenerator, JMenuItem onThisItem)
    {
        if (bluejPackage == null) {
            // Only BPackages can be null when a menu is invoked
            menuGenerator.notifyPostViewMenu(null, onThisItem);
        } else {
            BPackage bPackage = ExtensionBridge.newBPackage(bluejPackage);
            menuGenerator.notifyPostViewMenu(bPackage, onThisItem);
        }
    }
}
