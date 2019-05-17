/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2015,2016,2017,2018  Michael Kolling and John Rosenberg
 
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
package bluej.groupwork.ui;

import java.util.Arrays;
import java.util.List;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import bluej.Config;
import bluej.groupwork.TeamSettings;
import bluej.groupwork.TeamSettingsController;
import bluej.groupwork.TeamSettingsController.ServerType;
import bluej.groupwork.TeamworkProvider;
import bluej.groupwork.actions.ValidateConnectionAction;
import bluej.utility.Debug;
import bluej.utility.javafx.HorizontalRadio;
import bluej.utility.javafx.JavaFXUtil;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A panel for team settings.
 * 
 * @author fisker
 * @author Amjad Altadmri
 */
@OnThread(Tag.FXPlatform)
public class TeamSettingsPanel extends VBox
{
    private TeamSettingsController teamSettingsController;
    private TeamSettingsDialog teamSettingsDialog;

    private GridPane personalPane;
    private GridPane locationPane;

    private Label serverLabel    = new Label(Config.getString("team.settings.server"));
    private Label prefixLabel    = new Label(Config.getString("team.settings.prefix"));
    private Label protocolLabel  = new Label(Config.getString("team.settings.protocol"));
    private Label uriLabel       = new Label(Config.getString("team.settings.uri"));

    private Label yourNameLabel  = new Label(Config.getString("team.settings.yourName"));
    private Label yourEmailLabel = new Label(Config.getString("team.settings.yourEmail"));
    private Label userLabel      = new Label(Config.getString("team.settings.user"));
    private Label passwordLabel  = new Label(Config.getString("team.settings.password"));
    private Label groupLabel     = new Label(Config.getString("team.settings.group"));


    private final HorizontalRadio<ServerType> serverTypes;

    private final TextField serverField = new TextField();
    private final TextField prefixField = new TextField();
    private final ComboBox<String> protocolComboBox = new ComboBox<>();
    private final TextField uriField = new TextField();

    private final TextField yourNameField = new TextField();
    private final TextField yourEmailField = new TextField();
    private final TextField userField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField groupField = new TextField();
    
    /** identifies which field is the primary server information field */
    private TextField locationPrimaryField;
    /** identifiers which field is the primary personal information field */
    private TextField personalPrimaryField;

    private CheckBox useAsDefault;
    private ServerType selectedServerType = null;

    public TeamSettingsPanel(TeamSettingsController teamSettingsController, TeamSettingsDialog dialog, ObservableList<String> styleClass)
    {
        this.teamSettingsController = teamSettingsController;
        this.teamSettingsDialog = dialog;

        JavaFXUtil.addStyleClass(this, "panel");

        serverTypes = new HorizontalRadio<>(Arrays.asList(ServerType.Subversion, ServerType.Git));
        serverTypes.select(ServerType.Subversion);

        HBox serverTypeBox = new HBox();
        JavaFXUtil.addStyleClass(serverTypeBox, "serverType-box");
        serverTypeBox.getChildren().add(new Label(Config.getString("team.settings.serverType")));
        serverTypeBox.getChildren().addAll(serverTypes.getButtons());
        serverTypeBox.setAlignment(Pos.CENTER);
        this.getChildren().add(serverTypeBox);

        useAsDefault = new CheckBox(Config.getString("team.settings.rememberSettings"));

        locationPane = createGridPane();
        personalPane = createGridPane();
        preparePanes(serverTypes.selectedProperty().get());

        JavaFXUtil.addChangeListenerPlatform(serverTypes.selectedProperty(), type -> {
            preparePanes(type);
            updateOKButtonBinding();
        });

        ValidateConnectionAction validateConnectionAction = new ValidateConnectionAction(this, dialog::getOwner);
        Button validateButton = new Button();
        validateConnectionAction.useButton(teamSettingsController.getProject(), validateButton);

        getChildren().addAll(createPropertiesContainer(Config.getString("team.settings.location"), locationPane),
                             createPropertiesContainer(Config.getString("team.settings.personal"), personalPane),
                             useAsDefault,
                             validateButton);

        setupContent();
        updateOKButtonBinding();
        if (!teamSettingsController.hasProject()){
            useAsDefault.setSelected(true);
            useAsDefault.setDisable(true);
        }
    }
    
