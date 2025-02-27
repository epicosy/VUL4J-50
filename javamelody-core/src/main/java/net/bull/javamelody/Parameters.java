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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

/**
 * Classe d'accès aux paramètres du monitoring.
 * @author Emeric Vernat
 */
final class Parameters {
	static final String PARAMETER_SYSTEM_PREFIX = "javamelody.";
	static final File TEMPORARY_DIRECTORY = new File(System.getProperty("java.io.tmpdir"));
	static final String JAVA_VERSION = System.getProperty("java.version");
	static final String JAVAMELODY_VERSION = getJavaMelodyVersion();
	// résolution (ou pas) par défaut en s de stockage des valeurs dans les fichiers RRD
	private static final int DEFAULT_RESOLUTION_SECONDS = 60;
	// stockage des fichiers RRD de JRobin dans le répertoire temp/javamelody/<context> par défaut
	private static final String DEFAULT_DIRECTORY = "javamelody";
	// nom du fichier stockant les applications et leurs urls dans le répertoire de stockage
	private static final String COLLECTOR_APPLICATIONS_FILENAME = "applications.properties";
	private static Map<String, List<URL>> urlsByApplications;

	private static FilterConfig filterConfig;
	private static ServletContext servletContext;
	private static String lastConnectUrl;
	private static Properties lastConnectInfo;
	private static boolean dnsLookupsDisabled;

	private Parameters() {
		super();
	}

	static void initialize(FilterConfig config) {
		filterConfig = config;
		if (config != null) {
			final ServletContext context = config.getServletContext();
			initialize(context);
		}
	}

	static void initialize(ServletContext context) {
		if ("1.5".compareTo(JAVA_VERSION) > 0) {
			throw new IllegalStateException("La version java doit être 1.5 au minimum et non "
					+ JAVA_VERSION);
		}
		servletContext = context;

		dnsLookupsDisabled = Boolean.parseBoolean(getParameter(Parameter.DNS_LOOKUPS_DISABLED));
	}

	static void initJdbcDriverParameters(String connectUrl, Properties connectInfo) {
		Parameters.lastConnectUrl = connectUrl;
		Parameters.lastConnectInfo = connectInfo;
	}

	/**
	 * @return Contexte de servlet de la webapp, soit celle monitorée ou soit celle de collecte.
	 */
	static ServletContext getServletContext() {
		assert servletContext != null;
		return servletContext;
	}

	static String getLastConnectUrl() {
		return lastConnectUrl;
	}

	static Properties getLastConnectInfo() {
		return lastConnectInfo;
	}

	/**
	 * @return Nom et urls des applications telles que paramétrées dans un serveur de collecte.
	 * @throws IOException e
	 */
	static Map<String, List<URL>> getCollectorUrlsByApplications() throws IOException {
		if (urlsByApplications == null) {
			readCollectorApplications();
		}
		return Collections.unmodifiableMap(urlsByApplications);
	}

	static void addCollectorApplication(String application, List<URL> urls) throws IOException {
		assert application != null;
		assert urls != null && !urls.isEmpty();
		// initialisation si besoin
		getCollectorUrlsByApplications();

		urlsByApplications.put(application, urls);
		writeCollectorApplications();
	}

	static void removeCollectorApplication(String application) throws IOException {
		assert application != null;
		// initialisation si besoin
		getCollectorUrlsByApplications();

		urlsByApplications.remove(application);
		writeCollectorApplications();
	}

	private static void writeCollectorApplications() throws IOException {
		final Properties properties = new Properties();
		for (final Map.Entry<String, List<URL>> entry : urlsByApplications.entrySet()) {
			final List<URL> urls = entry.getValue();
			assert urls != null && !urls.isEmpty();
			final StringBuilder sb = new StringBuilder();
			for (final URL url : urls) {
				final String urlString = url.toString();
				// on enlève le suffixe ajouté précédemment dans parseUrl
				sb.append(urlString.substring(0, urlString.lastIndexOf("/monitoring"))).append(',');
			}
			sb.delete(sb.length() - 1, sb.length());
			properties.put(entry.getKey(), sb.toString());
		}
		final File collectorApplicationsFile = getCollectorApplicationsFile();
		final File directory = collectorApplicationsFile.getParentFile();
		if (!directory.mkdirs() && !directory.exists()) {
			throw new IOException("JavaMelody directory can't be created: " + directory.getPath());
		}
		final FileOutputStream output = new FileOutputStream(collectorApplicationsFile);
		try {
			properties.store(output, "urls of the applications to monitor");
		} finally {
			output.close();
		}
	}

