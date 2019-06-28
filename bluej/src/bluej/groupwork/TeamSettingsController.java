/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2014,2015,2016,2017,2018  Michael Kolling and John Rosenberg
 
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
package bluej.groupwork;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import bluej.Config;
import bluej.groupwork.ui.TeamSettingsDialog;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;
import bluej.utility.Debug;
import bluej.utility.DialogManager;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * This class is responsible for reading and writing the configuration files
 * regarding teamwork settings. The files are team.defs, which is located in
 * the top-level folder of a team project, and the bluej.properties
 *
 * @author fisker
 */
@OnThread(Tag.FXPlatform)
public class TeamSettingsController
{
    // Don't need synchronized because it's never modified again:
    @OnThread(value = Tag.Any)
    private static final ArrayList<TeamworkProvider> teamProviders;
    static {
        teamProviders = new ArrayList<>(2);
        try {
            teamProviders.add(loadProvider("bluej.groupwork.svn.SubversionProvider"));
        }
        catch (Throwable e) {
            Debug.message("Failed to initialize Subversion: " + e.getClass().getName()
                    + ": "+ e.getLocalizedMessage());
        }
        try {
            teamProviders.add(loadProvider("bluej.groupwork.git.GitProvider"));
        } catch (Throwable e) {
            Debug.message("Failed to initialize Git: " + e.getClass().getName()
                    + ": " + e.getLocalizedMessage());
        }
    }

    /**
     * An enum with all current use server types.
     */
    public enum ServerType
    {
        Subversion,
        Git
    }

    private static TeamworkProvider loadProvider(String name) throws Throwable
    {
        Class<?> c = Class.forName(name);
        Object instance = c.getDeclaredConstructor().newInstance();
        return (TeamworkProvider) instance;            
    }
    
    private Project project;
    private File projectDir;
    private Properties teamProperties;
    private TeamSettingsDialog teamSettingsDialog;
    private TeamSettings settings;
    
    //general
    private String password;

    private File teamdefs;
    
    // repository
    private Repository repository;
    
    /**
     * Construct a team settings controller for the given project.
     */
    public TeamSettingsController(Project project)
    {
        this.project = project;
        this.projectDir = project.getProjectDir();
        teamProperties = new Properties();
        readSetupFile();
    }

    /**
     * Construct a team settings controller, not associated with
     * any project initially. The supplied projectDir need not be the
     * final project directory - it is just used as a working location
     * until the project is set.
     */
    public TeamSettingsController(File projectDir)
    {
        this.projectDir = projectDir;
        teamProperties = new Properties();
    }

    /**
     * Assign this team settings controller to a particular project.
     * Once this is done, the repository settings can no longer be
     * changed.
     */
    public void setProject(Project proj)
    {
        project = proj;
        projectDir = proj.getProjectDir();
        repository = null;
        disableRepositorySettings();
    }
    
    /**
     * Get a list of the teamwork providers (Subversion, Git).
     */
    public List<TeamworkProvider> getTeamworkProviders()
    {
        return teamProviders;
    }

    /**
     * Get the teamwork provider by name (Subversion, Git).
     *
     * @param type The server type that we need to get its provider
     * @return The teamwork provider for the type passed
     */
    public TeamworkProvider getTeamworkProvider(ServerType type)
    {
        return teamProviders.stream()
                .filter(teamworkProvider -> teamworkProvider.getProviderName().equals(type.name()))
                .findAny().get();
    }

    /**
     * Get the repository. Returns null if user credentials are required
     * but the user chooses to cancel.
     */
    public Repository trytoEstablishRepository(boolean authRequired)
    {
        if (authRequired && password == null) {
            // If we don't yet know the password, prompt the user
            if (!getTeamSettingsDialog().showAndWait().isPresent())
                return null; // user cancelled, password still null

            TeamSettings settings = teamSettingsDialog.getSettings();
            if (repository == null) {
                try {
                    repository = settings.getProvider().getRepository(projectDir, settings);
                }
                catch (UnsupportedSettingException e)
                {
                    DialogManager.showErrorTextFX(teamSettingsDialog.asWindow(), e.getLocalizedMessage());
                }
            }
            else {
                repository.setPassword(settings);
            }
        }
        else if (!authRequired && password == null) {
            // We'll return a "temporary" repository.
            try {
                return settings.getProvider().getRepository(projectDir, settings);
            }
            catch (UnsupportedSettingException e) {
                DialogManager.showErrorTextFX(teamSettingsDialog.asWindow(), e.getLocalizedMessage());
            }
        }
        else {
            // We might have the password, but not yet have created
            // the repository
            if (repository == null) {
                try {
                    repository = settings.getProvider().getRepository(projectDir, settings);
                }
                catch (UnsupportedSettingException e) {
                    DialogManager.showErrorTextFX(teamSettingsDialog.asWindow(), e.getLocalizedMessage());
                }
            }
        }
        
        return repository;
    }
    
