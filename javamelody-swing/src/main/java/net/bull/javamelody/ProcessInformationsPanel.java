/*
 * Copyright 2008-2012 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Java Melody is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Java Melody is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java Melody.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bull.javamelody;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.bull.javamelody.swing.MButton;
import net.bull.javamelody.swing.MHyperLink;
import net.bull.javamelody.swing.Utilities;
import net.bull.javamelody.swing.table.MTable;
import net.bull.javamelody.swing.table.MTableScrollPane;

/**
 * Panel de la liste des process.
 * @author Emeric Vernat
 */
class ProcessInformationsPanel extends MelodyPanel {
	private static final long serialVersionUID = 1L;

	@SuppressWarnings("all")
	private Map<String, List<ProcessInformations>> processInformationsByTitle;

	ProcessInformationsPanel(RemoteCollector remoteCollector) throws IOException {
		super(remoteCollector);

		refresh();
	}

	final void refresh() throws IOException {
		removeAll();

		this.processInformationsByTitle = getRemoteCollector().collectProcessInformations();

		setName(getString("Processus"));

		add(createScrollPanes(), BorderLayout.CENTER);

		add(createButtonsPanel(), BorderLayout.SOUTH);
	}

	private JPanel createScrollPanes() {
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		for (final Map.Entry<String, List<ProcessInformations>> entry : processInformationsByTitle
				.entrySet()) {
			final String title;
			if (processInformationsByTitle.size() == 1) {
				title = getString("Processus");
			} else {
				title = entry.getKey();
			}
			final List<ProcessInformations> processInformationsList = entry.getValue();
			final boolean windows = HtmlProcessInformationsReport
					.isWindowsProcessList(processInformationsList);
			final MTableScrollPane<ProcessInformations> tableScrollPane = new MTableScrollPane<>();
			final MTable<ProcessInformations> table = tableScrollPane.getTable();
			table.addColumn("user", getString("Utilisateur"));
			table.addColumn("pid", getString("PID"));
			if (!windows) {
				table.addColumn("cpuPercentage", getString("cpu"));
				table.addColumn("memPercentage", getString("mem"));
			}
			table.addColumn("vsz", getString("vsz"));
			if (!windows) {
				table.addColumn("rss", getString("rss"));
				table.addColumn("tty", getString("tty"));
				table.addColumn("stat", getString("stat"));
				table.addColumn("start", getString("start"));
			}
			table.addColumn("cpuTime", getString("cpuTime"));
			table.addColumn("command", getString("command"));

			table.setList(processInformationsList);

			final JLabel titleLabel = Utilities.createParagraphTitle(title, "processes.png");
			panel.add(titleLabel);
			panel.add(tableScrollPane);

			if (!windows) {
				final MHyperLink hyperLink = new MHyperLink(" ps command reference",
						"http://en.wikipedia.org/wiki/Ps_(Unix)");
				panel.add(hyperLink);
			}
		}

		for (final Component component : panel.getComponents()) {
			((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		return panel;
	}

	private JPanel createButtonsPanel() {
		final MButton pdfButton = createPdfButton();
		pdfButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					actionPdf();
				} catch (final IOException ex) {
					showException(ex);
				}
			}
		});
		final Serializable serializable;
		if (processInformationsByTitle.size() == 1) {
			serializable = (Serializable) processInformationsByTitle.values().iterator().next();
		} else {
			serializable = (Serializable) processInformationsByTitle;
		}
		final MButton xmlJsonButton = createXmlJsonButton(serializable);

		final MButton refreshButton = createRefreshButton();
		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					refresh();
				} catch (final IOException ex) {
					showException(ex);
				}
			}
		});

		return Utilities.createButtonsPanel(refreshButton, pdfButton, xmlJsonButton);
	}

	final void actionPdf() throws IOException {
		final File tempFile = createTempFileForPdf();
		final PdfOtherReport pdfOtherReport = createPdfOtherReport(tempFile);
		try {
			pdfOtherReport.writeProcessInformations(processInformationsByTitle);
		} finally {
			pdfOtherReport.close();
		}
		Desktop.getDesktop().open(tempFile);
	}
}