    /**
     * Request focus to whatever field seems the most likely to be filled out next.
     */
    public void doRequestFocus()
    {
        if (locationPrimaryField != null && locationPrimaryField.getText().isEmpty())
        {
            // If the location hasn't been specified, that should be first:
            locationPrimaryField.requestFocus();
        }
        else if (personalPrimaryField.getText().isEmpty())
        {
            // Otherwise, if the name/username hasn't been set, select that:
            personalPrimaryField.requestFocus();
        }
        else
        {
            // Otherwise, select the password field:
            passwordField.requestFocus();
        }
    }

    private GridPane createGridPane()
    {
        GridPane pane = new GridPane();
        pane.getStyleClass().add("grid");

        ColumnConstraints column1 = new ColumnConstraints();
        column1.setPrefWidth(102);
        // Second column gets any extra width
        ColumnConstraints column2 = new ColumnConstraints();
        column2.setPrefWidth(260);
        column2.setHgrow(Priority.ALWAYS);
        pane.getColumnConstraints().addAll(column1, column2);

        return pane;
    }

    private Pane createPropertiesContainer(String title, Pane gridPane)
    {
        VBox container = new VBox();
        container.setSpacing(-5);
        container.getChildren().addAll(new Label(title), gridPane);
        return container;
    }

    private void preparePanes(ServerType type)
    {
        prepareLocationPane(type);
        preparePersonalPane(type);

        setProviderSettings();

        useAsDefault.setDisable(false);
    }

    private void preparePersonalPane(ServerType type)
    {
        personalPane.getChildren().clear();

        switch (type) {
            case Subversion:
                personalPane.addRow(0, userLabel, userField);
                personalPane.addRow(1, passwordLabel, passwordField);
                personalPane.addRow(2, groupLabel, groupField);
                personalPrimaryField = userField;
                break;
            case Git:
                personalPane.addRow(0, yourNameLabel, yourNameField);
                personalPane.addRow(1, yourEmailLabel, yourEmailField);
                personalPane.addRow(2, userLabel, userField);
                personalPane.addRow(3, passwordLabel, passwordField);
                personalPrimaryField = yourNameField;
                break;
            default:
                Debug.reportError(type + " is not recognisable as s server type");
        }
    }

    private void prepareLocationPane(ServerType type)
    {
        locationPane.getChildren().clear();
        protocolComboBox.setEditable(false);

        switch (type) {
            case Subversion:
                locationPane.addRow(0, serverLabel, serverField);
                locationPane.addRow(1, prefixLabel, prefixField);
                locationPane.addRow(2, protocolLabel, protocolComboBox);
                locationPrimaryField = serverField;
                break;
            case Git:
                locationPane.addRow(0, uriLabel, uriField);
                locationPrimaryField = uriField;
                break;
            default:
                Debug.reportError(type + " is not recognisable as s server type");
        }
    }
    
    /**
     * Set a text field's text property, adjusting null to the empty string.
     * 
     * @param field  the text field to set the text for
     * @param value  the value to set the text property to, or null for the empty string
     */
    private void setTextFieldText(TextField field, String value)
    {
        field.setText(value == null ? "" : value);
    }

