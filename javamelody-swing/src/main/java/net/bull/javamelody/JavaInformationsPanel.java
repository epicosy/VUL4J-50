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
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;

import net.bull.javamelody.PdfJavaInformationsReport.Bar;
import net.bull.javamelody.swing.MButton;
import net.bull.javamelody.swing.MHyperLink;
import net.bull.javamelody.swing.util.SpringUtilities;

import com.lowagie.text.Font;

/**
 * Panel des informations systèmes.
 * @author Emeric Vernat
 */
class JavaInformationsPanel extends MelodyPanel {
	static final ImageIcon PLUS_ICON = ImageIconCache.getImageIcon("bullets/plus.png");
	static final ImageIcon MINUS_ICON = ImageIconCache.getImageIcon("bullets/minus.png");

	private static final ImageIcon XML_ICON = ImageIconCache.getScaledImageIcon("xml.png", 14, 14);
	private static final long serialVersionUID = 1L;

	private final boolean noDatabase = Parameters.isNoDatabase();
	private final DecimalFormat integerFormat = I18N.createIntegerFormat();
	private final DecimalFormat decimalFormat = I18N.createPercentFormat();
	private final JavaInformations javaInformations;
	private final URL monitoringUrl;
	private final JPanel gridPanel;
	private JavaInformationsPanel detailsPanel;

	JavaInformationsPanel(RemoteCollector remoteCollector, JavaInformations javaInformations,
			URL monitoringUrl) {
		super(remoteCollector);
		assert javaInformations != null;
		this.javaInformations = javaInformations;
		this.monitoringUrl = monitoringUrl;
		gridPanel = new JPanel(new SpringLayout());
		gridPanel.setOpaque(false);
		add(gridPanel, BorderLayout.NORTH);
	}

	void showSummary() {
		addLabel(getString("Host"));
		final JLabel hostLabel = new JLabel(javaInformations.getHost());
		hostLabel.setFont(hostLabel.getFont().deriveFont(Font.BOLD));
		addJLabel(hostLabel);
		final MemoryInformations memoryInformations = javaInformations.getMemoryInformations();
		final long usedMemory = memoryInformations.getUsedMemory();
		final long maxMemory = memoryInformations.getMaxMemory();
		addLabel(getString("memoire_utilisee"));
		//		writeGraph("usedMemory", integerFormat.format(usedMemory / 1024 / 1024));
		final String divide = " / ";
		addJLabel(toBarWithAlert(integerFormat.format(usedMemory / 1024 / 1024) + ' '
				+ getString("Mo") + divide + integerFormat.format(maxMemory / 1024 / 1024) + ' '
				+ getString("Mo"), memoryInformations.getUsedMemoryPercentage(), "-Xmx"));
		if (javaInformations.getSessionCount() >= 0) {
			addLabel(getString("nb_sessions_http"));
			// 			writeGraph("httpSessions", integerFormat.format(javaInformations.getSessionCount()));
			addValue(integerFormat.format(javaInformations.getSessionCount()));
		}
		addLabel(getString("nb_threads_actifs") + "\n(" + getString("Requetes_http_en_cours") + ')');
		//		writeGraph("activeThreads", integerFormat.format(javaInformations.getActiveThreadCount()));
		addValue(integerFormat.format(javaInformations.getActiveThreadCount()));
		if (!noDatabase) {
			addLabel(getString("nb_connexions_actives"));
			// writeGraph("activeConnections", integerFormat.format(javaInformations.getActiveConnectionCount()));
			addValue(integerFormat.format(javaInformations.getActiveConnectionCount()));
			final int usedConnectionCount = javaInformations.getUsedConnectionCount();
			final int maxConnectionCount = javaInformations.getMaxConnectionCount();
			addLabel(getString("nb_connexions_utilisees") + "\n(" + getString("ouvertes") + ')');
			//			writeGraph("usedConnections", integerFormat.format(usedConnectionCount));
			if (maxConnectionCount > 0) {
				addJLabel(toBarWithAlert(integerFormat.format(usedConnectionCount),
						javaInformations.getUsedConnectionPercentage(), null));
			} else {
				addValue(integerFormat.format(usedConnectionCount) + divide
						+ integerFormat.format(maxConnectionCount));
			}
		}
		if (javaInformations.getSystemLoadAverage() >= 0) {
			addLabel(getString("Charge_systeme"));
			//			writeGraph("systemLoad", decimalFormat.format(javaInformations.getSystemLoadAverage()));
			addValue(decimalFormat.format(javaInformations.getSystemLoadAverage()));
		}
		makeGrid();
	}