    /**
     * Initialize the repository and make sure that authentication details (username/password) have
     * been provided.
     */
    public boolean initRepository()
    {
        return initRepository(true);
    }
    
    /**
     * Initialise the repository, with optional authentication details. This can be used to
     * intialise the repository without opening the team settings dialog.
     */
    private boolean initRepository(boolean auth)
    {
        return trytoEstablishRepository(auth) != null;
    }
    
    /**
     * Get a list of files (and possibly directories) in the project which should be
     * under version control management. This includes files which have been locally
     * deleted since the last commit.
     * 
     * @param includeLayout  indicates whether to include the layout (bluej.pkg) files.
     * (Note that locally deleted bluej.pkg files are always included).
     */
    public Set<File> getProjectFiles(boolean includeLayout)
    {
        initRepository(); // make sure the repository is constructed
        
        boolean versionsDirs = false;
        if (repository != null) {
            versionsDirs = repository.versionsDirectories();
        }
        
        // Get a list of files to commit
        Set<File> files = project.getFilesInProject(includeLayout, versionsDirs);
        
        if (repository != null) {
            repository.getAllLocallyDeletedFiles(files);
        }
        
        return files;
    }
    
    /**
     * Get a filename filter suitable for filtering out files which we don't want
     * to be under version control.
     *
     * @param includeLayout      A flag to indicate if layout files should be included or filtered out.
     * @param includeDirectories A flag to indicate if directories should be included or filtered out.
     */
    public FileFilter getFileFilter(boolean includeLayout, boolean includeDirectories)
    {
        initRepository(false);
        FileFilter repositoryFilter = null;
        if (repository != null) {
            repositoryFilter = repository.getMetadataFilter();
        }
        return new CodeFileFilter(getIgnoreFiles(), includeLayout, includeDirectories, projectDir, repositoryFilter);
    }
    