    private void setupContent()
    {
        String user = teamSettingsController.getPropString("bluej.teamsettings.user");
        if (user != null) {
            setUser(user);
        }
        
        String yourName = teamSettingsController.getPropString("bluej.teamsettings.yourName");
        if (yourName != null){
            setYourName(yourName);
        }
        
        String yourEmail = teamSettingsController.getPropString("bluej.teamsettings.yourEmail");
        if (yourEmail != null){
            setYourEmail(yourEmail);
        }
        
        String password = teamSettingsController.getPasswordString();
        if (password != null) {
            setPassword(password);
        }
        String group = teamSettingsController.getPropString("bluej.teamsettings.groupname");
        if(group != null) {
            setGroup(group);
        }
        String useAsDefault = teamSettingsController.getPropString("bluej.teamsettings.useAsDefault");
        if (useAsDefault != null) {
            setUseAsDefault(Boolean.getBoolean(useAsDefault));
        }
        
        String providerName = teamSettingsController.getPropString("bluej.teamsettings.vcs");
        // We always go through the providers.  If the user had no preference,
        // we select the first one, and update the email/name enabled states accordingly:
        List<TeamworkProvider> teamProviders = teamSettingsController.getTeamworkProviders();
        for (int index = 0; index < teamProviders.size(); index++)
        {
            TeamworkProvider provider = teamProviders.get(index);
            if (provider.getProviderName().equalsIgnoreCase(providerName)
                || (providerName == null && index == 0))
            {
                // Select first if no stored preference:
                serverTypes.select(ServerType.valueOf(teamProviders.get(index).getProviderName()));
                if (provider.needsEmail())
                {
                    if (teamSettingsController.getProject() != null)
                    {
                        File respositoryRoot = teamSettingsController.getProject().getProjectDir();
                        setTextFieldText(yourEmailField, provider.getYourEmailFromRepo(respositoryRoot));
                        setTextFieldText(yourNameField, provider.getYourNameFromRepo(respositoryRoot));
                    }
                }
                break;
            }
        }
        
        setProviderSettings();
    }
    
    /**
     * Set settings to provider-specific values (repository prefix, server, protocol).
     * The values are remembered on a per-provider basis; this sets the fields to show
     * the remembered values for the selected provider. 
     */
    private void setProviderSettings()
    {
        String keyBase = "bluej.teamsettings."
            + getSelectedProvider().getProviderName().toLowerCase() + ".";
        
        String prefix = teamSettingsController.getPropString(keyBase + "repositoryPrefix");
        if (prefix != null) {
            setPrefix(prefix);
        }
        String server = teamSettingsController.getPropString(keyBase + "server");
        if (server != null) {
            setServer(server);
        }

        fillProtocolSelections();
        
        String protocol = readProtocolString();
        if (protocol != null){
            setProtocol(protocol);
        }
    }

    /**
     * Empty the protocol selection box, then fill it with the available protocols
     * from the currently selected teamwork provider.
     */
    private void fillProtocolSelections()
    {
        ServerType type = serverTypes.selectedProperty().get();
        if (type != selectedServerType) {
            selectedServerType = type;
            protocolComboBox.getItems().clear();

            TeamworkProvider provider = teamSettingsController.getTeamworkProvider(type);
            protocolComboBox.getItems().addAll(Arrays.asList(provider.getProtocols()));
        }
    }
    
    private String readProtocolString()
    {
        String keyBase = "bluej.teamsettings."
            + getSelectedProvider().getProviderName().toLowerCase() + "."; 
        return teamSettingsController.getPropString(keyBase + "protocol");
    }

    private void setUser(String user)
    {
        userField.setText(user);
    }
    
    private void setYourName(String yourName)
    {
        yourNameField.setText(yourName);
    }
    
    private void setYourEmail(String yourEmail)
    {
        yourEmailField.setText(yourEmail);
    }
    
    private void setPassword(String password)
    {
        passwordField.setText(password);
    }
    
    private void setGroup(String group)
    {
        groupField.setText(group);
    }
    
    private void setPrefix(String prefix)
    {
        prefixField.setText(prefix);
    }
    
    private void setServer(String server)
    {
        serverField.setText(server);
    }
    
    /**
     * Set the protocol to that identified by the given protocol key.
     */
    private void setProtocol(String protocolKey)
    {
        String protocolLabel = getSelectedProvider().getProtocolLabel(protocolKey);
        protocolComboBox.getSelectionModel().select(protocolLabel);
    }
    