	private static void readCollectorApplications() throws IOException {
		// le fichier applications.properties contient les noms et les urls des applications à monitorer
		// par ex.: recette=http://recette1:8080/myapp
		// ou production=http://prod1:8080/myapp,http://prod2:8080/myapp
		final Map<String, List<URL>> result = new LinkedHashMap<String, List<URL>>();
		final File file = getCollectorApplicationsFile();
		if (file.exists()) {
			final Properties properties = new Properties();
			final FileInputStream input = new FileInputStream(file);
			try {
				properties.load(input);
			} finally {
				input.close();
			}
			@SuppressWarnings("unchecked")
			final List<String> propertyNames = (List<String>) Collections.list(properties
					.propertyNames());
			// propertyNames ne sont pas ordonnés donc on les trie par ordre alphabétique
			Collections.sort(propertyNames);
			for (final String property : propertyNames) {
				result.put(property, parseUrl(String.valueOf(properties.get(property))));
			}
		}
		urlsByApplications = result;
	}

	static File getCollectorApplicationsFile() {
		return new File(getStorageDirectory(""), COLLECTOR_APPLICATIONS_FILENAME);
	}

	static List<URL> parseUrl(String value) throws MalformedURLException {
		// pour un cluster, le paramètre vaut "url1,url2"
		final TransportFormat transportFormat;
		if (Parameters.getParameter(Parameter.TRANSPORT_FORMAT) == null) {
			transportFormat = TransportFormat.SERIALIZED;
		} else {
			transportFormat = TransportFormat.valueOfIgnoreCase(Parameters
					.getParameter(Parameter.TRANSPORT_FORMAT));
		}
		final String suffix = "/monitoring?collector=stop&format=" + transportFormat.getCode();

		final String[] urlsArray = value.split(",");
		final List<URL> urls = new ArrayList<URL>(urlsArray.length);
		for (final String s : urlsArray) {
			String s2 = s.trim();
			while (s2.endsWith("/")) {
				s2 = s2.substring(0, s2.length() - 1);
			}
			final URL url = new URL(s2 + suffix);
			urls.add(url);
		}
		return urls;
	}

	/**
	 * @return nom réseau de la machine
	 */
	static String getHostName() {
		if (dnsLookupsDisabled) {
			return "localhost";
		}

		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (final UnknownHostException ex) {
			return "unknown";
		}
	}

	/**
	 * @return adresse ip de la machine
	 */
	static String getHostAddress() {
		if (dnsLookupsDisabled) {
			return "127.0.0.1"; // NOPMD
		}

		try {
			return InetAddress.getLocalHost().getHostAddress();
		} catch (final UnknownHostException ex) {
			return "unknown";
		}
	}

	/**
	 * @param fileName Nom du fichier de resource.
	 * @return Chemin complet d'une resource.
	 */
	static String getResourcePath(String fileName) {
		final Class<Parameters> classe = Parameters.class;
		final String packageName = classe.getName().substring(0,
				classe.getName().length() - classe.getSimpleName().length() - 1);
		return '/' + packageName.replace('.', '/') + "/resource/" + fileName;
	}

	/**
	 * @return Résolution en secondes des courbes et période d'appels par le serveur de collecte le cas échéant.
	 */
	static int getResolutionSeconds() {
		final String param = getParameter(Parameter.RESOLUTION_SECONDS);
		if (param != null) {
			// lance une NumberFormatException si ce n'est pas un nombre
			final int result = Integer.parseInt(param);
			if (result <= 0) {
				throw new IllegalStateException(
						"Le paramètre resolution-seconds doit être > 0 (entre 60 et 600 recommandé)");
			}
			return result;
		}
		return DEFAULT_RESOLUTION_SECONDS;
	}

	/**
	 * @param application Nom de l'application
	 * @return Répertoire de stockage des compteurs et des données pour les courbes.
	 */
	static File getStorageDirectory(String application) {
		final String param = getParameter(Parameter.STORAGE_DIRECTORY);
		final String dir;
		if (param == null) {
			dir = DEFAULT_DIRECTORY;
		} else {
			dir = param;
		}
		// Si le nom du répertoire commence par '/', on considère que c'est un chemin absolu,
		// sinon on considère que c'est un chemin relatif par rapport au répertoire temporaire
		// ('temp' dans TOMCAT_HOME pour tomcat).
		final String directory;
		if (dir.length() > 0 && new File(dir).isAbsolute()) {
			directory = dir;
		} else {
			directory = TEMPORARY_DIRECTORY.getPath() + '/' + dir;
		}
		if (servletContext != null) {
			return new File(directory + '/' + application);
		}
		return new File(directory);
	}

	/**
	 * Booléen selon que le paramètre no-database vaut true.
	 * @return boolean
	 */
	static boolean isNoDatabase() {
		return Boolean.parseBoolean(Parameters.getParameter(Parameter.NO_DATABASE));
	}

