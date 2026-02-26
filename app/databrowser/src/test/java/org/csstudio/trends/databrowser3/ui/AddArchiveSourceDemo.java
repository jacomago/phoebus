/*******************************************************************************
 * Copyright (c) 2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.trends.databrowser3.ui;

import org.csstudio.trends.databrowser3.model.ArchiveDataSource;
import org.csstudio.trends.databrowser3.model.Model;
import org.phoebus.ui.javafx.ApplicationWrapper;

import javafx.stage.Stage;

/** Demo of the {@link AddArchiveSource}
 *  @author Sky Brewer
 */
@SuppressWarnings("nls")
public class AddArchiveSourceDemo extends ApplicationWrapper
{
    @Override
    public void start(final Stage stage) throws Exception
    {
        final Model model = new Model();
        final AddArchiveSource dlg = new AddArchiveSource(model);

        final ArchiveDataSource source = dlg.showAndWait().orElse(null);
        if (source != null)
            System.out.println("Selected: " + source.getName() + " @ " + source.getUrl());
        else
            System.out.println("Cancelled");
    }

    public static void main(final String[] args)
    {
        launch(AddArchiveSourceDemo.class, args);
    }
}