    private void setUseAsDefault(boolean use)
    {
        useAsDefault.setSelected(use);
    }
    
    public TeamworkProvider getSelectedProvider()
    {
        return teamSettingsController.getTeamworkProviders().stream()
                .filter(provider -> provider.getProviderName().equals(serverTypes.selectedProperty().get().name()))
                .findAny().get();
    }
    
    private String getUser()
    {
        return userField.getText();
    }

    private String getPassword()
    {
        return passwordField.getText();
    }

    private String getGroup()
    {
        //DCVS does not have group.
        if (getSelectedProvider().needsEmail()){
            return "";
        }
        return groupField.getText();
    }
    
    private String getPrefix()
    {
        if (getSelectedProvider().needsEmail()) {
            try {
                URI uri = new URI(uriField.getText());
                return uri.getPath();
            } catch (URISyntaxException ex) {
                return null;
            }
        }
        return prefixField.getText();
    }
    
    private String getServer()
    {
        if (getSelectedProvider().needsEmail()) {
            try {
                URI uri = new URI(uriField.getText());
                return uri.getHost();
            } catch (URISyntaxException ex) {
                return null;
            }
        }
        return serverField.getText();
    }
    
    private String getProtocolKey()
    {
        if (getSelectedProvider().needsEmail()) {
            try {
                URI uri = new URI(uriField.getText());
                return uri.getScheme();
            } catch (URISyntaxException ex) {
                return null;
            }
        }
        int protocol = protocolComboBox.getSelectionModel().getSelectedIndex();
        if (protocol == -1) {
            return null;
        }
        return getSelectedProvider().getProtocolKey(protocol);
    }
    
    public boolean getUseAsDefault()
    {
        return useAsDefault.isSelected();
    }
    
    private String getYourName()
    {
        return yourNameField.getText();
    }
    
    private String getYourEmail()
    {
        return yourEmailField.getText();
    }
    
    public TeamSettings getSettings()
    {
        TeamSettings result = new TeamSettings(getSelectedProvider(), getProtocolKey(),
                getServer(), getPrefix(), getGroup(), getUser(), getPassword());
        result.setYourEmail(getYourEmail());
        result.setYourName(getYourName());
        return result;
    }

    /**
     * Check whether the "ok" button should be enabled or disabled according
     * to whether required fields have been provided.
     */
    private void updateOKButtonBinding()
    {
        teamSettingsDialog.getOkButton().disableProperty().unbind();

        BooleanBinding disabled = userField.textProperty().isEmpty();
        switch (serverTypes.selectedProperty().get()) {
            case Subversion:
                disabled = disabled.or(serverField.textProperty().isEmpty());
                break;
            case Git:
                disabled = disabled.or(uriField.textProperty().isEmpty())
                        .or(yourNameField.textProperty().isEmpty())
                        .or(yourEmailField.textProperty().isEmpty())
                        .or(Bindings.createBooleanBinding(() -> !yourEmailField.getText().contains("@"), yourEmailField.textProperty()));
                break;
        }

        teamSettingsDialog.getOkButton().disableProperty().bind(disabled);
    }

    /**
     * Disable the fields used to specify the repository:
     * group, prefix, server and protocol
     */
    public void disableRepositorySettings()
    {
        serverTypes.setDisable(true);
        groupField.setDisable(true);
        prefixField.setDisable(true);
        serverField.setDisable(true);
        protocolComboBox.setDisable(true);
        uriField.setDisable(true);

        if (uriField.isVisible() && uriField.getText().isEmpty()){
            //update uri.
            uriField.setText(TeamSettings.getURI(readProtocolString(), serverField.getText(), prefixField.getText()));
        }

        groupLabel.setDisable(true);
        prefixLabel.setDisable(true);
        serverLabel.setDisable(true);
        protocolLabel.setDisable(true);
    }
}