	void showDetails(boolean repeatHost) {
		if (detailsPanel != null) {
			detailsPanel.setVisible(!detailsPanel.isVisible());
		} else {
			detailsPanel = new JavaInformationsPanel(getRemoteCollector(), javaInformations,
					monitoringUrl);
			detailsPanel.addDetails(repeatHost);
			add(detailsPanel, BorderLayout.SOUTH);
			// sans cela, le panel n'apparaît pas la première fois
			detailsPanel.setVisible(false);
			detailsPanel.setVisible(true);
		}
	}

	private void addDetails(boolean repeatHost) {
		if (repeatHost) {
			addLabel(getString("Host"));
			final JLabel hostLabel = new JLabel(javaInformations.getHost());
			hostLabel.setFont(hostLabel.getFont().deriveFont(Font.BOLD));
			addJLabel(hostLabel);
		}
		addLabel(getString("OS"));
		final String osIconName = HtmlJavaInformationsReport
				.getOSIconName(javaInformations.getOS());
		final JLabel osLabel = new JLabel(javaInformations.getOS() + " ("
				+ javaInformations.getAvailableProcessors() + ' ' + getString("coeurs") + ')');
		if (osIconName != null) {
			osLabel.setIcon(ImageIconCache.getImageIcon("servers/" + osIconName));
		}
		addJLabel(osLabel);
		addLabel(getString("Java"));
		addValue(javaInformations.getJavaVersion());
		addLabel(getString("JVM"));
		final JLabel jvmVersionLabel = new JLabel(javaInformations.getJvmVersion());
		if (javaInformations.getJvmVersion().contains("Client")) {
			jvmVersionLabel.setIcon(ImageIconCache.getImageIcon("alert.png"));
			jvmVersionLabel.setHorizontalTextPosition(SwingConstants.LEFT);
			jvmVersionLabel.setToolTipText(getString("Client_JVM"));
		}
		addJLabel(jvmVersionLabel);
		addLabel(getString("PID"));
		addValue(javaInformations.getPID());
		final long unixOpenFileDescriptorCount = javaInformations.getUnixOpenFileDescriptorCount();
		if (unixOpenFileDescriptorCount >= 0) {
			final long unixMaxFileDescriptorCount = javaInformations
					.getUnixMaxFileDescriptorCount();
			addLabel(getString("nb_fichiers"));
			addJLabel(toBarWithAlert(integerFormat.format(unixOpenFileDescriptorCount) + " / "
					+ integerFormat.format(unixMaxFileDescriptorCount),
					javaInformations.getUnixOpenFileDescriptorPercentage(), null));
			// writeGraph("fileDescriptors", integerFormat.format(unixOpenFileDescriptorCount));
		}
		writeServerInfoAndContextPath();
		addLabel(getString("Demarrage"));
		addValue(I18N.createDateAndTimeFormat().format(javaInformations.getStartDate()));
		addLabel(getString("Arguments_JVM"));
		addValue(javaInformations.getJvmArguments());

		if (javaInformations.getSessionCount() >= 0) {
			addLabel(getString("httpSessionsMeanAge"));
			// writeGraph("httpSessionsMeanAge", integerFormat.format(javaInformations.getSessionMeanAgeInMinutes()));
			addValue(integerFormat.format(javaInformations.getSessionMeanAgeInMinutes()));
		}

		writeTomcatInformations(javaInformations.getTomcatInformationsList());

		writeMemoryInformations(javaInformations.getMemoryInformations());

		if (javaInformations.getFreeDiskSpaceInTemp() >= 0) {
			// on considère que l'espace libre sur le disque dur est celui sur la partition du répertoire temporaire
			addLabel(getString("Free_disk_space"));
			addValue(integerFormat.format(javaInformations.getFreeDiskSpaceInTemp() / 1024 / 1024)
					+ ' ' + getString("Mo"));
		}

		writeDatabaseVersionAndDataSourceDetails();

		if (javaInformations.isDependenciesEnabled()) {
			addLabel(getString("Dependencies"));
			writeDependencies();
		}
		makeGrid();
	}

