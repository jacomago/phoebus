/*******************************************************************************
 * Copyright (c) 2010-2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.trends.databrowser3.ui;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;
import org.csstudio.trends.databrowser3.Messages;
import org.csstudio.trends.databrowser3.model.ArchiveDataSource;
import org.csstudio.trends.databrowser3.model.Model;
import org.csstudio.trends.databrowser3.preferences.Preferences;

/** Dialog for selecting an Archive Data Source
 */
@SuppressWarnings("nls")
public class AddArchiveSource extends Dialog<ArchiveDataSource>
{
    private ChoiceBox<ArchiveDataSource> archiveSourceChoiceBox = null;

    public AddArchiveSource(Model model) {
        setTitle(Messages.AddArchiveSource);
        getDialogPane().setContent(createContent(model));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        final Button ok = (Button) getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            if (archiveSourceChoiceBox.getValue() == null)
                event.consume();
        });

        setResultConverter(button -> {
            if (button == ButtonType.OK)
                return archiveSourceChoiceBox.getValue();
            return null;
        });
    }

    private Node createContent(Model model) {
        final GridPane gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        final ColumnConstraints fill = new ColumnConstraints();
        fill.setFillWidth(true);
        fill.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().add(fill);
        int row = 0;

        final List<ArchiveDataSource> archiveOptions = FXCollections.observableArrayList(Preferences.archive_urls);

        gridPane.add(new Label(Messages.AddArchiveSource), 0, row++);

        archiveSourceChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(archiveOptions));
        archiveSourceChoiceBox.setConverter(new StringConverter<ArchiveDataSource>() {
            @Override
            public String toString(final ArchiveDataSource archive) {
                return archive == null ? "" : archive.getName();
            }
            @Override
            public ArchiveDataSource fromString(final String s) { return null; }
        });
        archiveSourceChoiceBox.setTooltip(new Tooltip(Messages.AddArchiveSource));
        archiveSourceChoiceBox.setMaxWidth(Double.MAX_VALUE);
        if (!archiveOptions.isEmpty())
            archiveSourceChoiceBox.setValue(archiveOptions.get(0));
        gridPane.add(archiveSourceChoiceBox, 0, row);

        return gridPane;
    }
}