	/**
	 * Booléen selon que le paramètre system-actions-enabled vaut true.
	 * @return boolean
	 */
	static boolean isSystemActionsEnabled() {
		final String parameter = Parameters.getParameter(Parameter.SYSTEM_ACTIONS_ENABLED);
		return parameter == null || Boolean.parseBoolean(parameter);
	}

	/**
	 * Retourne false si le paramètre displayed-counters n'a pas été défini
	 * ou si il contient le compteur dont le nom est paramètre,
	 * et retourne true sinon (c'est-à-dire si le paramètre displayed-counters est défini
	 * et si il ne contient pas le compteur dont le nom est paramètre).
	 * @param counterName Nom du compteur
	 * @return boolean
	 */
	static boolean isCounterHidden(String counterName) {
		final String displayedCounters = getParameter(Parameter.DISPLAYED_COUNTERS);
		if (displayedCounters == null) {
			return false;
		}
		for (final String displayedCounter : displayedCounters.split(",")) {
			final String displayedCounterName = displayedCounter.trim();
			if (counterName.equalsIgnoreCase(displayedCounterName)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @return Nom de l'application courante et nom du sous-répertoire de stockage dans une application monitorée.
	 */
	static String getCurrentApplication() {
		if (servletContext != null) {
			// Le nom de l'application et donc le stockage des fichiers est dans le sous-répertoire
			// ayant pour nom le contexte de la webapp et le nom du serveur
			// pour pouvoir monitorer plusieurs webapps sur le même serveur et
			// pour pouvoir stocker sur un répertoire partagé entre plusieurs serveurs
			return getContextPath(servletContext) + '_' + getHostName();
		}
		return null;
	}

	static String getContextPath(ServletContext context) {
		// cette méthode retourne le contextPath de la webapp
		// en utilisant ServletContext.getContextPath si servlet api 2.5
		// ou en se débrouillant sinon
		// (on n'a pas encore pour l'instant de request pour appeler HttpServletRequest.getContextPath)
		if (context.getMajorVersion() == 2 && context.getMinorVersion() >= 5
				|| context.getMajorVersion() > 2) {
			// api servlet 2.5 (Java EE 5) minimum pour appeler ServletContext.getContextPath
			return context.getContextPath();
		}
		URL webXmlUrl;
		try {
			webXmlUrl = context.getResource("/WEB-INF/web.xml");
		} catch (final MalformedURLException e) {
			throw new IllegalStateException(e);
		}
		String contextPath = webXmlUrl.toExternalForm();
		contextPath = contextPath.substring(0, contextPath.indexOf("/WEB-INF/web.xml"));
		final int indexOfWar = contextPath.indexOf(".war");
		if (indexOfWar > 0) {
			contextPath = contextPath.substring(0, indexOfWar);
		}
		// tomcat peut renvoyer une url commençant pas "jndi:/localhost"
		// (v5.5.28, webapp dans un répertoire)
		if (contextPath.startsWith("jndi:/localhost")) {
			contextPath = contextPath.substring("jndi:/localhost".length());
		}
		final int lastIndexOfSlash = contextPath.lastIndexOf('/');
		if (lastIndexOfSlash != -1) {
			contextPath = contextPath.substring(lastIndexOfSlash);
		}
		return contextPath;
	}

	private static String getJavaMelodyVersion() {
		final Properties properties = new Properties();
		final InputStream inputStream = Parameters.class.getResourceAsStream("/VERSION.properties");
		if (inputStream == null) {
			return null;
		}

		try {
			try {
				properties.load(inputStream);
				return properties.getProperty("version");
			} finally {
				inputStream.close();
			}
		} catch (final IOException e) {
			return e.toString();
		}
	}

	/**
	 * Recherche la valeur d'un paramètre qui peut être défini par ordre de priorité croissant :
	 * - dans les paramètres d'initialisation du filtre (fichier web.xml dans la webapp)
	 * - dans les paramètres du contexte de la webapp avec le préfixe "javamelody." (fichier xml de contexte dans Tomcat)
	 * - dans les variables d'environnement du système d'exploitation avec le préfixe "javamelody."
	 * - dans les propriétés systèmes avec le préfixe "javamelody." (commande de lancement java)
	 * @param parameter Enum du paramètre
	 * @return valeur du paramètre ou null si pas de paramètre défini
	 */
	static String getParameter(Parameter parameter) {
		assert parameter != null;
		final String name = parameter.getCode();
		final String globalName = PARAMETER_SYSTEM_PREFIX + name;
		String result = System.getProperty(globalName);
		if (result != null) {
			return result;
		}
		if (servletContext != null) {
			result = servletContext.getInitParameter(globalName);
			if (result != null) {
				return result;
			}
		}
		if (filterConfig != null) {
			result = filterConfig.getInitParameter(name);
			if (result != null) {
				return result;
			}
		}
		return null;
	}
}