	private void writeServerInfoAndContextPath() {
		final String serverInfo = javaInformations.getServerInfo();
		if (serverInfo != null) {
			addLabel(getString("Serveur"));
			final String applicationServerIconName = HtmlJavaInformationsReport
					.getApplicationServerIconName(serverInfo);
			final JLabel serverInfoLabel = new JLabel(serverInfo);
			if (applicationServerIconName != null) {
				serverInfoLabel.setIcon(ImageIconCache.getImageIcon("servers/"
						+ applicationServerIconName));
			}
			addJLabel(serverInfoLabel);
			addLabel(getString("Contexte_webapp"));
			addValue(javaInformations.getContextPath());
		}
	}

	private void writeDatabaseVersionAndDataSourceDetails() {
		if (!noDatabase && javaInformations.getDataBaseVersion() != null) {
			addLabel(getString("Base_de_donnees"));
			addValue(javaInformations.getDataBaseVersion());
		}
		if (javaInformations.getDataSourceDetails() != null) {
			addLabel(getString("DataSource_jdbc"));
			addValue(javaInformations.getDataSourceDetails());
			addLabel("");
			final MHyperLink dataSourceReferenceHyperLink = new MHyperLink("DataSource reference",
					"http://commons.apache.org/dbcp/apidocs/org/apache/commons/dbcp/BasicDataSource.html");
			gridPanel.add(dataSourceReferenceHyperLink);
		}
	}

	private void writeTomcatInformations(List<TomcatInformations> tomcatInformationsList) {
		final List<TomcatInformations> list = new ArrayList<>();
		for (final TomcatInformations tomcatInformations : tomcatInformationsList) {
			if (tomcatInformations.getRequestCount() > 0) {
				list.add(tomcatInformations);
			}
		}
		//		final boolean onlyOne = list.size() == 1;
		final String equals = " = ";
		for (final TomcatInformations tomcatInformations : list) {
			addLabel("Tomcat " + I18N.htmlEncode(tomcatInformations.getName(), false));
			// rq: on n'affiche pas pour l'instant getCurrentThreadCount
			final int currentThreadsBusy = tomcatInformations.getCurrentThreadsBusy();
			final String value = getString("busyThreads") + equals
					+ integerFormat.format(currentThreadsBusy) + " /  "
					+ integerFormat.format(tomcatInformations.getMaxThreads()) + '\n'
					+ getString("bytesReceived") + equals
					+ integerFormat.format(tomcatInformations.getBytesReceived()) + '\n'
					+ getString("bytesSent") + equals
					+ integerFormat.format(tomcatInformations.getBytesSent()) + '\n'
					+ getString("requestCount") + equals
					+ integerFormat.format(tomcatInformations.getRequestCount()) + '\n'
					+ getString("errorCount") + equals
					+ integerFormat.format(tomcatInformations.getErrorCount()) + '\n'
					+ getString("processingTime") + equals
					+ integerFormat.format(tomcatInformations.getProcessingTime()) + '\n'
					+ getString("maxProcessingTime") + equals
					+ integerFormat.format(tomcatInformations.getMaxTime());
			final JLabel label = toBarWithAlert(value, 100d * currentThreadsBusy
					/ tomcatInformations.getMaxThreads(), null);
			label.setVerticalTextPosition(SwingConstants.TOP);
			addJLabel(label);
			//			if (onlyOne) {
			//				writeGraph("tomcatBusyThreads", integerFormat.format(currentThreadsBusy));
			//          }
			//			if (onlyOne) {
			//				writeGraph("tomcatBytesReceived",
			//						integerFormat.format(tomcatInformations.getBytesReceived()));
			//			}
			//			if (onlyOne) {
			//				writeGraph("tomcatBytesSent",
			//						integerFormat.format(tomcatInformations.getBytesSent()));
			//			}
		}
	}

	private void writeMemoryInformations(MemoryInformations memoryInformations) {
		addLabel(getString("Gestion_memoire"));
		addValue(memoryInformations.getMemoryDetails().replace(" Mo", ' ' + getString("Mo")));

		final long usedPermGen = memoryInformations.getUsedPermGen();
		if (usedPermGen > 0) {
			// perm gen est à 0 sous jrockit
			final long maxPermGen = memoryInformations.getMaxPermGen();
			addLabel(getString("Memoire_Perm_Gen"));
			final String permGen = integerFormat.format(usedPermGen / 1024 / 1024) + ' '
					+ getString("Mo");
			if (maxPermGen > 0) {
				addJLabel(toBarWithAlert(
						permGen + " / " + integerFormat.format(maxPermGen / 1024 / 1024) + ' '
								+ getString("Mo"), memoryInformations.getUsedPermGenPercentage(),
						"-XX:MaxPermSize"));
			} else {
				addValue(permGen);
			}
		}
	}

