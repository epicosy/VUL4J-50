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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Driver jdbc "proxy" pour le monitoring.
 * C'est la classe de ce driver qui doit être déclarée si un driver jdbc et non une dataSource est utilisé.
 * Dans la déclaration une propriété jdbc 'driver' doit contenir le nom de la classe du vrai driver.
 * D'autres propriétés jdbc comme url, username ou password peuvent être déclarées.
 * @author Emeric Vernat
 */
public class JdbcDriver implements Driver {
	// cette classe est publique pour être déclarée dans une configuration jdbc
	static final JdbcDriver SINGLETON = new JdbcDriver();

	// initialisation statique du driver
	static {
		try {
			DriverManager.registerDriver(SINGLETON);
			LOG.debug("JDBC driver registered");

			// on désinstalle et on réinstalle les autres drivers pour que le notre soit en premier
			// (notamment, si le jar du driver contient un fichier java.sql.Driver dans META-INF/services
			// pour initialiser automatiquement le driver contenu dans le jar)
			for (final Driver driver : Collections.list(DriverManager.getDrivers())) {
				if (driver != SINGLETON) {
					DriverManager.deregisterDriver(driver);
					DriverManager.registerDriver(driver);
				}
			}
		} catch (final SQLException e) {
			// ne peut arriver
			throw new IllegalStateException(e);
		}
	}

	/** {@inheritDoc} */
	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		final String proxiedDriver = info.getProperty("driver");
		if (proxiedDriver == null) {
			// si pas de propriété driver alors ce n'est pas pour nous
			// (on passe ici lors du DriverManager.getConnection ci-dessous)
			return null;
		}
		try {
			// on utilise Thread.currentThread().getContextClassLoader() car le driver peut ne pas être
			// dans le même classLoader que les classes de javamelody
			Class.forName(proxiedDriver, true, Thread.currentThread().getContextClassLoader());
			// et non Class.forName(proxiedDriver);
		} catch (final ClassNotFoundException e) {
			// Rq: le constructeur de SQLException avec message et cause n'existe qu'en jdk 1.6
			final SQLException ex = new SQLException(e.getMessage());
			ex.initCause(e);
			throw ex; // NOPMD
		}
		final Properties tmp = (Properties) info.clone();
		tmp.remove("driver");
		Parameters.initJdbcDriverParameters(url, tmp);
		return JdbcWrapper.SINGLETON.createConnectionProxy(DriverManager.getConnection(url, tmp));
	}

	/** {@inheritDoc} */
	@Override
	public boolean acceptsURL(String url) throws SQLException {
		// test sur dbcp nécessaire pour le cas où le monitoring est utilisé avec le web.xml global
		// et le répertoire lib global de tomcat et également pour les anomalies 1&2 (sonar, grails)
		// (rq: Thread.currentThread().getStackTrace() a été mesuré à environ 3 micro-secondes)
		for (final StackTraceElement element : Thread.currentThread().getStackTrace()) {
			if (element.getClassName().endsWith("dbcp.BasicDataSource")) {
				return false;
			}
		}
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public int getMajorVersion() {
		return -1;
	}

	/** {@inheritDoc} */
	@Override
	public int getMinorVersion() {
		return -1;
	}

	/** {@inheritDoc} */
	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
		return new DriverPropertyInfo[0];
	}

	/** {@inheritDoc} */
	@Override
	public boolean jdbcCompliant() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return getClass().getSimpleName() + "[lastConnectUrl=" + Parameters.getLastConnectUrl()
				+ ", lastConnectInfo=" + Parameters.getLastConnectInfo() + ']';
	}

	/**
	 * Définition de la méthode getParentLogger ajoutée dans l'interface Driver en jdk 1.7.
	 * @return Logger
	 * @throws SQLFeatureNotSupportedException e
	 */
	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	}
}