    /**
     * Read the team setup file in the top level folder of the project
     */
    private void readSetupFile()
    {
        teamdefs = new File(projectDir, "team.defs");

        try {
            teamProperties.load(new FileInputStream(teamdefs));
            
            initSettings();
        }
        catch (FileNotFoundException e) {
            // e.printStackTrace();
            // This is allowed to happen - if a non-shared project becomes
            // shared
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * checks if a project has a team.defs if it doesn't, then return false
     * @param projDir File object representing the directory where team.defs is located.
     * @return true if there is a valid vcs. false otherwise.
     */
    @OnThread(Tag.Any)
    public static boolean isValidVCSfound(File projDir)
    {
        File teamDefs = new File(projDir, "team.defs");
        Properties p = new Properties();
        String providerName = null;
        try {
            p.load(new FileInputStream(teamDefs));
            providerName = p.getProperty("bluej.teamsettings.vcs");
        } catch (IOException e){
        }

        if (providerName != null) {
            for (int index = 0; index < teamProviders.size(); index++) {
                TeamworkProvider prov = (TeamworkProvider) teamProviders.get(index);
                if (prov.getProviderName().equalsIgnoreCase(providerName)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private void initSettings()
    {
        String user = getPropString("bluej.teamsettings.user");
        if (user == null) {
            user = "";
        }
        
        String yourName = getPropString("bluej.teamsettings.yourName");
        if (yourName == null){
            yourName = "";
        }
        
        String yourEmail = getPropString("bluej.teamsettings.yourEmail");
        if (yourEmail == null){
            yourEmail = "";
        }
        
        String group = getPropString("bluej.teamsettings.groupname");
        if(group == null) {
            group = "";
        }

        TeamworkProvider provider = null;
        String providerName = getPropString("bluej.teamsettings.vcs");
        if (providerName != null) {
            for (int index = 0; index < teamProviders.size(); index++) {
                TeamworkProvider prov = (TeamworkProvider) teamProviders.get(index);
                if (prov.getProviderName().equalsIgnoreCase(providerName)) {
                    provider = prov;
                }
            }
        }
        
        if (provider != null) {
            settings = initProviderSettings(user, group, password, provider);
            settings.setYourName(yourName);
            settings.setYourEmail(yourEmail);
        }
    }
    
    public TeamSettings initProviderSettings(String user, String group, String password,
            TeamworkProvider provider) {
        
        String keyBase = "bluej.teamsettings."
            + provider.getProviderName().toLowerCase() + "."; 
        
        String prefix = getPropString(keyBase + "repositoryPrefix");
        String server = getPropString(keyBase + "server");
        
        String protocol = getPropString(keyBase + "protocol");

        return new TeamSettings(provider, protocol, server, prefix, group, user, password);
    }
    
    /**
     * Prepare for the deletion of a directory. For CVS, this involves moving
     * the metadata elsewhere. Returns true if the directory should actually
     * be deleted, or false if the version control system will delete it either
     * immediately or at commit time.
     */
    public boolean prepareDeleteDir(File dir)
    {
        Repository repository = trytoEstablishRepository(false);
        if (repository == null)
            return false;
        return repository.prepareDeleteDir(dir);
    }
    
    /**
     * Prepare a newly created directory for version control.
     */
    public void prepareCreateDir(File dir)
    {
        Repository repository = trytoEstablishRepository(false);
        if (repository != null)
            repository.prepareCreateDir(dir);
    }

    /**
     * Get the team settings dialog to edit these team settings.
     */
    public TeamSettingsDialog getTeamSettingsDialog()
    {
        if (teamSettingsDialog == null) {
            teamSettingsDialog = new TeamSettingsDialog(PkgMgrFrame.getMostRecent().getFXWindow(), this);
            disableRepositorySettings();
        }

        return teamSettingsDialog;
    }
    
    /**
     * Disable the repository fields in the team settings dialog if
     * we have a project attached.
     */
    private void disableRepositorySettings()
    {
        if (teamSettingsDialog != null && project != null) {
            // We have a project, which means we have an established
            // repository. It shouldn't be changed now.
            teamSettingsDialog.disableRepositorySettings();
        }
    }
    
    /**
     * Write the settings to team.defs in the project. It no project is known,
     * nothing happens. Note that nothing is written to bluej.properties. That
     * is handled by the Config class.
     *
     */
    public void writeToProject()
    {
        if (projectDir == null) {
            return;
        }

        File cfgFile = new File(projectDir + "/team.defs");

        if (!cfgFile.exists()) {
            addIgnoreFilePatterns(teamProperties);
        }

        try {
            teamProperties.store(new FileOutputStream(cfgFile), null);
            repository = null;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add to the team properties the files we wish to ignore, like class files
     * and ctxt files
     * @param teamProperties
     */
    private void addIgnoreFilePatterns(Properties teamProperties)
    {
        teamProperties.put("bluej.teamsettings.ignore1", ".*\\.class");
        teamProperties.put("bluej.teamsettings.ignore2", "bluej\\.pkh");
        teamProperties.put("bluej.teamsettings.ignore3", "team\\.defs");
        teamProperties.put("bluej.teamsettings.ignore4", ".*\\.ctxt");
        teamProperties.put("bluej.teamsettings.ignore5", ".*\\~");
        teamProperties.put("bluej.teamsettings.ignore6", ".*\\#");
        teamProperties.put("bluej.teamsettings.ignore7", ".*\\#backup");
        teamProperties.put("bluej.teamsettings.ignore8", "\\.DS_Store");
    }

    /**
     * get the property by the name strname. If the property is present in
     * the project, that value is returned. If not, bluej.properties and then
     * bluej.defs are searched. If not found, null is returned.
     * @param strname
     * @return
     */
    public String getPropString(String strname)
    {
        String result = teamProperties.getProperty(strname);

        if (result != null) {
            return result;
        }

        result = Config.getPropString(strname, null);

        return result;
    }

    public void setPropString(String key, String value)
    {
        if (key != null && value != null)
            teamProperties.setProperty(key, value);
    }
    
    public void updateSettings(TeamSettings newSettings, boolean useAsDefault)
    {
        settings = newSettings;
        
        String userKey = "bluej.teamsettings.user";
        String userValue = settings.getUserName();
        setPropString(userKey, userValue);
        
        String yourNameKey = "bluej.teamsettings.yourName";
        String yourNameValue = "";
        
        if (teamSettingsDialog.getSettings().getProvider().needsName()){
            //save field "your name"
            yourNameValue = settings.getYourName();
            setPropString(yourNameKey, yourNameValue);
        }
        String yourEmailKey = "bluej.teamsettings.yourEmail";
        String yourEmailValue = "";
        if (teamSettingsDialog.getSettings().getProvider().needsEmail()){
            //save field "your email"
            yourEmailValue = settings.getYourEmail();
            setPropString(yourEmailKey, yourEmailValue);
        }
        

        String providerKey = "bluej.teamsettings.vcs";
        
        String providerName = newSettings.getProvider()
                .getProviderName().toLowerCase();
        setPropString(providerKey, providerName);
        
        String keyBase = "bluej.teamsettings."
                + providerName + ".";
        String serverKey = keyBase + "server";
        String serverValue = settings.getServer();
        if (serverValue != null)
            setPropString(serverKey, serverValue);

        String prefixKey = keyBase + "repositoryPrefix";
        String prefixValue = settings.getPrefix();
        setPropString(prefixKey, prefixValue);

        String protocolKey = keyBase + "protocol";
        String protocolValue = settings.getProtocol();
        setPropString(protocolKey, protocolValue);

        String groupKey = "bluej.teamsettings.groupname";
        String groupValue = settings.getGroup();
        setPropString(groupKey,  groupValue);

        String useAsDefaultKey = "bluej.teamsettings.useAsDefault";
        Config.putPropString(useAsDefaultKey,
            Boolean.toString(useAsDefault));

        // passwords are handled differently for security reasons,
        // we don't at present store them on disk
        String passValue = settings.getPassword();
        setPasswordString(passValue);
        
        if (repository != null) {
            TeamSettings settings = getTeamSettingsDialog().getSettings();
            repository.setPassword(settings);
        }
        
        if (useAsDefault) {
            Config.putPropString(providerKey, providerName);
            Config.putPropString(userKey, userValue);
            if (teamSettingsDialog.getSettings().getProvider().needsName()){
                Config.putPropString(yourNameKey, yourNameValue);
            }
            if (teamSettingsDialog.getSettings().getProvider().needsEmail()){
                Config.putPropString(yourEmailKey, yourEmailValue);
            } else {
                //save server information if svn, not git.
                Config.putPropString(serverKey, serverValue);
                Config.putPropString(prefixKey, prefixValue);
                Config.putPropString(groupKey, groupValue);
                Config.putPropString(protocolKey, protocolValue);
            }
        }
    }

    /**
     * In the first instance we don't want to store password.
     * We want to ask the first time they want to try and perform operation
     * We then store for the rest of the session. Over time we may want to provide
     * some way of storing with relative security.
     */
    public String getPasswordString()
    {
        return password;
    }

    private void setPasswordString(String password)
    {
        this.password = password;
    }

    public boolean hasPasswordString()
    {
        return password != null;
    }

    /**
     * gets the regular expressions in string form for the files we should ignore
     * @return List containing Strings
     */
    public List<String> getIgnoreFiles()
    {
        Iterator<Object> keys = teamProperties.keySet().iterator();
        List<String> patterns = new LinkedList<String>();

        while (keys.hasNext()) {
            String key = (String) keys.next();

            // legacy settings
            if (key.startsWith("bluej.teamsettings.cvs.ignore")) {
                patterns.add(teamProperties.getProperty(key));
            }
            
            // new settings
            if (key.startsWith("bluej.teamsettings.ignore")) {
                patterns.add(teamProperties.getProperty(key));
            }
        }

        return patterns;
    }

    public boolean hasProject()
    {
        return project != null;
    }
    
    public Project getProject()
    {
        return project;
    }

    /**
     * Method to get working copy version.
     *
     * @return version number. -1 if not applicable or not subversion.
     */
    public double getWorkingCopyVersion()
    {
        return settings.getProvider().getWorkingCopyVersion(projectDir);
    }

    public boolean isDVCS()
    {
        // We should possibly show the dialog here to get the settings,
        // but right now (preparing for BlueJ 4.0.0-preview2 release,
        // and with a lot of isDVCS calls on the repository which could be null),
        // I'd rather return false and thus get a button label wrong than
        // to have a NullPointerException thrown if the settings are wrong.
        if (settings != null)
        {
            TeamworkProvider provider = settings.getProvider();
            if (provider != null)
                return provider.isDVCS();
        }
        return false;
    }
}