	private void writeDependencies() {
		final int nbDependencies = javaInformations.getDependenciesList().size();
		final JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		final JLabel nbDependenciesLabel = new JLabel(getFormattedString("nb_dependencies",
				nbDependencies));
		panel.add(nbDependenciesLabel, BorderLayout.CENTER);
		if (nbDependencies > 0) {
			nbDependenciesLabel.setText(nbDependenciesLabel.getText() + " ; ");
			final JPanel buttonsPanel = new JPanel(new BorderLayout());
			buttonsPanel.setOpaque(false);
			final MButton detailsButton = new MButton(getString("Details"), PLUS_ICON);
			buttonsPanel.add(detailsButton, BorderLayout.WEST);
			if (javaInformations.doesPomXmlExists() && Parameters.isSystemActionsEnabled()) {
				final MButton pomXmlButton = new MButton(getString("pom.xml"), XML_ICON);
				buttonsPanel.add(pomXmlButton, BorderLayout.EAST);
				pomXmlButton.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						try {
							Desktop.getDesktop().browse(
									new URI(getMonitoringUrl().toExternalForm() + "?part=pom.xml"));
						} catch (final Exception ex) {
							showException(ex);
						}
					}
				});
			}
			panel.add(buttonsPanel, BorderLayout.EAST);

			final JLabel dependenciesLabel = new JLabel(
					replaceLineFeedWithHtmlBr(javaInformations.getDependencies()));
			dependenciesLabel.setVisible(false);
			panel.add(dependenciesLabel, BorderLayout.SOUTH);
			final JPanel localGridPanel = gridPanel;
			detailsButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					dependenciesLabel.setVisible(!dependenciesLabel.isVisible());
					localGridPanel.validate();
					if (detailsButton.getIcon() == PLUS_ICON) {
						detailsButton.setIcon(MINUS_ICON);
					} else {
						detailsButton.setIcon(PLUS_ICON);
					}
				}
			});
		}
		gridPanel.add(panel);
	}

	private void makeGrid() {
		SpringUtilities.makeCompactGrid(gridPanel, gridPanel.getComponentCount() / 2, 2, 0, 0, 10,
				5);
	}

	private void addLabel(String text) {
		final String tmp = replaceLineFeedWithHtmlBr(text);
		final JLabel label = new JLabel(tmp + ": ");
		label.setVerticalAlignment(SwingConstants.TOP);
		addJLabel(label);
	}

	private void addValue(String value) {
		final String tmp = replaceLineFeedWithHtmlBr(value);
		addJLabel(new JLabel(tmp));
	}

	private void addJLabel(JLabel jLabel) {
		gridPanel.add(jLabel);
	}

	static JLabel toBar(String text, double percentValue) {
		final String tmp = replaceLineFeedWithHtmlBr(text);
		final JLabel label = new JLabel(tmp);
		label.setIconTextGap(10);
		try {
			label.setIcon(new ImageIcon(Bar.toBar(percentValue)));
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
		label.setHorizontalTextPosition(SwingConstants.LEFT);
		final double myPercent = Math.max(Math.min(percentValue, 100d), 0d);
		label.setToolTipText(I18N.createPercentFormat().format(myPercent) + '%');
		return label;
	}

	private static JLabel toBarWithAlert(String text, double percentValue,
			String configurationDetail) {
		final JLabel label = toBar(text, percentValue);
		if (percentValue >= JavaInformations.HIGH_USAGE_THRESHOLD_IN_PERCENTS) {
			try {
				label.setIcon(new ImageIcon(Bar.toBarWithAlert(percentValue)));
			} catch (final IOException e) {
				throw new IllegalStateException(e);
			}
			String toolTipText = label.getToolTipText();
			toolTipText = "<html>" + toolTipText + "<br/>" + getString("High_usage");
			if (configurationDetail != null) {
				toolTipText += " (" + configurationDetail + ')';
			}
			label.setToolTipText(toolTipText);
		}
		return label;
	}

	private static String replaceLineFeedWithHtmlBr(String text) {
		if (text.indexOf('\n') != -1) {
			// JLabel accepte la syntaxe html
			return "<html>" + text.replace("\n", "<br/>");
		}
		return text;
	}

	URL getMonitoringUrl() {
		return monitoringUrl;
	}
}
